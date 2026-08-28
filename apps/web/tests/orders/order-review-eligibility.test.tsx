import { render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { OrderDetailView } from "@/features/orders/order-detail";

const { getOrder } = vi.hoisted(() => ({ getOrder: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ back: vi.fn() }) }));
vi.mock("@/features/orders/order-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/orders/order-api")>("@/features/orders/order-api");
  return { ...actual, getOrder };
});

function order(eligible: boolean) {
  return {
    id: "order-1", orderCode: "CV001", type: "PRODUCT", status: "COMPLETED", version: 1,
    source: "MARKETPLACE", buyer: {}, merchant: {}, pricing: { subtotal: 100000, knownTotal: 100000, totalAmount: 100000, currency: "VND" },
    paymentMethod: "COD", timeline: [], allowedActions: [], createdAt: "2026-08-28T00:00:00Z",
    items: [{ id: "order-item-1", type: "PRODUCT", quantity: 1, unitPrice: 100000, lineTotal: 100000,
      currency: "VND", snapshot: { name: "Sản phẩm CoVie" }, reviewEligibility: { eligible, reason: eligible ? "ELIGIBLE" : "ALREADY_REVIEWED" } }],
  };
}

it("mounts review forms only from explicit item eligibility", async () => {
  getOrder.mockResolvedValueOnce(order(true));
  const { unmount } = render(<OrderDetailView orderId="order-1" />);
  await waitFor(() => expect(screen.getAllByText("Đánh giá giao dịch").length).toBeGreaterThan(0));
  unmount();

  getOrder.mockResolvedValueOnce(order(false));
  render(<OrderDetailView orderId="order-1" />);
  await screen.findAllByText(/Sản phẩm CoVie/);
  expect(screen.queryByText("Đánh giá giao dịch")).not.toBeInTheDocument();
});
