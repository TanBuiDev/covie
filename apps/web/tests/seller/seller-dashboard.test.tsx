import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SellerDashboard } from "@/features/seller/seller-dashboard";

describe("SellerDashboard", () => {
  it("shows actionable recent data without fake revenue analytics", () => {
    render(
      <SellerDashboard
        merchantId="merchant-1"
        orders={[{ id: "order-1", orderCode: "CV-1", type: "PRODUCT", status: "PENDING_CONFIRMATION" }]}
        locations={[{ id: "location-1", name: "Điểm A", status: "ACTIVE", currency: "VND" }]}
      />,
    );

    expect(screen.getByRole("link", { name: "CV-1" })).toHaveAttribute("href", "/seller/merchant-1/orders/order-1");
    expect(screen.getByRole("link", { name: "Mở danh sách Cần mua" })).toHaveAttribute("href", "/seller/merchant-1/needs");
    expect(screen.getByRole("link", { name: "Điểm A" })).toHaveAttribute("href", "/seller/merchant-1/locations/location-1");
    expect(screen.queryByText(/doanh thu/i)).not.toBeInTheDocument();
  });
});
