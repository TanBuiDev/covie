import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
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

afterEach(() => vi.clearAllMocks());

describe("PUB-003/004 Search", () => {
  it("renders the focused Search frame without querying until the user provides search intent", () => {
    renderSearch({ sort: "RELEVANCE" });

    expect(screen.getByRole("heading", { name: "Khám phá" })).toBeInTheDocument();
    expect(screen.getByText("Tìm quanh vị trí bạn đã chọn")).toBeInTheDocument();
    expect(screen.getByText("Vinhomes Grand Park")).toBeInTheDocument();
    expect(screen.getByRole("searchbox", { name: "Tìm món đồ, dịch vụ" })).toBeInTheDocument();
    expect(mocks.discoverCatalogItems).not.toHaveBeenCalled();
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
