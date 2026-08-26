import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { CatalogDetail } from "@/features/catalog/catalog-detail";
import { CovieApiError } from "@/lib/api/api-error";
import type { components } from "@covie/api-client";

const mocks = vi.hoisted(() => ({ addCartItem: vi.fn(), listCatalogReviews: vi.fn(), push: vi.fn(), back: vi.fn() }));
vi.mock("@/features/cart/cart-api", () => ({ addCartItem: mocks.addCartItem }));
vi.mock("@/features/reviews/review-api", () => ({ listCatalogReviews: mocks.listCatalogReviews }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: mocks.push, back: mocks.back }) }));

const product: components["schemas"]["CatalogItemDetailResponse"] = {
  id: "item-1", type: "PRODUCT", listingKind: "STANDARD", name: "Coca-Cola 1.5L", description: "Nước ngọt Coca-Cola 1.5L.",
  merchant: { id: "merchant-1", name: "Tạp hóa Minh", slug: "tap-hoa-minh", reputation: { eligibleOrders: 100, completedOrders: 98, completionRateBasisPoints: 9800 } },
  resolvedLocation: { id: "location-1", name: "Long Bình", addressText: "Thủ Đức", distanceMeters: 350 },
  variants: [{ id: "variant-1", name: "Mặc định", defaultVariant: true, offer: { locationVariantId: "lv-1", available: true, price: { amount: 85_000, currency: "VND" } } }],
};

const service: components["schemas"]["CatalogItemDetailResponse"] = {
  ...product,
  id: "service-1",
  type: "SERVICE",
  name: "Vệ sinh máy lạnh tại nhà",
  variants: [
    { id: "service-variant-1", name: "1 máy", defaultVariant: false, offer: { locationVariantId: "service-lv-1", available: true, price: { amount: 150_000, currency: "VND" } } },
    { id: "service-variant-2", name: "2 máy", defaultVariant: true, offer: { locationVariantId: "service-lv-2", available: true, price: { amount: 280_000, currency: "VND" } } },
    { id: "service-variant-3", name: "3 máy", defaultVariant: false, offer: { locationVariantId: "service-lv-3", available: true, price: { amount: 400_000, currency: "VND" } } },
  ],
};

const resale: components["schemas"]["CatalogItemDetailResponse"] = {
  ...product,
  id: "resale-1",
  listingKind: "RESALE",
  name: "Ghế gaming còn tốt",
  description: "Có xước nhẹ ở tay vịn; đệm và bánh xe vẫn hoạt động tốt.",
  media: [{ id: "media-1", contentType: "image/png", url: "https://cdn.test/resale.png" }],
  variants: [{ id: "resale-variant", name: "Một chiếc", defaultVariant: true, offer: { locationVariantId: "resale-lv", available: true, price: { amount: 500_000, currency: "VND" } } }],
};

afterEach(() => vi.clearAllMocks());

