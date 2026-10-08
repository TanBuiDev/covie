import { fireEvent, render, screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type { components } from "@covie/api-client";
import { MerchantProfile } from "@/features/merchant/merchant-profile";

const merchantApi = vi.hoisted(() => ({
  listPublicMerchantCatalog: vi.fn(),
}));

vi.mock("@/features/merchant/merchant-api", () => merchantApi);

const merchant: components["schemas"]["PublicMerchantResponse"] = {
  id: "merchant-1",
  name: "Cửa hàng Chợ Xanh",
  description: "Thực phẩm · Tạp hóa · Thanh lý địa phương",
  locations: [{ id: "location-1", name: "Điểm bán Long Bình", addressText: "123 Nguyễn Xiển, TP. Thủ Đức", distanceMeters: 850 }],
  contacts: [
    { id: "contact-1", type: "ZALO_LINK", value: "https://zalo.me/0909123456", primary: true },
    { id: "contact-2", type: "PHONE", value: "0909 123 456" },
    { id: "contact-3", type: "OTHER", value: "javascript:alert(1)" },
  ],
  reputation: { eligibleOrders: 125, completedOrders: 120, completionRateBasisPoints: 9600 },
  capabilities: { catalogTypes: ["PRODUCT"] },
};

const reviews: components["schemas"]["ReviewPageResponse"] = {
  rating: { averageRating: 4.8, reviewCount: 96 },
  items: [],
};

const catalog: components["schemas"]["PublicMerchantCatalogResponse"] = {
  page: {
    items: [
      { id: "product-1", type: "PRODUCT", listingKind: "STANDARD", name: "Rau củ theo mùa", primaryMedia: { url: "https://media.test.covie/catalog/ready.png" }, priceFrom: { amount: 85000, currency: "VND" } },
      { id: "resale-1", type: "PRODUCT", listingKind: "RESALE", name: "Kệ gỗ còn mới", priceFrom: { amount: 650000, currency: "VND" } },
      { id: "service-1", type: "SERVICE", listingKind: "STANDARD", name: "Vệ sinh máy lạnh", priceFrom: { amount: 150000, currency: "VND" } },
    ],
    hasMore: true,
    nextCursor: "opaque+/=cursor",
  },
};

describe("PUB-009 Merchant profile", () => {
  it("renders only contract-backed public identity, trust, contacts and locations", () => {
    render(<MerchantProfile merchant={merchant} reviews={reviews} />);

    expect(screen.getAllByRole("heading", { name: "Cửa hàng" })).toHaveLength(2);
    expect(screen.getByRole("heading", { name: "Cửa hàng Chợ Xanh" })).toBeInTheDocument();
    const identityDetails = screen.getByLabelText("Chi tiết cửa hàng");
    expect(within(identityDetails).getByText("Cửa hàng Chợ Xanh")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Báo cáo cửa hàng" })).toBeInTheDocument();
    expect(screen.getByText("96%")).toBeInTheDocument();
    expect(screen.getByText("4,8 · 96 đánh giá")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở Zalo" })).toHaveAttribute("href", "https://zalo.me/0909123456");
    expect(screen.getByRole("link", { name: "Gọi điện" })).toHaveAttribute("href", "tel:0909123456");
    expect(screen.getAllByText("Điểm bán Long Bình")).toHaveLength(2);
    expect(screen.queryByText("Giao tận nơi · Nhận tại điểm bán")).not.toBeInTheDocument();
    expect(screen.queryByText("Hoạt động")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "javascript:alert(1)" })).not.toBeInTheDocument();
  });

  it("renders merchant-scoped product and resale cards with opaque cursor navigation", () => {
    render(<MerchantProfile merchant={merchant} reviews={reviews} catalog={catalog} />);

    expect(screen.getByRole("heading", { name: "Danh mục công khai" })).toBeInTheDocument();
    const productLink = screen.getByRole("link", { name: /Rau củ theo mùa/ });
    expect(productLink).toHaveAttribute("href", "/items/product-1");
    expect(productLink.querySelector("img")).toHaveAttribute("src", "https://media.test.covie/catalog/ready.png");
    expect(screen.getByRole("link", { name: /Kệ gỗ còn mới/ })).toHaveAttribute("href", "/items/resale-1");
    expect(screen.getByRole("link", { name: /Vệ sinh máy lạnh/ })).toHaveAttribute("href", "/items/service-1");
    expect(screen.getByText((_, element) => element?.tagName === "B" && element.textContent?.includes("85.000") === true)).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Xem thêm" })).toHaveAttribute(
      "href",
      "/merchants/merchant-1?catalogCursor=opaque%2B%2F%3Dcursor",
    );
    expect(screen.queryByText(/Đang mở cửa|Giao tận nơi|Nhận tại điểm bán/)).not.toBeInTheDocument();
  });

  it("derives Provider and neutral presentation only from authoritative capabilities", () => {
    const { rerender } = render(<MerchantProfile merchant={{ ...merchant, capabilities: { catalogTypes: ["SERVICE"] } }} catalog={{ page: { items: [] } }} />);
    expect(screen.getAllByRole("heading", { name: "Nhà cung cấp" })).toHaveLength(2);
    expect(screen.getByRole("heading", { name: "Dịch vụ công khai" })).toBeInTheDocument();

    rerender(<MerchantProfile merchant={{ ...merchant, capabilities: { catalogTypes: ["PRODUCT", "SERVICE"] } }} catalog={{ page: { items: [] } }} />);
    expect(screen.getAllByRole("heading", { name: "Đơn vị cung cấp" })).toHaveLength(2);
    expect(screen.getByRole("heading", { name: "Danh mục công khai" })).toBeInTheDocument();

    rerender(<MerchantProfile merchant={{ ...merchant, capabilities: { catalogTypes: [] } }} catalog={{ page: { items: [] } }} />);
    expect(screen.getAllByRole("heading", { name: "Đơn vị cung cấp" })).toHaveLength(2);
  });

  it("keeps identity available when catalog loading fails and preserves the cursor in retry", () => {
    render(<MerchantProfile merchant={merchant} catalogError catalogCursor="failed cursor" />);
    expect(screen.getByRole("heading", { name: "Cửa hàng Chợ Xanh" })).toBeInTheDocument();
    expect(screen.getByText("Chưa thể tải danh mục công khai.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Thử lại danh mục" })).toHaveAttribute(
      "href",
      "/merchants/merchant-1?catalogCursor=failed%20cursor",
    );
  });

  it("appends more catalog items seamlessly without losing previous items", async () => {
    merchantApi.listPublicMerchantCatalog.mockResolvedValueOnce({
      page: {
        items: [
          {
            id: "product-2",
            type: "PRODUCT",
            listingKind: "STANDARD",
            name: "Bánh mì tươi",
            priceFrom: { amount: 15000, currency: "VND" },
          },
        ],
        hasMore: false,
        nextCursor: null,
      },
    });

    render(<MerchantProfile merchant={merchant} reviews={reviews} catalog={catalog} />);

    expect(screen.getByRole("link", { name: /Rau củ theo mùa/ })).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /Bánh mì tươi/ })).not.toBeInTheDocument();

    const loadMoreBtn = screen.getByRole("link", { name: "Xem thêm" });
    fireEvent.click(loadMoreBtn);

    expect(merchantApi.listPublicMerchantCatalog).toHaveBeenCalledWith("merchant-1", "opaque+/=cursor");

    expect(await screen.findByRole("link", { name: /Bánh mì tươi/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Rau củ theo mùa/ })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Kệ gỗ còn mới/ })).toBeInTheDocument();
  });
});
