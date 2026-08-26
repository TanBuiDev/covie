import type { components } from "@covie/api-client";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import type { Etagged } from "./merchant-api";

async function requireData<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listMyMerchantsForServer() {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/merchants");
  return requireData(response, data);
}

export async function getMerchantForServer(merchantId: string): Promise<Etagged<components["schemas"]["MerchantManagementResponse"]>> {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}", { params: { path: { merchantId } } });
  return { data: await requireData(response, data), etag: response.headers.get("etag") ?? "" };
}

export async function getPublicMerchantForServer(merchantId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/catalog/merchants/{merchantId}", { params: { path: { merchantId } } });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listPublicMerchantReviewsForServer(merchantId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/reviews", { params: { path: { merchantId }, query: {} } });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function listLocationsForServer(merchantId: string) {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations", { params: { path: { merchantId } } });
  return requireData(response, data);
}

export async function getLocationForServer(
  merchantId: string,
  locationId: string,
): Promise<Etagged<components["schemas"]["MerchantLocationResponse"]>> {
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}", {
    params: { path: { merchantId, locationId } },
  });
  return { data: await requireData(response, data), etag: response.headers.get("etag") ?? "" };
}
