import { render, screen } from "@testing-library/react";
import { expect, it } from "vitest";
import { OrderStatusLabel } from "./order-status";

it("renders a safe fallback for a future order status", () => {
  render(<OrderStatusLabel status="FUTURE_STATUS" />);
  expect(screen.getByText("Trạng thái mới")).toBeTruthy();
});
