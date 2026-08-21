import { fireEvent, render, screen } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { CartItemRow } from "./cart-item-row";

it("does not allow a service cart quantity above one", () => {
  const change = vi.fn();
  render(<CartItemRow cartType="SERVICE" item={{ id: "item", quantity: 1, itemName: "Dọn nhà" }} onQuantityChange={change} onRemove={() => undefined} />);
  expect(screen.getByRole("button", { name: "Tăng số lượng" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "Tăng số lượng" }));
  expect(change).not.toHaveBeenCalled();
});
