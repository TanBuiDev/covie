import type { components } from "@covie/api-client";
import { formatDateTime } from "@/lib/format/time";
import { OrderStatusLabel } from "./order-status";

type TimelineEvent = components["schemas"]["OrderTimelineEventResponse"];

const eventCopy: Record<string, string> = {
  ORDER_CREATED: "Đơn hàng đã được tạo",
  SELLER_CONFIRMED: "Người bán đã xác nhận",
  SELLER_REJECTED: "Người bán đã từ chối",
  BUYER_CANCELLED_BEFORE_CONFIRMATION: "Bạn đã hủy đơn",
  BUYER_REQUESTED_CANCELLATION: "Bạn đã gửi yêu cầu hủy",
  SELLER_ACCEPTED_CANCELLATION: "Người bán đã chấp nhận hủy",
  SELLER_REJECTED_CANCELLATION: "Người bán đã từ chối yêu cầu hủy",
  SELLER_CANCELLED_AFTER_CONFIRMATION: "Người bán đã hủy đơn",
  SELLER_MARKED_OUT_FOR_DELIVERY: "Đơn đang được giao",
  SELLER_MARKED_DELIVERED: "Người bán đã đánh dấu đã giao",
  SELLER_MARKED_READY_FOR_PICKUP: "Đơn đã sẵn sàng để nhận",
  SELLER_MARKED_BUYER_NO_SHOW: "Người bán đã báo người mua không đến",
  BUYER_CONTESTED_NO_SHOW: "Bạn đã phản hồi báo không đến",
  PROVIDER_MARKED_PERFORMED: "Người cung cấp đã thực hiện dịch vụ",
  BUYER_CONFIRMED_RECEIVED: "Bạn đã xác nhận nhận hàng",
  BUYER_CONFIRMED_SERVICE_COMPLETED: "Bạn đã xác nhận dịch vụ hoàn thành",
  BUYER_REPORTED_NOT_RECEIVED: "Bạn đã báo chưa nhận được hàng",
  BUYER_REPORTED_SERVICE_NOT_COMPLETED: "Bạn đã báo dịch vụ chưa hoàn thành",
  ORDER_EXPIRED: "Đơn đã hết hạn",
  ORDER_AUTO_COMPLETED: "Đơn đã được tự động hoàn tất",
  ORDER_CLOSED_WITH_ISSUE: "Đơn đã đóng với vấn đề",
};

function eventLabel(event: TimelineEvent): string {
  return event.eventType ? (eventCopy[event.eventType] ?? event.eventType) : "Cập nhật đơn hàng";
}

export function OrderTimeline({
  status,
  events,
  createdAt,
  confirmedAt,
  completedAt,
  closedAt,
}: {
  status?: string;
  events?: TimelineEvent[];
  createdAt?: string;
  confirmedAt?: string;
  completedAt?: string;
  closedAt?: string;
}) {
  if (events?.length) {
    return (
      <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
        {events.map((event, index) => (
          <li key={event.id ?? `${event.eventType ?? "event"}-${event.createdAt ?? index}`}>
            <strong>{eventLabel(event)}</strong>
            {event.createdAt ? <time dateTime={event.createdAt}>{formatDateTime(event.createdAt)}</time> : null}
            {event.note ? <p>{event.note}</p> : null}
          </li>
        ))}
        <li className="order-timeline__current"><span>Hiện tại</span><OrderStatusLabel status={status} /></li>
      </ol>
    );
  }

  const fallback = [
    createdAt ? { label: "Đơn hàng đã được tạo", date: createdAt } : null,
    confirmedAt ? { label: "Người bán đã xác nhận", date: confirmedAt } : null,
    completedAt ? { label: "Đơn đã hoàn thành", date: completedAt } : null,
    closedAt && !completedAt ? { label: "Đơn đã đóng", date: closedAt } : null,
  ].filter((entry): entry is { label: string; date: string } => Boolean(entry));

  return (
    <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
      {fallback.map((entry) => (
        <li key={`${entry.label}-${entry.date}`}>
          <strong>{entry.label}</strong>
          <time dateTime={entry.date}>{formatDateTime(entry.date)}</time>
        </li>
      ))}
      <li className="order-timeline__current"><span>Hiện tại</span><OrderStatusLabel status={status} /></li>
    </ol>
  );
}
