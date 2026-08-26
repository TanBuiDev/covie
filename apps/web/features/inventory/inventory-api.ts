import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export async function setStock(
  merchantId: string,
  locationVariantId: string,
  stockOnHand: number,
  idempotencyKey: string,
  note?: string,
) {
  const { data, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock", {
    params: { path: { merchantId, locationVariantId }, header: { "Idempotency-Key": idempotencyKey } },
    body: { stockOnHand, reasonCode: "SELLER_SET_STOCK", note: note?.trim() || undefined },
  });
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}