describe("PUB-006 Catalog detail", () => {
  it("renders contract-backed listing, trust and product quantity state", async () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: { averageRating: 4.8, reviewCount: 96 }, items: [] });
    render(<CatalogDetail item={product} />);
    expect(screen.getByRole("heading", { name: "Chi tiết sản phẩm" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Coca-Cola 1.5L" })).toBeInTheDocument();
    expect(screen.getByText("85.000 ₫")).toBeInTheDocument();
    expect(screen.getByText("Hoàn thành 98% đơn")).toBeInTheDocument();
    expect(await screen.findByText("4,8 · 96 đánh giá")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Giảm số lượng" })).toBeDisabled();
  });

  it("adds the selected available offer and can continue to its cart", async () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    mocks.addCartItem.mockResolvedValue({ id: "cart-1", items: [] });
    render(<CatalogDetail item={product} />);
    fireEvent.click(screen.getByRole("button", { name: "Tăng số lượng" }));
    fireEvent.click(screen.getByRole("button", { name: "Thêm vào giỏ" }));
    await waitFor(() => expect(mocks.addCartItem).toHaveBeenCalledWith({ locationVariantId: "lv-1", quantity: 2 }));
    expect(await screen.findByText("Đã thêm vào giỏ hàng.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Xem giỏ hàng" }));
    expect(mocks.push).toHaveBeenCalledWith("/cart/cart-1");
  });

  it("never renders a quantity stepper for a service and disables unavailable purchase", () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    render(<CatalogDetail item={{ ...product, type: "SERVICE", variants: [{ id: "service-variant", name: "Mặc định", offer: { locationVariantId: "service-lv", available: false, price: { amount: 85_000, currency: "VND" } } }] }} />);
    expect(screen.queryByText("Số lượng")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thêm vào giỏ" })).toBeDisabled();
    expect(screen.getByText("Hiện không khả dụng tại điểm bán này")).toBeInTheDocument();
  });

  it("shows an authorization action when cart API returns 401", async () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    mocks.addCartItem.mockRejectedValue(new CovieApiError(401, "UNAUTHORIZED", "Unauthorized"));
    render(<CatalogDetail item={product} />);
    fireEvent.click(screen.getByRole("button", { name: "Thêm vào giỏ" }));
    expect(await screen.findByText("Đăng nhập để thêm sản phẩm vào giỏ.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Đăng nhập" })).toHaveAttribute("href", "/login");
  });

  it("renders the PUB-007 service detail with a native offer radio group", () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    render(<CatalogDetail item={service} />);

    expect(screen.getByRole("heading", { name: "Chi tiết dịch vụ" })).toBeInTheDocument();
    expect(screen.getByRole("radiogroup", { name: "Phân loại" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: /2 máy/ })).toBeChecked();
    expect(screen.getByText("Phối hợp dịch vụ")).toBeInTheDocument();
    expect(screen.getByText(/V1 không có calendar booking/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Đặt dịch vụ" })).toBeEnabled();
  });

  it("adds the selected service offer with quantity one", async () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    mocks.addCartItem.mockResolvedValue({ id: "service-cart", items: [] });
    render(<CatalogDetail item={service} />);

    fireEvent.click(screen.getByRole("radio", { name: /1 máy/ }));
    fireEvent.click(screen.getByRole("button", { name: "Đặt dịch vụ" }));

    await waitFor(() => expect(mocks.addCartItem).toHaveBeenCalledWith({ locationVariantId: "service-lv-1", quantity: 1 }));
    expect(mocks.push).toHaveBeenCalledWith("/cart/service-cart");
  });

  it("renders PUB-008 resale information without inventing fulfillment eligibility", () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    render(<CatalogDetail item={resale} />);

    expect(screen.getByRole("heading", { name: "Chi tiết thanh lý" })).toBeInTheDocument();
    expect(screen.getByText("Ảnh thực tế · 1/1")).toBeInTheDocument();
    expect(screen.getByText("Tình trạng")).toBeInTheDocument();
    expect(screen.getByText(resale.description!)).toBeInTheDocument();
    expect(screen.getByText("Vị trí giao dịch")).toBeInTheDocument();
    expect(screen.getByText(/Long Bình · Thủ Đức/)).toBeInTheDocument();
    expect(screen.getByText("Xác nhận trong giỏ hàng")).toBeInTheDocument();
    expect(screen.queryByText("Số lượng")).not.toBeInTheDocument();
    expect(screen.queryByText("Nhận tại điểm bán")).not.toBeInTheDocument();
  });

  it("always adds one unit for a resale listing", async () => {
    mocks.listCatalogReviews.mockResolvedValue({ rating: {}, items: [] });
    mocks.addCartItem.mockResolvedValue({ id: "resale-cart", items: [] });
    render(<CatalogDetail item={resale} />);

    fireEvent.click(screen.getByRole("button", { name: "Mua ngay" }));

    await waitFor(() => expect(mocks.addCartItem).toHaveBeenCalledWith({ locationVariantId: "resale-lv", quantity: 1 }));
    expect(mocks.push).toHaveBeenCalledWith("/cart/resale-cart");
  });
});
