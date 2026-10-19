import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SearchResults } from "@/features/discovery/search-results";
import { BrowseLocationContext, type BrowseLocationContextValue } from "@/features/location/browse-location-provider";
import type { BrowseLocation } from "@/features/location/location-context";

const mocks = vi.hoisted(() => ({
  discoverCatalogItems: vi.fn(),
  listPublicCategories: vi.fn(),
  replace: vi.fn(),
  back: vi.fn(),
}));

vi.mock("@/features/catalog/catalog-api", () => ({ discoverCatalogItems: mocks.discoverCatalogItems, listPublicCategories: mocks.listPublicCategories }));
vi.mock("next/navigation", async () => {
  const actual = await vi.importActual<typeof import("next/navigation")>("next/navigation");
  return { ...actual, useRouter: () => ({ replace: mocks.replace, back: mocks.back }) };
});

const location: BrowseLocation = { kind: "coordinates", lat: 10.84, lng: 106.81, label: "Vinhomes Grand Park" };

function pageWith(name: string) {
  return {
    page: {
      items: [{
        id: "item-1",
        name,
        type: "PRODUCT" as const,
        listingKind: "STANDARD" as const,
        merchant: { id: "merchant-1", name: "Mina Tea", slug: "mina-tea" },
        resolvedLocation: { id: "location-1", name: "Long Bình", distanceMeters: 350 },
        priceFrom: { amount: 85_000, currency: "VND" },
      }],
      hasMore: false,
    },
  };
}

function renderSearch(initial: Parameters<typeof SearchResults>[0]["initial"], selectedLocation: BrowseLocation | null = location) {
  const value: BrowseLocationContextValue = {
    location: selectedLocation,
    hydrated: true,
    setLocation: vi.fn(),
    clearLocation: vi.fn(),
  };
  return render(
    <BrowseLocationContext.Provider value={value}>
      <SearchResults initial={initial} />
    </BrowseLocationContext.Provider>,
  );
}

afterEach(() => {vi.clearAllMocks(); mocks.discoverCatalogItems.mockReset(); mocks.listPublicCategories.mockReset();});
beforeEach(() => mocks.listPublicCategories.mockResolvedValue([]));

