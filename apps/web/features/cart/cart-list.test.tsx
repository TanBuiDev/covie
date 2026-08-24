import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { CartItemRow } from "./cart-item-row";

it("does not render service quantity increment or decrement controls", () => {
  const change = vi.fn();
  render(
    <CartItemRow
      cartType="SERVICE"
      item={{ id: "item", quantity: 1, itemName: "Dọn nhà" }}
      onQuantityChange={change}
      onRemove={() => undefined}
    />,
  );

  expect(screen.getByLabelText("Số lượng")).toHaveTextContent("1");
  expect(screen.queryByRole("button", { name: "Tăng số lượng" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Giảm số lượng" })).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Xóa" }));
  expect(change).not.toHaveBeenCalled();
});
