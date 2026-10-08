import { afterEach, expect, it, vi } from "vitest";
import { getCart, listCarts, removeCartItem, updateCartItem } from "./cart-api";
import { invalidateCsrfToken } from "@/features/auth/csrf";

afterEach(() => { invalidateCsrfToken(); vi.unstubAllGlobals(); });

it.each(["update", "remove", "get", "list"])("preserves parsed cart errors for %s", async operation => {
  const fetch = vi.fn();
  if (operation === "update" || operation === "remove") fetch.mockResolvedValueOnce(new Response(JSON.stringify({ headerName: "X-CSRF-Token", token: "fixture" })));
  fetch.mockResolvedValueOnce(new Response(JSON.stringify({ error: { code: "RESOURCE_VERSION_CONFLICT", requestId: "fixture-request" } }), { status: 409 }));
  vi.stubGlobal("fetch", fetch);
  const result = operation === "update" ? updateCartItem("cart", "item", 1, { quantity: 2 })
    : operation === "remove" ? removeCartItem("cart", "item", 1)
      : operation === "get" ? getCart("cart") : listCarts();
  await expect(result).rejects.toMatchObject({ status: 409, code: "RESOURCE_VERSION_CONFLICT", requestId: "fixture-request" });
});
