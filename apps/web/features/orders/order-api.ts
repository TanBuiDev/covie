import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { parsedApiError } from "@/lib/api/api-error";

export type OrderSummary = components["schemas"]["OrderSummaryResponse"];
export type OrderDetail = components["schemas"]["OrderDetailResponse"];
export type OrderAction = NonNullable<OrderDetail["allowedActions"]>[number];

type OrderActionInput = {
  reasonCode?: string;
  note?: string;
};

type MerchantOrderQuery = {
  cursor?: string;
  locationId?: string;
  limit?: number;
};

async function dataOrThrow<T>(response: Response, data: T | undefined, error?: unknown): Promise<T> {
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}

async function ensureOk(response: Response, error?: unknown): Promise<void> {
  if (!response.ok) throw await parsedApiError(response, error);
}

export async function myOrders(query: { cursor?: string; limit?: number } = {}): Promise<components["schemas"]["OrderPageResponse"]> {
  const { data, error, response } = await createApiClient().GET("/api/v1/me/orders", { params: { query } });
  return dataOrThrow(response, data, error);
}

export async function merchantOrders(
  merchantId: string,
  query: MerchantOrderQuery = {},
): Promise<components["schemas"]["OrderPageResponse"]> {
  const { data, error, response } = await createApiClient().GET("/api/v1/merchants/{merchantId}/orders", {
    params: { path: { merchantId }, query: { limit: query.limit ?? 20, cursor: query.cursor, locationId: query.locationId } },
  });
  return dataOrThrow(response, data, error);
}

export async function getOrder(orderId: string): Promise<OrderDetail> {
  const { data, error, response } = await createApiClient().GET("/api/v1/orders/{orderId}", {
    params: { path: { orderId } },
  });
  return dataOrThrow(response, data, error);
}

/**
 * Executes only commands explicitly returned by backend `allowedActions`.
 * The caller owns one stable idempotency key for the complete dialog intent, including retries.
 */
export async function orderCommand(
  orderId: string,
  action: OrderAction,
  intentKey: string,
  input: OrderActionInput = {},
): Promise<void> {
  const client = createApiClient();
  const path = { orderId };
  const header = { "Idempotency-Key": intentKey };

  switch (action) {
    case "CONFIRM": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/confirm", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "REJECT": {
      if (!input.reasonCode) throw new Error("REASON_REQUIRED");
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/reject", {
        params: { path, header }, body: { reasonCode: input.reasonCode, note: input.note },
      });
      return ensureOk(response, error);
    }
    case "CANCEL": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/cancel", {
        params: { path, header },
        body: input.reasonCode || input.note ? { reasonCode: input.reasonCode, note: input.note } : undefined,
      });
      return ensureOk(response, error);
    }
    case "REQUEST_CANCELLATION": {
      if (!input.reasonCode) throw new Error("REASON_REQUIRED");
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/request-cancellation", {
        params: { path, header }, body: { reasonCode: input.reasonCode, note: input.note },
      });
      return ensureOk(response, error);
    }
    case "ACCEPT_CANCELLATION": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/accept-cancellation", {
        params: { path, header }, body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response, error);
    }
    case "REJECT_CANCELLATION": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/reject-cancellation", {
        params: { path, header }, body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response, error);
    }
    case "MARK_OUT_FOR_DELIVERY": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/mark-out-for-delivery", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "MARK_DELIVERED": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/mark-delivered", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "MARK_READY_FOR_PICKUP": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/mark-ready-for-pickup", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "MARK_SERVICE_PERFORMED": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/mark-service-performed", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "CONFIRM_RECEIVED": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/confirm-received", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "CONFIRM_COMPLETED": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/confirm-completed", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "REPORT_DELIVERY_ISSUE": {
      if (!input.note?.trim()) throw new Error("REASON_REQUIRED");
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/report-delivery-issue", {
        params: { path, header }, body: { reasonText: input.note.trim() },
      });
      return ensureOk(response, error);
    }
    case "REPORT_SERVICE_ISSUE": {
      if (!input.note?.trim()) throw new Error("REASON_REQUIRED");
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/report-service-issue", {
        params: { path, header }, body: { reasonText: input.note.trim() },
      });
      return ensureOk(response, error);
    }
    case "REPORT_BUYER_NO_SHOW": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/report-buyer-no-show", { params: { path, header } });
      return ensureOk(response, error);
    }
    case "CONTEST_NO_SHOW": {
      const { error, response } = await client.POST("/api/v1/orders/{orderId}/contest-no-show", {
        params: { path, header }, body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response, error);
    }
  }
}
