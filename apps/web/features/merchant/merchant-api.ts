import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { CovieApiError, isApiErrorShape, readApiError } from "@/lib/api/api-error";

export type Etagged<T> = { data: T; etag: string };

async function requireData<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok) {
    if (isApiErrorShape(error)) {
      throw new CovieApiError(response.status, error.error.code, error.error.message ?? error.error.code,
        error.error.requestId, error.error.fields);
    }
    throw await readApiError(response);
  }
  if (!data) throw await readApiError(response);
  return data;
}

export async function listMyMerchants() {
  const client = createApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/merchants");
  return requireData(response, data, error);
}

export async function createMerchant(input: components["schemas"]["CreateMerchantRequest"]) {
  const client = createApiClient();
  const { data, error, response } = await client.POST("/api/v1/me/merchants", { body: input });
  return requireData(response, data, error);
}

export async function getMerchant(merchantId: string): Promise<Etagged<components["schemas"]["MerchantManagementResponse"]>> {
  const client = createApiClient();
  const { data, error, response } = await client.GET("/api/v1/merchants/{merchantId}", { params: { path: { merchantId } } });
  return { data: await requireData(response, data, error), etag: response.headers.get("etag") ?? "" };
}

export async function updateMerchant(merchantId: string, etag: string, input: components["schemas"]["UpdateMerchantRequest"]) {
  const client = createApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/merchants/{merchantId}", {
    params: { path: { merchantId }, header: { "If-Match": etag } }, body: input,
  });
  return { data: await requireData(response, data, error), etag: response.headers.get("etag") ?? "" };
}

export async function listLocations(merchantId: string) {
  const client = createApiClient();
  const { data, error, response } = await client.GET("/api/v1/merchants/{merchantId}/locations", { params: { path: { merchantId } } });
  return requireData(response, data, error);
}

export async function createLocation(merchantId: string, input: components["schemas"]["MerchantLocationUpsertRequest"]) {
  const client = createApiClient();
  const { data, error, response } = await client.POST("/api/v1/merchants/{merchantId}/locations", { params: { path: { merchantId } }, body: input });
  return { data: await requireData(response, data, error), etag: response.headers.get("etag") ?? "" };
}

export async function updateLocation(merchantId: string, locationId: string, etag: string, input: components["schemas"]["MerchantLocationUpsertRequest"]) {
  const client = createApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/merchants/{merchantId}/locations/{locationId}", {
    params: { path: { merchantId, locationId }, header: { "If-Match": etag } }, body: input,
  });
  return { data: await requireData(response, data, error), etag: response.headers.get("etag") ?? "" };
}

export async function listPublicMerchantCatalog(merchantId: string, cursor?: string) {
  const client = createApiClient();
  const { data, error, response } = await client.GET("/api/v1/catalog/merchants/{merchantId}/items", {
    params: { path: { merchantId }, query: { limit: 3, ...(cursor ? { cursor } : {}) } },
  });
  return requireData(response, data, error);
}
