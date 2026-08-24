import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";

const { getCart, updateCartItem } = vi.hoisted(() => ({ getCart: vi.fn(), updateCartItem: vi.fn() }));
vi.mock("./cart-api", () => ({ getCart, updateCartItem, removeCartItem: vi.fn() }));

import { CartDetail } from "./cart-detail";

afterEach(() => {
  cleanup();
  getCart.mockReset();
  updateCartItem.mockReset();
});

it("refetches the cart when a stale version rejects a quantity change", async () => {
  updateCartItem.mockRejectedValueOnce(new CovieApiError(409, "RESOURCE_VERSION_CONFLICT", "stale"));
  getCart.mockResolvedValueOnce({
    id: "cart", merchantLocationId: "location", cartType: "PRODUCT", version: 2,
    items: [{ id: "item", itemName: "Rau sạch", quantity: 1, priceSnapshot: 10_000 }],
  });
  render(<CartDetail initialCart={{
    id: "cart", merchantLocationId: "location", cartType: "PRODUCT", version: 1,
    items: [{ id: "item", itemName: "Rau sạch", quantity: 1, priceSnapshot: 10_000 }],
  }} />);

  fireEvent.click(screen.getByRole("button", { name: "Tăng số lượng" }));

  await waitFor(() => expect(getCart).toHaveBeenCalledWith("cart"));
  expect(await screen.findByText(/Giỏ hàng đã thay đổi ở nơi khác/)).toBeInTheDocument();
  expect(screen.getByLabelText("Số lượng")).toHaveTextContent("1");
});
