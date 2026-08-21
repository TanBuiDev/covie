import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listCarts() {
  const { data, response } = await createApiClient().GET("/api/v1/me/carts");
  return dataOrThrow(response, data);
}

export async function getCart(cartId: string) {
  const { data, response } = await createApiClient().GET("/api/v1/me/carts/{cartId}", { params: { path: { cartId } } });
  return dataOrThrow(response, data);
}

export async function addCartItem(input: components["schemas"]["AddCartItemRequest"]) {
  const { data, response } = await createApiClient().POST("/api/v1/me/carts/items", { body: input });
  return dataOrThrow(response, data);
}

export async function updateCartItem(cartId: string, itemId: string, version: number,
  input: components["schemas"]["UpdateCartItemRequest"]) {
  const { data, response } = await createApiClient().PATCH("/api/v1/me/carts/{cartId}/items/{itemId}", {
    params: { path: { cartId, itemId }, header: { "If-Match": String(version) } }, body: input,
  });
  return dataOrThrow(response, data);
}

export async function removeCartItem(cartId: string, itemId: string, version: number) {
  const { response } = await createApiClient().DELETE("/api/v1/me/carts/{cartId}/items/{itemId}", {
    params: { path: { cartId, itemId }, header: { "If-Match": String(version) } },
  });
  if (!response.ok) throw await readApiError(response);
}
