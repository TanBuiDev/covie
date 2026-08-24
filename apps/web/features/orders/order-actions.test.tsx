import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OrderActions } from "./order-actions";

vi.mock("./order-api", () => ({ orderCommand: vi.fn(() => new Promise(() => {})) }));

describe("OrderActions", () => {
  it("renders only actions allowed by the server", () => {
    render(
      <OrderActions
        order={{ id: "order-1", orderCode: "CV-1", type: "PRODUCT", status: "CONFIRMED", createdAt: "2026-01-01T00:00:00Z", allowedActions: ["MARK_DELIVERED"] }}
        onChanged={vi.fn()}
      />,
    );

    expect(screen.queryByRole("button", { name: "Xác nhận đơn" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "Đã giao" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Đã giao hàng" })).toBeInTheDocument();
  });

  it("warns that completion is irreversible", () => {
    render(
      <OrderActions
        order={{ id: "order-2", type: "SERVICE", status: "CONFIRMED", allowedActions: ["CONFIRM_COMPLETED"] }}
        onChanged={vi.fn()}
      />,
    );
    fireEvent.click(screen.getByRole("button", { name: "Dịch vụ đã hoàn thành" }));
    expect(screen.getByText(/không thể hoàn tác/i)).toBeInTheDocument();
  });
});
