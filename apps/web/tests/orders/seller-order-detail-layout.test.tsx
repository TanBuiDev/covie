import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { SellerOrderDetail } from "@/features/orders/order-detail";

describe("Seller order detail layout", () => {
  it("maps the authoritative order sections into the frozen desktop split-detail regions", () => {
    const { container } = render(<SellerOrderDetail
      merchantId="merchant-1"
      refresh={async () => undefined}
      order={{
        id: "order-1",
        orderCode: "CV-ORDER-1",
        type: "PRODUCT",
        status: "PENDING_CONFIRMATION",
        createdAt: "2026-09-18T08:00:00Z",
        allowedActions: [],
        timeline: [],
        items: [{ id: "item-1", type: "PRODUCT", quantity: 2, unitPrice: 85000, lineTotal: 170000, currency: "VND" }],
        pricing: { currency: "VND", subtotal: 170000, shippingFee: 25000, totalAmount: 195000 },
        fulfillment: { productMethod: "SELLER_DELIVERY", distanceMeters: 0 },
        reputation: { buyer: { eligibleOrders: 25, completedOrders: 24, completionRateBasisPoints: 9600 } },
      } as never}
    />);

    const detail = container.querySelector(".seller-order-detail");
    expect(detail).toBeInTheDocument();
    expect(detail?.querySelector(":scope > .seller-order-detail__hero")).toBeInTheDocument();
    expect(detail?.querySelector(":scope > .seller-order-detail__timeline")).toBeInTheDocument();
    expect(screen.getAllByRole("heading", { name: "Tiến trình" })).toHaveLength(1);
    expect(detail?.querySelector(":scope > .seller-order-detail__snapshot")).toBeInTheDocument();
    expect(detail?.querySelector(":scope > .seller-order-detail__fulfillment")).toBeInTheDocument();
    expect(detail?.querySelector(":scope > .seller-order-detail__privacy")).toContainElement(screen.getByText("Độ tin cậy người mua"));
    expect(detail?.querySelector(":scope > .seller-order-detail__actions")).toContainElement(screen.getByText("Hiện không có thao tác khả dụng cho đơn này."));
  });
});
