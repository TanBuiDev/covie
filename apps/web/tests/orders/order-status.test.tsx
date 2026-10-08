import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { OrderStatusLabel } from "@/features/orders/order-status";

it.each([
  ["PENDING_CONFIRMATION", "Chờ người bán xác nhận", "info"],
  ["OUT_FOR_DELIVERY", "Đang giao", "warning"],
  ["COMPLETED", "Hoàn tất", "success"],
  ["FUTURE_STATUS", "Trạng thái đã được cập nhật", "neutral"],
])("renders the presentation chip for %s", (status, label, tone) => {
  render(<OrderStatusLabel status={status} />);
  expect(screen.getByText(label)).toHaveClass("status-chip");
  expect(screen.getByText(label)).toHaveAttribute("data-tone", tone);
});
