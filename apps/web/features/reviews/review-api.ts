import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

export type CreateReview = components["schemas"]["CreateReviewRequest"];
export type PublicReview = components["schemas"]["PublicReviewResponse"];

export async function createReview(input: CreateReview, idempotencyKey: string) {
  const { data, error, response } = await createApiClient().POST("/api/v1/me/reviews", { body: input, params: { header: { "Idempotency-Key": idempotencyKey } } });
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}
export async function listCatalogReviews(itemId: string, cursor?: string) {
  const { data, response } = await createApiClient().GET("/api/v1/catalog/items/{itemId}/reviews", { params: { path: { itemId }, query: cursor ? { cursor } : {} } });
  if (!response.ok || !data) throw new Error("Không thể tải đánh giá.");
  return data;
}
export async function listMerchantReviews(merchantId: string, cursor?: string) {
  const { data, response } = await createApiClient().GET("/api/v1/merchants/{merchantId}/reviews", { params: { path: { merchantId }, query: cursor ? { cursor } : {} } });
  if (!response.ok || !data) throw new Error("Không thể tải đánh giá.");
  return data;
}
