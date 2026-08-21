import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export async function setStock(merchantId: string, locationVariantId: string, stockOnHand: number) {
  const { data, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock", {
    params: { path: { merchantId, locationVariantId }, header: { "Idempotency-Key": crypto.randomUUID() } },
    body: { stockOnHand, reasonCode: "SELLER_SET_STOCK" },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
