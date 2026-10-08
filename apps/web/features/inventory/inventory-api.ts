import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

export async function setStock(
  merchantId: string,
  locationVariantId: string,
  stockOnHand: number,
  idempotencyKey: string,
  note?: string,
) {
  const { data, error, response } = await createApiClient().POST("/api/v1/merchants/{merchantId}/inventory/{locationVariantId}/set-stock", {
    params: { path: { merchantId, locationVariantId }, header: { "Idempotency-Key": idempotencyKey } },
    body: { stockOnHand, reasonCode: "SELLER_SET_STOCK", note: note?.trim() || undefined },
  });
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}
