import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";

const { getCart, updateCartItem } = vi.hoisted(() => ({ getCart: vi.fn(), updateCartItem: vi.fn() }));
vi.mock("./cart-api", () => ({ getCart, updateCartItem, removeCartItem: vi.fn() }));

import { CartList } from "./cart-list";

afterEach(() => {
  cleanup();
  getCart.mockReset();
  updateCartItem.mockReset();
});

it("refetches the cart when a stale version rejects a quantity change", async () => {
  updateCartItem.mockRejectedValueOnce(new Error("ETag mismatch"));
  getCart.mockResolvedValueOnce({
    id: "cart", merchantLocationId: "location", cartType: "PRODUCT", version: 2,
    items: [{ id: "item", itemName: "Rau sạch", quantity: 1, priceSnapshot: 10_000 }],
  });
  render(<CartList initialCarts={[{
    id: "cart", merchantLocationId: "location", cartType: "PRODUCT", version: 1,
    items: [{ id: "item", itemName: "Rau sạch", quantity: 1, priceSnapshot: 10_000 }],
  }]} />);

  fireEvent.click(screen.getByRole("button", { name: "Tăng số lượng" }));

  await waitFor(() => expect(getCart).toHaveBeenCalledWith("cart"));
  expect(await screen.findByText("Giỏ hàng vừa thay đổi. Đã tải lại dữ liệu mới nhất.")).toBeInTheDocument();
});
