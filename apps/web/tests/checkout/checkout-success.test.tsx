import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { CheckoutSuccess } from "@/features/checkout/checkout-success";
import { CheckoutSafeError } from "@/features/checkout/checkout-preview";

it("states that the order was created without implying seller confirmation", () => {
  render(<CheckoutSuccess orderId="order-id" orderCode="CV1024" />);
  expect(screen.getByRole("heading", { name: "Đã tạo đơn hàng" })).toBeInTheDocument();
  expect(screen.getByText("Mã đơn: #CV1024")).toBeInTheDocument();
  expect(screen.getByText(/Trạng thái và hành động tiếp theo được hiển thị trong chi tiết đơn/)).toBeInTheDocument();
  expect(screen.queryByText(/đang chờ người bán xác nhận/i)).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Xem đơn hàng" })).toHaveAttribute("href", "/orders/order-id");
});

it("offers a safe recovery when checkout has no valid source", () => {
  render(<CheckoutSafeError />);
  expect(screen.getByRole("heading", { name: "Không thể tiếp tục đặt hàng" })).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Quay lại giỏ hàng" })).toHaveAttribute("href", "/cart");
});
