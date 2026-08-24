import { readApiError } from "@/lib/api/api-error";
import { requestBrowserApi } from "@/lib/api/api-client";
import type { components } from "@covie/api-client";

export type NeedType = components["schemas"]["CreateNeedRequest"]["type"];
export type NeedStatus = NonNullable<components["schemas"]["NeedSummaryResponse"]["status"]>;
export type NeedSummary = Required<components["schemas"]["NeedSummaryResponse"]>;
export type NeedDetail = Required<components["schemas"]["NeedDetailResponse"]>;
export type NeedResponseItem = Omit<Required<components["schemas"]["NeedResponseItemResponse"]>, "priceAtResponse" | "currentPrice"> & {
  priceAtResponse?: Required<components["schemas"]["MoneyResponse"]>;
  currentPrice?: Required<components["schemas"]["MoneyResponse"]>;
};
export type NeedResponse = Omit<Required<components["schemas"]["NeedResponseResponse"]>, "items"> & {
  items: NeedResponseItem[];
};
export type MerchantNeedMatch = Omit<Required<components["schemas"]["MerchantNeedMatchResponse"]>, "need" | "resolvedLocation" | "responseConstraints"> & {
  need: Required<components["schemas"]["Need"]>;
  resolvedLocation: Required<components["schemas"]["ResolvedLocation"]>;
  responseConstraints: Required<components["schemas"]["ResponseConstraints"]>;
};
type CursorPage<T> = { items: T[]; hasMore: boolean; nextCursor?: string };
export type CreateNeedInput = components["schemas"]["CreateNeedRequest"];

async function jsonOrThrow<T>(response: Response): Promise<T> {
  if (!response.ok) throw await readApiError(response);
  return (await response.json()) as T;
}

export async function listMyNeeds(status?: NeedStatus, cursor?: string): Promise<CursorPage<NeedSummary>> {
  const params = new URLSearchParams();
  if (status) params.set("status", status);
  if (cursor) params.set("cursor", cursor);
  const response = await requestBrowserApi("GET", `/api/v1/me/need-requests${params.size ? `?${params}` : ""}`);
  return jsonOrThrow(response);
}

export async function getMyNeed(needId: string): Promise<NeedDetail> {
  return jsonOrThrow(await requestBrowserApi("GET", `/api/v1/me/need-requests/${needId}`));
}

export async function createNeed(input: CreateNeedInput): Promise<NeedDetail> {
  return jsonOrThrow(await requestBrowserApi("POST", "/api/v1/me/need-requests", input, {
    "Idempotency-Key": crypto.randomUUID(),
  }));
}

export async function cancelNeed(needId: string): Promise<NeedDetail> {
  return jsonOrThrow(await requestBrowserApi("POST", `/api/v1/me/need-requests/${needId}/cancel`, undefined, {
    "Idempotency-Key": crypto.randomUUID(),
  }));
}

export async function listNeedResponses(needId: string, cursor?: string): Promise<CursorPage<NeedResponse>> {
  const params = new URLSearchParams();
  if (cursor) params.set("cursor", cursor);
  const response = await requestBrowserApi(
    "GET",
    `/api/v1/me/need-requests/${needId}/responses${params.size ? `?${params}` : ""}`,
  );
  return jsonOrThrow(response);
}

export async function discoverMerchantNeeds(
  merchantId: string,
  locationId?: string,
  cursor?: string,
): Promise<CursorPage<MerchantNeedMatch>> {
  const params = new URLSearchParams();
  if (locationId) params.set("locationId", locationId);
  if (cursor) params.set("cursor", cursor);
  return jsonOrThrow(await requestBrowserApi("GET", `/api/v1/merchants/${merchantId}/need-requests?${params}`));
}

export async function respondToNeed(
  needId: string,
  input: { merchantLocationId: string; locationVariantIds: string[]; note?: string },
): Promise<NeedResponse> {
  return jsonOrThrow(await requestBrowserApi("POST", `/api/v1/need-requests/${needId}/responses`, input, {
    "Idempotency-Key": crypto.randomUUID(),
  }));
}

export async function previewNeedCheckout(input: {
  needRequestId: string;
  needResponseItemId: string;
  locationVariantId: string;
  quantity: number;
  fulfillmentMethod: "PICKUP" | "SELLER_DELIVERY" | "SERVICE";
  addressId?: string;
  paymentMethod: "COD" | "BANK_TRANSFER_NEGOTIATED";
}) {
  return jsonOrThrow<{ checkoutToken: string }>(await requestBrowserApi("POST", "/api/v1/checkouts/preview", {
    source: {
      type: "NEED_REQUEST",
      needRequestId: input.needRequestId,
      needResponseItemId: input.needResponseItemId,
    },
    items: [{ locationVariantId: input.locationVariantId, quantity: input.quantity }],
    fulfillmentMethod: input.fulfillmentMethod,
    addressId: input.addressId || null,
    paymentMethod: input.paymentMethod,
  }));
}

export async function commitNeedCheckout(checkoutToken: string) {
  return jsonOrThrow<{ id: string; orderCode: string }>(await requestBrowserApi("POST", "/api/v1/checkouts/commit", {
    checkoutToken,
  }, { "Idempotency-Key": crypto.randomUUID() }));
}
