import type { components } from "@covie/api-client";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { locationToQuery, type DiscoveryLocation } from "@/features/location/location-context";

export async function listCatalogItemsForServer(merchantId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/catalog-items", {
    params: { path: { merchantId } },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listCategoriesForServer(type?: components["schemas"]["CreateCatalogItemRequest"]["type"]) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/categories", {
    params: { query: type ? { type } : {} },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function getCatalogItemForServer(itemId: string, location?: DiscoveryLocation) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/catalog/items/{itemId}", {
    params: { path: { itemId }, query: locationToQuery(location) },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function getCatalogManagementDetailForServer(merchantId: string, itemId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/catalog-items/{itemId}", {
    params: { path: { merchantId, itemId } },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return { item: data, etag: response.headers.get("ETag") };
}
