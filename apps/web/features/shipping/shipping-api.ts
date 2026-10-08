import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}

function versionEtag(version: number | undefined): string {
  if (!Number.isSafeInteger(version) || (version ?? -1) < 0) throw new Error("RESOURCE_VERSION_REQUIRED");
  return `\"v${version}\"`;
}

export async function listLocationAreas() {
  const { data, error, response } = await createApiClient().GET("/api/v1/location-areas");
  return dataOrThrow(response, data, error);
}

export async function createShippingPolicy(
  merchantId: string,
  locationId: string,
  input: components["schemas"]["ShippingPolicyUpsertRequest"],
) {
  const { data, error, response } = await createApiClient().POST(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies",
    { params: { path: { merchantId, locationId } }, body: input },
  );
  return dataOrThrow(response, data, error);
}

export async function updateShippingPolicy(
  merchantId: string,
  locationId: string,
  policyId: string,
  version: number,
  input: components["schemas"]["ShippingPolicyUpsertRequest"],
) {
  const { data, error, response } = await createApiClient().PATCH(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId}",
    {
      params: { path: { merchantId, locationId, policyId }, header: { "If-Match": versionEtag(version) } },
      body: input,
    },
  );
  return dataOrThrow(response, data, error);
}

export async function deleteShippingPolicy(
  merchantId: string,
  locationId: string,
  policyId: string,
  version: number,
) {
  const { error, response } = await createApiClient().DELETE(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies/{policyId}",
    { params: { path: { merchantId, locationId, policyId }, header: { "If-Match": versionEtag(version) } } },
  );
  if (!response.ok) throw await parsedApiError(response, error);
}

export async function createDeliveryCoverage(
  merchantId: string,
  locationId: string,
  input: components["schemas"]["DeliveryCoverageUpsertRequest"],
) {
  const { data, error, response } = await createApiClient().POST(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages",
    { params: { path: { merchantId, locationId } }, body: input },
  );
  return dataOrThrow(response, data, error);
}

export async function updateDeliveryCoverage(
  merchantId: string,
  locationId: string,
  coverageId: string,
  version: number,
  input: components["schemas"]["DeliveryCoverageUpsertRequest"],
) {
  const { data, error, response } = await createApiClient().PATCH(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}",
    {
      params: { path: { merchantId, locationId, coverageId }, header: { "If-Match": versionEtag(version) } },
      body: input,
    },
  );
  return dataOrThrow(response, data, error);
}

export async function deleteDeliveryCoverage(
  merchantId: string,
  locationId: string,
  coverageId: string,
  version: number,
) {
  const { error, response } = await createApiClient().DELETE(
    "/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages/{coverageId}",
    { params: { path: { merchantId, locationId, coverageId }, header: { "If-Match": versionEtag(version) } } },
  );
  if (!response.ok) throw await parsedApiError(response, error);
}
