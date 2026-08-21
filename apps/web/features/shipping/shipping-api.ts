import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function createShippingPolicy(merchantId: string, locationId: string, input: components["schemas"]["ShippingPolicyUpsertRequest"]) {
  const { data, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies", { params: { path: { merchantId, locationId } }, body: input });
  return dataOrThrow(response, data);
}

export async function createDeliveryCoverage(merchantId: string, locationId: string, input: components["schemas"]["DeliveryCoverageUpsertRequest"]) {
  const { data, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages", { params: { path: { merchantId, locationId } }, body: input });
  return dataOrThrow(response, data);
}
