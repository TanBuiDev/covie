import { readApiError } from "@/lib/api/api-error";
import { requestBrowserApi } from "@/lib/api/api-client";

export type NeedType = "PRODUCT" | "SERVICE";
export type NeedStatus = "ACTIVE" | "FULFILLED" | "EXPIRED" | "CANCELLED";

export type NeedSummary = {
  id: string;
  type: NeedType;
  status: NeedStatus;
  categoryId: string;
  title: string;
  radiusMeters: number;
  expiresAt: string;
  responseCount: number;
  createdAt: string;
};

export type NeedDetail = NeedSummary & {
  queryText: string;
  description?: string | null;
  requestedQuantity?: number | null;
  locationSummary?: { addressId?: string | null; locationAreaId?: string | null; timezone?: string | null } | null;
  fulfilledOrderId?: string | null;
  closedAt?: string | null;
};

export type NeedResponseItem = {
  id: string;
  locationVariantId: string;
  catalogItemId: string;
  variantId: string;
  priceAtResponse?: { amount: number; currency: string } | null;
  currentPrice?: { amount: number; currency: string } | null;
  available: boolean;
  actionable: boolean;
};

export type NeedResponse = {
  id: string;
  needRequestId: string;
  merchantId: string;
  merchantName?: string | null;
  merchantLocationId: string;
  merchantLocationName?: string | null;
  distanceMeters?: number | null;
  status: "ACTIVE" | "WITHDRAWN";
  note?: string | null;
  version: number;
  createdAt: string;
  items: NeedResponseItem[];
};

export type MerchantNeedMatch = {
  need: { id: string; type: NeedType; title: string; description?: string | null; categoryId: string; expiresAt: string };
  resolvedLocation: { id: string; name: string };
  buyerArea?: { label?: string | null } | null;
  distanceMeters: number;
  alreadyResponded: boolean;
  responseConstraints: { maxItems: number };
};

type CursorPage<T> = { items: T[]; hasMore: boolean; nextCursor?: string | null };

export type CreateNeedInput = {
  type: NeedType;
  categoryId: string;
  title: string;
  queryText: string;
  description?: string;
  requestedQuantity?: number;
  addressId?: string;
  lat?: number;
  lng?: number;
  radiusMeters: number;
  expiresAt: string;
};

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
