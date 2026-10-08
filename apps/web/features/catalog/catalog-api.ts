import type { components, paths } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { locationToQuery, type DiscoveryLocation } from "@/features/location/location-context";

async function requireData<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

async function requireOk(response: Response) {
  if (!response.ok) throw await readApiError(response);
}

function versionEtag(version: number) {
  return `"v${version}"`;
}

export async function createCatalogItem(merchantId: string, idempotencyKey: string, input: components["schemas"]["CreateCatalogItemRequest"]) {
  const client = createApiClient();
  const { data, response } = await client.POST("/api/v1/merchants/{merchantId}/catalog-items", {
    params: { path: { merchantId }, header: { "Idempotency-Key": idempotencyKey } }, body: input,
  });
  return requireData(response, data);
}

export async function getCategoryDetail(categoryId: string): Promise<components["schemas"]["CategoryDetail"]> {
  const client = createApiClient();
  const { data, response } = await client.GET("/api/v1/categories/{categoryId}", {
    params: { path: { categoryId } },
  });
  return requireData(response, data);
}

type DiscoveryQuery = NonNullable<paths["/api/v1/catalog/items"]["get"]>["parameters"] extends { query?: infer Query } ? NonNullable<Query> : never;
export type CatalogDiscoveryFilters = Omit<DiscoveryQuery, "lat" | "lng" | "addressId"> & {
  location?: DiscoveryLocation;
};

function discoveryQuery(filters: CatalogDiscoveryFilters): DiscoveryQuery {
  const { location, ...rest } = filters;
  return { ...locationToQuery(location), ...rest };
}

export async function discoverCatalogItems(filters: CatalogDiscoveryFilters = {}) {
  const client = createApiClient();
  const { data, response } = await client.GET("/api/v1/catalog/items", { params: { query: discoveryQuery(filters) } });
  return requireData(response, data);
}

export async function listPublicCategories() {
  const client = createApiClient();
  const { data, response } = await client.GET("/api/v1/categories");
  return requireData(response, data);
}

export async function getCatalogItem(itemId: string, location?: DiscoveryLocation) {
  const client = createApiClient();
  const { data, response } = await client.GET("/api/v1/catalog/items/{itemId}", {
    params: { path: { itemId }, query: locationToQuery(location) },
  });
  return requireData(response, data);
}

export async function updateCatalogItem(merchantId: string, itemId: string, etag: string,
  input: components["schemas"]["UpdateCatalogItemRequest"]) {
  const client = createApiClient();
  const { response } = await client.PATCH("/api/v1/merchants/{merchantId}/catalog-items/{itemId}", {
    params: { path: { merchantId, itemId }, header: { "If-Match": etag } }, body: input,
  });
  await requireOk(response);
}

export async function updateCatalogVariant(merchantId: string, itemId: string, variantId: string, version: number,
  input: components["schemas"]["UpdateVariantRequest"]) {
  const client = createApiClient();
  const { response } = await client.PATCH("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}", {
    params: { path: { merchantId, itemId, variantId }, header: { "If-Match": versionEtag(version) } }, body: input,
  });
  await requireOk(response);
}

export async function createCatalogVariant(merchantId: string, itemId: string,
  input: components["schemas"]["CreateVariantRequest"]) {
  const client = createApiClient();
  const { response } = await client.POST("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants", {
    params: { path: { merchantId, itemId } }, body: input,
  });
  await requireOk(response);
}

export async function deleteCatalogVariant(merchantId: string, itemId: string, variantId: string, version: number) {
  const client = createApiClient();
  const { response } = await client.DELETE("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/variants/{variantId}", {
    params: { path: { merchantId, itemId, variantId }, header: { "If-Match": versionEtag(version) } },
  });
  await requireOk(response);
}

export async function updateCatalogLocationOffer(merchantId: string, itemId: string, offerId: string, version: number,
  input: components["schemas"]["UpdateLocationOfferRequest"]) {
  const client = createApiClient();
  const { response } = await client.PATCH("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}", {
    params: { path: { merchantId, itemId, locationVariantId: offerId }, header: { "If-Match": versionEtag(version) } }, body: input,
  });
  await requireOk(response);
}

export type CatalogLifecycleAction = "PUBLISH" | "PAUSE" | "ACTIVATE" | "ARCHIVE";

export async function runCatalogLifecycleAction(merchantId: string, itemId: string, action: CatalogLifecycleAction,
  etag: string) {
  const client = createApiClient();
  const params = { params: { path: { merchantId, itemId }, header: { "If-Match": etag } } };
  const { response } = action === "PUBLISH"
    ? await client.POST("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish", params)
    : action === "PAUSE"
      ? await client.POST("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause", params)
      : action === "ACTIVATE"
        ? await client.POST("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate", params)
        : await client.POST("/api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive", params);
  await requireOk(response);
}
