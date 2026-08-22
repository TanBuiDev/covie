import { requestBrowserApi } from "@/lib/api/api-client";

export type OrderSummary = { id: string; orderCode: string; type: string; status: string; createdAt: string };
export type OrderDetail = OrderSummary & { allowedActions?: string[]; merchantId?: string; confirmedAt?: string; completedAt?: string; closedAt?: string };
export type OrderTimelineEvent = { id: string; eventType: string; fromStatus?: string; toStatus?: string; note?: string; createdAt: string };

export async function myOrders(): Promise<OrderSummary[]> {
  const response = await requestBrowserApi("GET", "/api/v1/me/orders");
  if (!response.ok) throw new Error("Không thể tải đơn hàng.");
  return ((await response.json()) as { items?: OrderSummary[] }).items ?? [];
}
export async function merchantOrders(merchantId: string): Promise<OrderSummary[]> {
  const response = await requestBrowserApi("GET", `/api/v1/merchants/${merchantId}/orders`);
  if (!response.ok) throw new Error("Không thể tải đơn hàng người bán.");
  return ((await response.json()) as { items?: OrderSummary[] }).items ?? [];
}
export async function getOrder(orderId: string): Promise<OrderDetail> {
  const response = await requestBrowserApi("GET", `/api/v1/orders/${orderId}`);
  if (!response.ok) throw new Error("Không thể tải chi tiết đơn hàng.");
  return response.json() as Promise<OrderDetail>;
}
export async function getOrderEvents(orderId: string): Promise<OrderTimelineEvent[]> {
  const response = await requestBrowserApi("GET", `/api/v1/orders/${orderId}/events`);
  if (!response.ok) throw new Error("Không thể tải lịch sử đơn hàng.");
  return response.json() as Promise<OrderTimelineEvent[]>;
}
export async function orderCommand(orderId: string, command: string, body?: unknown): Promise<Response> {
  return requestBrowserApi("POST", `/api/v1/orders/${orderId}/${command}`, body, {
    "Idempotency-Key": crypto.randomUUID(),
  });
}
