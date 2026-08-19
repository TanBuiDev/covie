import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

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
