import { render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import HomePage from "../app/page";
import { BrowseLocationContext, type BrowseLocationContextValue } from "@/features/location/browse-location-provider";
import type { BrowseLocation } from "@/features/location/location-context";

const api = vi.hoisted(() => ({
  discoverCatalogItems: vi.fn(),
  listPublicCategories: vi.fn(),
}));

vi.mock("@/features/catalog/catalog-api", () => api);

const categories = [
  { id: "food", name: "Đồ ăn", slug: "do-an", catalogType: "PRODUCT" as const, sortOrder: 1 },
  { id: "drink", name: "Đồ uống", slug: "do-uong", catalogType: "PRODUCT" as const, sortOrder: 2 },
  { id: "grocery", name: "Tạp hóa", slug: "tap-hoa", catalogType: "PRODUCT" as const, sortOrder: 3 },
  { id: "service", name: "Dịch vụ", slug: "dich-vu", catalogType: "SERVICE" as const, sortOrder: 4 },
];

function discoveryPage(name: string, type: "PRODUCT" | "SERVICE", listingKind: "STANDARD" | "RESALE" = "STANDARD") {
  return {
    page: {
      items: [{
        id: `${type}-${listingKind}`,
        name,
        type,
        listingKind,
        merchant: { id: "merchant", name: "Cửa hàng CoVie", slug: "cua-hang-covie" },
        resolvedLocation: { id: "location", name: "Long Bình", distanceMeters: 350 },
        priceFrom: { amount: 85_000, currency: "VND" },
      }],
      hasMore: false,
    },
  };
}

function renderHome(location: BrowseLocation | null) {
  const value: BrowseLocationContextValue = {
    location,
    hydrated: true,
    setLocation: vi.fn(),
    clearLocation: vi.fn(),
  };

  return render(
    <BrowseLocationContext.Provider value={value}>
      <HomePage />
    </BrowseLocationContext.Provider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe("PUB-001 Home", () => {
  it("shows categories and the missing-location state without requesting nearby listings", async () => {
    api.listPublicCategories.mockResolvedValue(categories);

    renderHome(null);

    expect(await screen.findByRole("heading", { name: "Khám phá danh mục" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Chọn vị trí để xem chợ gần bạn" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Chọn vị trí" })).toBeInTheDocument();
    expect(api.discoverCatalogItems).not.toHaveBeenCalled();
  });

  it("loads the three Figma Home sections with generated-contract discovery filters", async () => {
    api.listPublicCategories.mockResolvedValue(categories);
    api.discoverCatalogItems.mockImplementation((filters: { type?: string; listingKind?: string }) => {
      if (filters.type === "SERVICE") return Promise.resolve(discoveryPage("Sửa máy lạnh tại nhà", "SERVICE"));
      if (filters.listingKind === "RESALE") return Promise.resolve(discoveryPage("Bàn học gỗ còn mới", "PRODUCT", "RESALE"));
      return Promise.resolve(discoveryPage("Bánh mì chả lụa", "PRODUCT"));
    });

    renderHome({ kind: "coordinates", lat: 10.84, lng: 106.81, label: "Vinhomes Grand Park" });

    expect(await screen.findByRole("heading", { name: "Gần bạn" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Dịch vụ gần bạn" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Thanh lý gần bạn" })).toBeInTheDocument();
    expect(await screen.findByText("Bánh mì chả lụa")).toBeInTheDocument();
    expect(screen.getByText("Sửa máy lạnh tại nhà")).toBeInTheDocument();
    expect(screen.getByText("Bàn học gỗ còn mới")).toBeInTheDocument();

    expect(within(screen.getByRole("region", {name:"Dịch vụ gần bạn"})).getByRole("link", {name:"Xem tất cả"}))
      .toHaveAttribute("href", "/search?type=SERVICE&sort=DISTANCE");
    expect(within(screen.getByRole("region", {name:"Thanh lý gần bạn"})).getByRole("link", {name:"Xem tất cả"}))
      .toHaveAttribute("href", "/search?type=PRODUCT&listingKind=RESALE&sort=DISTANCE");
    expect(within(screen.getByRole("region", {name:"Gần bạn"})).getByRole("link", {name:"Xem tất cả"}))
      .toHaveAttribute("href", "/search?type=PRODUCT&sort=DISTANCE");

    await waitFor(() => expect(api.discoverCatalogItems).toHaveBeenCalledTimes(3));
    expect(api.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({
      type: "PRODUCT", listingKind: "STANDARD", sort: "DISTANCE", limit: 4,
      location: { kind: "coordinates", lat: 10.84, lng: 106.81 },
    }));
    expect(api.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({
      type: "SERVICE", listingKind: "STANDARD", sort: "DISTANCE", limit: 4,
    }));
    expect(api.discoverCatalogItems).toHaveBeenCalledWith(expect.objectContaining({
      type: "PRODUCT", listingKind: "RESALE", sort: "DISTANCE", limit: 4,
    }));
  });

  it("keeps successful Home sections visible when one discovery request fails", async () => {
    api.listPublicCategories.mockResolvedValue(categories);
    api.discoverCatalogItems.mockImplementation((filters: { type?: string; listingKind?: string }) => {
      if (filters.type === "SERVICE") return Promise.reject(new Error("service unavailable"));
      if (filters.listingKind === "RESALE") return Promise.resolve(discoveryPage("Bàn học gỗ còn mới", "PRODUCT", "RESALE"));
      return Promise.resolve(discoveryPage("Bánh mì chả lụa", "PRODUCT"));
    });

    renderHome({ kind: "coordinates", lat: 10.84, lng: 106.81, label: "Vinhomes Grand Park" });

    expect(await screen.findByText("Bánh mì chả lụa")).toBeInTheDocument();
    expect(screen.getByText("Bàn học gỗ còn mới")).toBeInTheDocument();
    expect(await screen.findByText("Không thể tải dịch vụ gần bạn.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thử lại dịch vụ gần bạn" })).toBeInTheDocument();
  });

  it("renders Phase 2 discovery shortcuts and need matching spotlight with prefill action", async () => {
    api.listPublicCategories.mockResolvedValue(categories);
    api.discoverCatalogItems.mockResolvedValue(discoveryPage("Bánh mì chả lụa", "PRODUCT"));

    renderHome({ kind: "coordinates", lat: 10.84, lng: 106.81, label: "Vinhomes Grand Park" });

    // Discovery shortcuts
    const shortcuts = await screen.findByLabelText("Bộ lọc nhanh");
    expect(within(shortcuts).getByRole("link", { name: "Gần nhất" })).toBeInTheDocument();
    expect(within(shortcuts).getByRole("link", { name: "Đang mở cửa" })).toBeInTheDocument();
    expect(within(shortcuts).getByRole("link", { name: "Đồ thanh lý" })).toBeInTheDocument();
    expect(within(shortcuts).getByRole("link", { name: "Dịch vụ tại nhà" })).toBeInTheDocument();

    // Need matching spotlight
    expect(screen.getByRole("heading", { name: "Bạn đang cần tìm món đồ hay thợ sửa gấp?" })).toBeInTheDocument();
    const needInput = screen.getByRole("textbox", { name: "Nhu cầu cần tìm" });
    expect(needInput).toBeInTheDocument();
    expect(needInput.closest("form")).toHaveAttribute("action", "/needs/new");
    expect(screen.getByRole("button", { name: "Đăng nhu cầu ngay" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "+ Đăng nhu cầu đầy đủ" })).toHaveAttribute("href", "/needs/new");
    expect(screen.getByRole("link", { name: "Xem tin cần mua quanh đây" })).toHaveAttribute("href", "/needs");
  });

  it("renders Phase 3 community pulse bar and featured neighborhood merchants with quick CTAs", async () => {
    api.listPublicCategories.mockResolvedValue(categories);
    api.discoverCatalogItems.mockResolvedValue(discoveryPage("Bánh mì chả lụa", "PRODUCT"));

    renderHome({ kind: "coordinates", lat: 10.84, lng: 106.81, label: "Vinhomes Grand Park" });

    // Community pulse & context metrics
    expect(await screen.findByText("CHỢ NỘI KHU ĐANG HOẠT ĐỘNG")).toBeInTheDocument();
    const contextBanner = screen.getByLabelText("Khu vực chợ hiện tại");
    expect(within(contextBanner).getByText(/Đang mở cửa/)).toBeInTheDocument();

    // Featured neighborhood merchants section
    const merchantsSection = await screen.findByRole("region", { name: "Gian hàng & Thợ uy tín gần bạn" });
    expect(merchantsSection).toBeInTheDocument();
    expect(within(merchantsSection).getByText("Cửa hàng CoVie")).toBeInTheDocument();
    expect(within(merchantsSection).getByRole("link", { name: /Ghé gian hàng/ })).toHaveAttribute("href", "/merchants/merchant");

    // Product card quick CTA
    const productCtas = screen.getAllByRole("link", { name: /Xem & Đặt/ });
    expect(productCtas.length).toBeGreaterThan(0);
  });
});
