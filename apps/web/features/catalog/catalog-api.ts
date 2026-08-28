import type { components, paths } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { locationToQuery, type DiscoveryLocation } from "@/features/location/location-context";

async function requireData<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function createCatalogItem(merchantId: string, idempotencyKey: string, input: components["schemas"]["CreateCatalogItemRequest"]) {
  const client = createApiClient();
  const { data, response } = await client.POST("/api/v1/merchants/{merchantId}/catalog-items", {
    params: { path: { merchantId }, header: { "Idempotency-Key": idempotencyKey } }, body: input,
  });
  return requireData(response, data);
}

export async function updateCatalogItem(
  merchantId: string,
  itemId: string,
  version: number,
  input: components["schemas"]["UpdateCatalogItemRequest"],
) {
  const { response } = await createApiClient().PATCH("/api/v1/merchants/{merchantId}/catalog-items/{itemId}", {
    params: { path: { merchantId, itemId }, header: { "If-Match": `"v${version}"` } },
    body: input,
  });
  if (!response.ok) throw await readApiError(response);
}

export async function updateCatalogLocationOffer(
  merchantId: string,
  itemId: string,
  locationVariantId: string,
  version: number,
  input: components["schemas"]["UpdateLocationOfferRequest"],
) {
  const { response } = await createApiClient().PATCH(
    "/api/v1/merchants/{merchantId}/catalog-items/{itemId}/location-offers/{locationVariantId}",
    {
      params: {
        path: { merchantId, itemId, locationVariantId },
        header: { "If-Match": `"v${version}"` },
      },
      body: input,
    },
  );
  if (!response.ok) throw await readApiError(response);
}

export async function transitionCatalogItem(
  merchantId: string,
  itemId: string,
  version: number,
  action: "publish" | "pause" | "activate" | "archive",
) {
  const { response } = await createApiClient().POST(
    `/api/v1/merchants/{merchantId}/catalog-items/{itemId}/${action}` as
      | "/api/v1/merchants/{merchantId}/catalog-items/{itemId}/publish"
      | "/api/v1/merchants/{merchantId}/catalog-items/{itemId}/pause"
      | "/api/v1/merchants/{merchantId}/catalog-items/{itemId}/activate"
      | "/api/v1/merchants/{merchantId}/catalog-items/{itemId}/archive",
    { params: { path: { merchantId, itemId }, header: { "If-Match": `"v${version}"` } } },
  );
  if (!response.ok) throw await readApiError(response);
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
