import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

async function dataOrThrow<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

export async function previewCheckout(input: components["schemas"]["CheckoutPreviewRequest"]) {
  const { data, response } = await createApiClient().POST("/api/v1/checkouts/preview", { body: input });
  return dataOrThrow(response, data);
}

export async function commitCheckout(checkoutToken: string, idempotencyKey: string) {
  const { data, response } = await createApiClient().POST("/api/v1/checkouts/commit", {
    params: { header: { "Idempotency-Key": idempotencyKey } }, body: { checkoutToken },
  });
  return dataOrThrow(response, data);
}
