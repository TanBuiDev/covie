"use client";

import { useCallback, useEffect, useState } from "react";
import { getOrder, getOrderEvents, type OrderDetail, type OrderTimelineEvent } from "./order-api";
import { OrderActions } from "./order-actions";
import { OrderStatusLabel } from "./order-status";
import { OrderTimeline } from "./order-timeline";

export function OrderDetailView({ orderId }: { orderId: string }) {
  const [order, setOrder] = useState<OrderDetail>(); const [events, setEvents] = useState<OrderTimelineEvent[]>([]); const [error, setError] = useState<string>();
  const refresh = useCallback(async () => { try { const [detail, timeline] = await Promise.all([getOrder(orderId), getOrderEvents(orderId)]); setOrder(detail); setEvents(timeline); setError(undefined); } catch (e) { setError(e instanceof Error ? e.message : "Không thể tải đơn hàng."); } }, [orderId]);
  useEffect(() => { queueMicrotask(() => { void refresh(); }); }, [refresh]);
  if (error) return <p role="alert">{error}</p>; if (!order) return <p>Đang tải đơn hàng…</p>;
  return <article className="order-detail"><p className="eyebrow">Đơn {order.orderCode}</p><h1>{order.type === "SERVICE" ? "Dịch vụ cộng đồng" : "Sản phẩm cộng đồng"}</h1><OrderStatusLabel status={order.status} />
    <OrderTimeline status={order.status} createdAt={order.createdAt} confirmedAt={order.confirmedAt} completedAt={order.completedAt} events={events} />
    <OrderActions order={order} onChanged={refresh} />
  </article>;
}
