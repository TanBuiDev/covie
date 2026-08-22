import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OrderActions } from "./order-actions";

vi.mock("./order-api", () => ({ orderCommand: vi.fn(() => new Promise(() => {})) }));

describe("OrderActions", () => {
  it("renders only actions allowed by the server and disables a duplicate click", () => {
    render(<OrderActions order={{ id: "order-1", orderCode: "CV-1", type: "PRODUCT", status: "CONFIRMED", createdAt: "2026-01-01T00:00:00Z", allowedActions: ["MARK_DELIVERED"] }} onChanged={vi.fn()} />);
    expect(screen.queryByRole("button", { name: "Xác nhận đơn" })).not.toBeInTheDocument();
    const action = screen.getByRole("button", { name: "Đã giao" });
    fireEvent.click(action);
    expect(action).toBeDisabled();
  });
});
