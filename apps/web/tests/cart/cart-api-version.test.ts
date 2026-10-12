import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ patch: vi.fn(), remove: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => ({ PATCH: mocks.patch, DELETE: mocks.remove }) }));
import { removeCartItem, updateCartItem } from "@/features/cart/cart-api";

describe("cart version preconditions", () => {
  it("sends the BE quoted version ETag when updating quantity", async () => {
    mocks.patch.mockResolvedValueOnce({ data: { id: "cart" }, response: new Response(null, { status: 200 }) });
    await updateCartItem("cart", "item", 3, { quantity: 2 });
    expect(mocks.patch).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({
      params: { path: { cartId: "cart", itemId: "item" }, header: { "If-Match": '"v3"' } },
      body: { quantity: 2 },
    }));
  });
  it("sends the same version contract when removing an item", async () => {
    mocks.remove.mockResolvedValueOnce({ response: new Response(null, { status: 204 }) });
    await removeCartItem("cart", "item", 3);
    expect(mocks.remove).toHaveBeenCalledWith(expect.any(String), {
      params: { path: { cartId: "cart", itemId: "item" }, header: { "If-Match": '"v3"' } },
    });
  });
});
