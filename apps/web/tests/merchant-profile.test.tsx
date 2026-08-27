import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { components } from "@covie/api-client";
import { MerchantProfile } from "@/features/merchant/merchant-profile";

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
};

const reviews: components["schemas"]["ReviewPageResponse"] = {
  rating: { averageRating: 4.8, reviewCount: 96 },
  items: [],
};

describe("PUB-009 Merchant profile", () => {
  it("renders only contract-backed public identity, trust, contacts and locations", () => {
    render(<MerchantProfile merchant={merchant} reviews={reviews} />);

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { name: "Cửa hàng" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Cửa hàng Chợ Xanh" })).toBeInTheDocument();
    expect(screen.getByText("96%")).toBeInTheDocument();
    expect(screen.getByText("4,8 · 96 đánh giá")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Mở Zalo" })).toHaveAttribute("href", "https://zalo.me/0909123456");
    expect(screen.getByRole("link", { name: "Gọi điện" })).toHaveAttribute("href", "tel:0909123456");
    expect(screen.getAllByText("Điểm bán Long Bình")).toHaveLength(2);
    expect(screen.queryByText("Giao tận nơi · Nhận tại điểm bán")).not.toBeInTheDocument();
    expect(screen.queryByText("Hoạt động")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "javascript:alert(1)" })).not.toBeInTheDocument();
  });

  it("keeps the catalog section truthful while public merchant filtering is unavailable", () => {
    render(<MerchantProfile merchant={merchant} reviews={reviews} />);

    expect(screen.getByRole("heading", { name: "Danh mục công khai" })).toBeInTheDocument();
    expect(screen.getByText("Chưa thể tải danh mục của cửa hàng.")).toBeInTheDocument();
  });
});