describe("PUB-003/004 Search", () => {
  it("names the desktop price bounds and preserves their applied filter values", async () => {
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Kết quả giá có nhãn"));
    renderSearch({q:"cơm"});
    await screen.findByText("Kết quả giá có nhãn");
    const minimum=screen.getByPlaceholderText("Từ (₫)");
    const maximum=screen.getByPlaceholderText("Đến (₫)");
    expect.soft(minimum).toHaveAccessibleName("Giá từ (₫)");
    expect.soft(maximum).toHaveAccessibleName("Giá đến (₫)");
    fireEvent.change(minimum,{target:{value:"10000"}});
    fireEvent.change(maximum,{target:{value:"30000"}});
    fireEvent.click(screen.getByRole("button",{name:"Áp dụng giá"}));
    await waitFor(()=>expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({q:"cơm",minPrice:10000,maxPrice:30000})));
  });
  it("keeps keyboard focus inside the filter dialog and restores it on Escape", async () => {
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Kết quả"));
    renderSearch({q:"cơm"});
    await screen.findByText("Kết quả");
    const trigger = screen.getByRole("button",{name:"Mở bộ lọc"});
    trigger.focus();
    fireEvent.click(trigger);
    const dialog = screen.getByRole("dialog");
    const first = within(dialog).getByRole("button",{name:"Đặt lại"});
    const last = within(dialog).getByRole("button",{name:"Áp dụng"});
    expect(first).toHaveFocus();
    fireEvent.keyDown(first,{key:"Tab",shiftKey:true});
    expect(last).toHaveFocus();
    fireEvent.keyDown(last,{key:"Tab"});
    expect(first).toHaveFocus();
    fireEvent.keyDown(first,{key:"Escape"});
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });
  it("applies price and fulfillment controls, rejects an inverted range and resets filters", async () => {
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Kết quả lọc"));
    renderSearch({q:"cơm"});
    await screen.findByText("Kết quả lọc");
    fireEvent.click(screen.getByRole("button",{name:"Mở bộ lọc"}));
    fireEvent.change(screen.getByLabelText("Giá từ (₫)"),{target:{value:"50000"}});
    fireEvent.change(screen.getByLabelText("Giá đến (₫)"),{target:{value:"10000"}});
    expect(screen.getByRole("button",{name:"Áp dụng"})).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Giá từ (₫)"),{target:{value:"0"}});
    fireEvent.click(screen.getByLabelText("Giao tận nơi"));
    fireEvent.click(screen.getByLabelText("Nhận tại điểm bán"));
    fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
    await waitFor(()=>expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({q:"cơm",minPrice:0,maxPrice:10000,canServe:true,pickup:true})));
    expect(mocks.replace).toHaveBeenLastCalledWith(expect.stringContaining("minPrice=0"),{scroll:false});
    fireEvent.click(screen.getByRole("button",{name:"Mở bộ lọc"}));
    expect(screen.getByLabelText("Giá đến (₫)")).toHaveValue(10000);
    expect(screen.getByLabelText("Giao tận nơi")).toBeChecked();
    fireEvent.click(screen.getByRole("button",{name:"Đặt lại"}));
    fireEvent.click(screen.getByRole("button",{name:"Áp dụng"}));
    await waitFor(()=>expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({q:"cơm",minPrice:undefined,maxPrice:undefined,canServe:undefined,pickup:undefined})));
  });
  it("synchronizes results and the search field when URL-derived filters change", async () => {
    mocks.discoverCatalogItems.mockResolvedValueOnce(pageWith("Cơm cũ"))
      .mockResolvedValueOnce(pageWith("Trà mới"));
    const value: BrowseLocationContextValue = {location, hydrated:true, setLocation:vi.fn(), clearLocation:vi.fn()};
    const view = render(<BrowseLocationContext.Provider value={value}><SearchResults initial={{q:"cơm"}} /></BrowseLocationContext.Provider>);
    await screen.findByText("Cơm cũ");
    view.rerender(<BrowseLocationContext.Provider value={value}><SearchResults initial={{q:"trà",type:"SERVICE"}} /></BrowseLocationContext.Provider>);
    expect(await screen.findByText("Trà mới")).toBeInTheDocument();
    expect(screen.queryByText("Cơm cũ")).not.toBeInTheDocument();
    expect(screen.getByRole("searchbox", {name:"Tìm món đồ, dịch vụ"})).toHaveValue("trà");
    expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({q:"trà",type:"SERVICE"}));
  });
  it("offers a real category selector and opens filters with the current applied values", async () => {
    mocks.listPublicCategories.mockResolvedValue([{id:"food",name:"Ăn uống"}]);
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Món ăn"));
    renderSearch({q:"cơm"});
    await screen.findByText("Món ăn");
    fireEvent.click(screen.getByRole("button", {name:"Danh mục⌄"}));
    const dialog=screen.getByRole("dialog");
    fireEvent.change(within(dialog).getByRole("combobox", {name:"Danh mục"}), {target:{value:"food"}});
    fireEvent.click(within(dialog).getByRole("button", {name:"Áp dụng"}));
    await waitFor(() => expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({categoryId:"food",q:"cơm"})));
    fireEvent.click(screen.getByRole("button", {name:"Danh mục đã chọn"}));
    expect(within(screen.getByRole("dialog")).getByRole("combobox", {name:"Danh mục"})).toHaveValue("food");
  });
  it("loads the next cursor page without losing results and retries a failed next page", async () => {
    mocks.discoverCatalogItems.mockResolvedValueOnce({page:{...pageWith("Trang đầu").page, hasMore:true, nextCursor:"cursor-1"}})
      .mockRejectedValueOnce(new Error("offline"))
      .mockResolvedValueOnce({page:{items:[{...pageWith("Trang hai").page.items[0],id:"item-2"}],hasMore:false}});
    const initial = {q:"hàng",minPrice:0,maxPrice:100000,pickup:true,canServe:true,shipping:"FREE"};
    renderSearch(initial);
    expect(await screen.findByText("Trang đầu")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", {name:"Tải thêm kết quả"}));
    expect(await screen.findByText("Không thể tải thêm kết quả.")).toBeInTheDocument();
    expect(screen.getByText("Trang đầu")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", {name:"Thử lại tải thêm"}));
    expect(await screen.findByText("Trang hai")).toBeInTheDocument();
    expect(screen.getByText("Trang đầu")).toBeInTheDocument();
    expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({...initial,cursor:"cursor-1"}));
    expect(screen.queryByRole("button", {name:"Tải thêm kết quả"})).not.toBeInTheDocument();
  });
  it("does not show an endless results loader when a query has no selected location", () => {
    renderSearch({q:"cơm"}, null);
    expect(screen.getByText("Chọn khu vực để xem kết quả gần bạn")).toBeInTheDocument();
    expect(screen.queryByLabelText("Đang tải kết quả")).not.toBeInTheDocument();
    expect(mocks.discoverCatalogItems).not.toHaveBeenCalled();
  });
  it("loads nearby listings on Explore when a location is selected without requiring a keyword", async () => {
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Hàng gần bạn"));
    renderSearch({ sort: "RELEVANCE" });

    expect(screen.getByRole("heading", { name: "Khám phá" })).toBeInTheDocument();
    expect(screen.getByRole("tablist", { name: "Phân loại kết quả" })).toBeInTheDocument();
    expect(screen.getByText("Vinhomes Grand Park")).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Tìm món đồ, dịch vụ" })).toBeInTheDocument();
    expect(await screen.findByText("Hàng gần bạn")).toBeInTheDocument();
    expect(mocks.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({sort:"DISTANCE", q:undefined}));
  });

  it("renders the Results frame and sends generated-contract filters for an initial query", async () => {
    mocks.discoverCatalogItems.mockResolvedValue(pageWith("Trà sữa Mina"));

    renderSearch({ q: "trà sữa", sort: "DISTANCE" });

    expect(await screen.findByRole("heading", { name: "Kết quả tìm kiếm" })).toBeInTheDocument();
    expect(await screen.findByText("Trà sữa Mina")).toBeInTheDocument();
    expect(screen.getByRole("combobox", { name: "Sắp xếp" })).toHaveValue("DISTANCE");
    expect(mocks.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({
      q: "trà sữa",
      sort: "DISTANCE",
      limit: 20,
      location: { kind: "coordinates", lat: 10.84, lng: 106.81 },
    }));
  });

  it("renders PUB-005 Category on the canonical search route", async () => {
    mocks.listPublicCategories.mockResolvedValue([{ id: "drinks", name: "Đồ uống", slug: "do-uong" }]);
    mocks.discoverCatalogItems.mockResolvedValue({ page: { items: [], hasMore: false } });

    renderSearch({ categoryId: "drinks", sort: "RELEVANCE" });

    expect(await screen.findByRole("heading", { name: "Đồ uống" })).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Tìm trong Đồ uống" })).toHaveAttribute("placeholder", "Tìm trong Đồ uống");
    expect(screen.getByRole("button", { name: "Danh mục đã chọn" })).toBeInTheDocument();
    expect(mocks.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({ categoryId: "drinks", sort: "DISTANCE" }));
  });

  it("applies filter-sheet values to discovery and the shareable URL", async () => {
    mocks.discoverCatalogItems.mockResolvedValue({ page: { items: [], hasMore: false } });
    renderSearch({ q: "bàn", sort: "RELEVANCE" });
    await waitFor(() => expect(mocks.discoverCatalogItems).toHaveBeenCalledTimes(1));

    fireEvent.click(screen.getByRole("button", { name: "Mở bộ lọc" }));
    const sheet = screen.getByRole("dialog", { name: "Bộ lọc tìm kiếm" });
    fireEvent.change(within(sheet).getByRole("combobox", { name: "Loại" }), { target: { value: "PRODUCT" } });
    fireEvent.change(within(sheet).getByRole("combobox", { name: "Loại tin" }), { target: { value: "RESALE" } });
    fireEvent.change(within(sheet).getByRole("combobox", { name: "Khoảng cách" }), { target: { value: "5000" } });
    fireEvent.click(within(sheet).getByRole("checkbox", { name: "Đang mở cửa" }));
    fireEvent.click(within(sheet).getByRole("button", { name: "Áp dụng" }));

    await waitFor(() => expect(mocks.discoverCatalogItems).toHaveBeenLastCalledWith(expect.objectContaining({
      type: "PRODUCT",
      listingKind: "RESALE",
      radiusMeters: 5000,
      openNow: true,
    })));
    expect(mocks.replace).toHaveBeenLastCalledWith(expect.stringMatching(/type=PRODUCT/), { scroll: false });
    expect(mocks.replace).toHaveBeenLastCalledWith(expect.stringMatching(/listingKind=RESALE/), { scroll: false });
  });

  it("shows a retryable Results error without losing the active query", async () => {
    mocks.discoverCatalogItems.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce(pageWith("Trà sữa Mina"));
    renderSearch({ q: "trà sữa", sort: "RELEVANCE" });

    expect(await screen.findByText("Không thể tải kết quả tìm kiếm.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Thử lại tìm kiếm" }));

    expect(await screen.findByText("Trà sữa Mina")).toBeInTheDocument();
    expect(mocks.discoverCatalogItems).toHaveBeenCalledTimes(2);
  });
});
