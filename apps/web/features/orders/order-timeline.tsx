import { OrderStatusLabel } from "./order-status";
import type { OrderTimelineEvent } from "./order-api";

export function OrderTimeline({ status, createdAt, confirmedAt, completedAt, events = [] }: { status: string; createdAt: string; confirmedAt?: string; completedAt?: string; events?: OrderTimelineEvent[] }) {
  return <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
    <li>Đặt đơn: {new Date(createdAt).toLocaleString("vi-VN")}</li>
    {confirmedAt && <li>Xác nhận: {new Date(confirmedAt).toLocaleString("vi-VN")}</li>}
    {completedAt && <li>Hoàn thành: {new Date(completedAt).toLocaleString("vi-VN")}</li>}
    {events.map((event) => <li key={event.id}>{event.note || event.eventType}: {new Date(event.createdAt).toLocaleString("vi-VN")}</li>)}
    <li>Hiện tại: <OrderStatusLabel status={status} /></li>
  </ol>;
}
