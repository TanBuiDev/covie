import type { components, paths } from "@covie/api-client";
import { readApiError } from "@/lib/api/api-error";
import { requestBrowserApi } from "@/lib/api/api-client";

type GeneratedRequired<T> = T extends Array<infer Item>
  ? GeneratedRequired<Item>[]
  : T extends object
    ? { [Key in keyof T]-?: GeneratedRequired<NonNullable<T[Key]>> }
    : T;

export type NeedType = NonNullable<components["schemas"]["NeedSummaryResponse"]["type"]>;
export type NeedStatus = NonNullable<components["schemas"]["NeedSummaryResponse"]["status"]>;
export type NeedSummary = GeneratedRequired<components["schemas"]["NeedSummaryResponse"]>;
export type NeedDetail = GeneratedRequired<components["schemas"]["NeedDetailResponse"]>;
export type NeedResponseItem = GeneratedRequired<components["schemas"]["NeedResponseItemResponse"]>;
export type NeedResponse = GeneratedRequired<components["schemas"]["NeedResponseResponse"]>;
export type MerchantNeedMatch = components["schemas"]["MerchantNeedMatchResponse"];
export type NeedCategory = components["schemas"]["CategorySummary"];
export type NeedAddress = components["schemas"]["UserAddressResponse"];
export type MerchantNeedPage = components["schemas"]["MerchantNeedCursorPage"];
export type NeedEligibleItem = components["schemas"]["NeedEligibleItemResponse"];
export type NeedEligibleItemPage = components["schemas"]["NeedEligibleItemCursorPage"];
type EligibleNeedQuery = paths["/api/v1/merchants/{merchantId}/need-requests/{needRequestId}/eligible-items"]["get"]["parameters"]["query"];
type MerchantResponseQuery = paths["/api/v1/merchants/{merchantId}/need-requests/{needRequestId}/response"]["get"]["parameters"]["query"];
type CursorPage<T> = { items: T[]; hasMore: boolean; nextCursor?: string | null };
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

export async function listNeedCategories(type: NeedType): Promise<NeedCategory[]> {
  const params = new URLSearchParams({ type });
  return jsonOrThrow(await requestBrowserApi("GET", `/api/v1/categories?${params}`));
}

export async function listNeedAddresses(): Promise<NeedAddress[]> {
  return jsonOrThrow(await requestBrowserApi("GET", "/api/v1/me/addresses"));
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
): Promise<MerchantNeedPage> {
  const params = new URLSearchParams();
  if (locationId) params.set("locationId", locationId);
  if (cursor) params.set("cursor", cursor);
  params.set("limit", "20");
  return jsonOrThrow(await requestBrowserApi(
    "GET",
    `/api/v1/merchants/${encodeURIComponent(merchantId)}/need-requests${params.size ? `?${params}` : ""}`,
  ));
}

export async function listEligibleNeedItems(
  merchantId: string,
  needId: string,
  locationId: string,
  cursor?: string,
): Promise<NeedEligibleItemPage> {
  const query: EligibleNeedQuery = { locationId, cursor, limit: 20 };
  const params = new URLSearchParams({ locationId: query.locationId, limit: String(query.limit) });
  if (query.cursor) params.set("cursor", query.cursor);
  const path = `/api/v1/merchants/${encodeURIComponent(merchantId)}/need-requests/${encodeURIComponent(needId)}/eligible-items?${params}`;
  return jsonOrThrow(await requestBrowserApi("GET", path));
}

export async function getMerchantNeedResponse(
  merchantId: string,
  needId: string,
  locationId: string,
): Promise<NeedResponse> {
  const query: MerchantResponseQuery = { locationId };
  const params = new URLSearchParams({ locationId: query.locationId });
  const path = `/api/v1/merchants/${encodeURIComponent(merchantId)}/need-requests/${encodeURIComponent(needId)}/response?${params}`;
  return jsonOrThrow(await requestBrowserApi("GET", path));
}

export async function respondToNeed(
  needId: string,
  input: { merchantLocationId: string; locationVariantIds: string[]; note?: string },
  idempotencyKey = crypto.randomUUID(),
): Promise<NeedResponse> {
  return jsonOrThrow(await requestBrowserApi("POST", `/api/v1/need-requests/${encodeURIComponent(needId)}/responses`, input, {
    "Idempotency-Key": idempotencyKey,
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
