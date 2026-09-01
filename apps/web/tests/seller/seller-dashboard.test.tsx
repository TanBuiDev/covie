import { render, screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SellerDashboard } from "@/features/seller/seller-dashboard";

describe("SellerDashboard", () => {
  it("shows actionable recent data without fake revenue analytics", () => {
    render(
      <SellerDashboard
        merchantId="merchant-1"
        merchantName="Tiệm A"
        merchantStatus="ACTIVE"
        orders={[{ id: "order-1", orderCode: "CV-1", type: "PRODUCT", status: "PENDING_CONFIRMATION" }]}
        needMatches={[]}
        locations={[{ id: "location-1", name: "Điểm A", status: "ACTIVE", currency: "VND" }]}
      />,
    );

    expect(screen.getByRole("heading", { name: "Tổng quan" })).toBeInTheDocument();
    expect(screen.getByText("Tiệm A · Trung tâm bán hàng")).toBeInTheDocument();
    const orderLink = screen.getByText("CV-1").closest("a");
    expect(orderLink).toHaveAttribute("href", "/seller/merchant-1/orders/order-1");

    const needSection = screen.getByRole("heading", { name: "Cần mua phù hợp" }).closest("section");
    expect(needSection).not.toBeNull();
    if (!needSection) throw new Error("Expected Need section");
    expect(within(needSection).getByRole("link", { name: "Xem tất cả" })).toHaveAttribute("href", "/seller/merchant-1/needs");

    expect(screen.getByText("1")).toBeInTheDocument();
    expect(screen.queryByText(/doanh thu/i)).not.toBeInTheDocument();
  });
});
