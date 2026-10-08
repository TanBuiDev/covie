import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}

export async function previewCheckout(input: components["schemas"]["CheckoutPreviewRequest"]) {
  const { data, error, response } = await createApiClient().POST("/api/v1/checkouts/preview", { body: input });
  return dataOrThrow(response, data, error);
}

export async function commitCheckout(checkoutToken: string, idempotencyKey: string) {
  const { data, error, response } = await createApiClient().POST("/api/v1/checkouts/commit", {
    params: { header: { "Idempotency-Key": idempotencyKey } }, body: { checkoutToken },
  });
  return dataOrThrow(response, data, error);
}
