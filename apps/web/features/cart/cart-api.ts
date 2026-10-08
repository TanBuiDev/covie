import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}

export async function listCarts() {
  const { data, error, response } = await createApiClient().GET("/api/v1/me/carts");
  return dataOrThrow(response, data, error);
}

export async function getCart(cartId: string) {
  const { data, error, response } = await createApiClient().GET("/api/v1/me/carts/{cartId}", { params: { path: { cartId } } });
  return dataOrThrow(response, data, error);
}

export async function addCartItem(input: components["schemas"]["AddCartItemRequest"]) {
  const { data, error, response } = await createApiClient().POST("/api/v1/me/carts/items", { body: input });
  const result = await dataOrThrow(response, data, error);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("covie:cart-updated"));
  }
  return result;
}

export async function updateCartItem(cartId: string, itemId: string, version: number,
  input: components["schemas"]["UpdateCartItemRequest"]) {
  const { data, error, response } = await createApiClient().PATCH("/api/v1/me/carts/{cartId}/items/{itemId}", {
    params: { path: { cartId, itemId }, header: { "If-Match": String(version) } }, body: input,
  });
  const result = await dataOrThrow(response, data, error);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("covie:cart-updated"));
  }
  return result;
}

export async function removeCartItem(cartId: string, itemId: string, version: number) {
  const { error, response } = await createApiClient().DELETE("/api/v1/me/carts/{cartId}/items/{itemId}", {
    params: { path: { cartId, itemId }, header: { "If-Match": String(version) } },
  });
  if (!response.ok) throw await parsedApiError(response, error);
  if (typeof window !== "undefined") {
    window.dispatchEvent(new CustomEvent("covie:cart-updated"));
  }
}
