import type { components } from "@covie/api-client";
import { createApiClient } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type OrderSummary = components["schemas"]["OrderSummaryResponse"];
export type OrderDetail = components["schemas"]["OrderDetailResponse"];
export type OrderAction = NonNullable<OrderDetail["allowedActions"]>[number];

type OrderActionInput = {
  reasonCode?: string;
  note?: string;
};

async function dataOrThrow<T>(response: Response, data: T | undefined): Promise<T> {
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

async function ensureOk(response: Response): Promise<void> {
  if (!response.ok) throw await readApiError(response);
}

export async function myOrders(): Promise<components["schemas"]["OrderPageResponse"]> {
  const { data, response } = await createApiClient().GET("/api/v1/me/orders");
  return dataOrThrow(response, data);
}

export async function merchantOrders(merchantId: string): Promise<components["schemas"]["OrderPageResponse"]> {
  const { data, response } = await createApiClient().GET("/api/v1/merchants/{merchantId}/orders", {
    params: { path: { merchantId } },
  });
  return dataOrThrow(response, data);
}

export async function getOrder(orderId: string): Promise<OrderDetail> {
  const { data, response } = await createApiClient().GET("/api/v1/orders/{orderId}", {
    params: { path: { orderId } },
  });
  return dataOrThrow(response, data);
}

/**
 * Executes only commands explicitly returned by backend `allowedActions`.
 * The caller owns one stable intent key for actions whose current generated contract accepts Idempotency-Key.
 */
export async function orderCommand(
  orderId: string,
  action: OrderAction,
  intentKey: string,
  input: OrderActionInput = {},
): Promise<void> {
  const client = createApiClient();
  const path = { orderId };

  switch (action) {
    case "CONFIRM": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/confirm", {
        params: { path, header: { "Idempotency-Key": intentKey } },
      });
      return ensureOk(response);
    }
    case "REJECT": {
      if (!input.reasonCode) throw new Error("REASON_REQUIRED");
      const { response } = await client.POST("/api/v1/orders/{orderId}/reject", {
        params: { path, header: { "Idempotency-Key": intentKey } },
        body: { reasonCode: input.reasonCode, note: input.note },
      });
      return ensureOk(response);
    }
    case "CANCEL": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/cancel", {
        params: { path, header: { "Idempotency-Key": intentKey } },
        body: input.reasonCode || input.note ? { reasonCode: input.reasonCode, note: input.note } : undefined,
      });
      return ensureOk(response);
    }
    case "REQUEST_CANCELLATION": {
      if (!input.reasonCode) throw new Error("REASON_REQUIRED");
      const { response } = await client.POST("/api/v1/orders/{orderId}/request-cancellation", {
        params: { path },
        body: { reasonCode: input.reasonCode, note: input.note },
      });
      return ensureOk(response);
    }
    case "ACCEPT_CANCELLATION": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/accept-cancellation", {
        params: { path },
        body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response);
    }
    case "REJECT_CANCELLATION": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/reject-cancellation", {
        params: { path },
        body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response);
    }
    case "MARK_OUT_FOR_DELIVERY": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/mark-out-for-delivery", { params: { path } });
      return ensureOk(response);
    }
    case "MARK_DELIVERED": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/mark-delivered", { params: { path } });
      return ensureOk(response);
    }
    case "MARK_READY_FOR_PICKUP": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/mark-ready-for-pickup", { params: { path } });
      return ensureOk(response);
    }
    case "MARK_SERVICE_PERFORMED": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/mark-service-performed", { params: { path } });
      return ensureOk(response);
    }
    case "CONFIRM_RECEIVED": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/confirm-received", { params: { path } });
      return ensureOk(response);
    }
    case "CONFIRM_COMPLETED": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/confirm-completed", { params: { path } });
      return ensureOk(response);
    }
    case "REPORT_DELIVERY_ISSUE": {
      if (!input.note?.trim()) throw new Error("REASON_REQUIRED");
      const { response } = await client.POST("/api/v1/orders/{orderId}/report-delivery-issue", {
        params: { path },
        body: { reasonText: input.note.trim() },
      });
      return ensureOk(response);
    }
    case "REPORT_SERVICE_ISSUE": {
      if (!input.note?.trim()) throw new Error("REASON_REQUIRED");
      const { response } = await client.POST("/api/v1/orders/{orderId}/report-service-issue", {
        params: { path },
        body: { reasonText: input.note.trim() },
      });
      return ensureOk(response);
    }
    case "REPORT_BUYER_NO_SHOW": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/report-buyer-no-show", { params: { path } });
      return ensureOk(response);
    }
    case "CONTEST_NO_SHOW": {
      const { response } = await client.POST("/api/v1/orders/{orderId}/contest-no-show", {
        params: { path },
        body: input.note ? { note: input.note } : undefined,
      });
      return ensureOk(response);
    }
  }
}
