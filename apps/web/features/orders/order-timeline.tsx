import type { components } from "@covie/api-client";
import { formatDateTime } from "@/lib/format/time";
import { OrderStatusLabel } from "./order-status";

type TimelineEvent = components["schemas"]["OrderTimelineEventResponse"];

const eventCopy: Partial<Record<NonNullable<TimelineEvent["eventType"]>, string>> = {
  ORDER_CREATED: "Đơn được tạo",
  SELLER_CONFIRMED: "Người bán xác nhận",
  SELLER_REJECTED: "Người bán từ chối",
  BUYER_CANCELLED_BEFORE_CONFIRMATION: "Người mua hủy trước xác nhận",
  BUYER_REQUESTED_CANCELLATION: "Người mua yêu cầu hủy",
  SELLER_ACCEPTED_CANCELLATION: "Người bán chấp nhận hủy",
  SELLER_REJECTED_CANCELLATION: "Người bán từ chối hủy",
  SELLER_CANCELLED_AFTER_CONFIRMATION: "Người bán hủy sau xác nhận",
  SELLER_MARKED_OUT_FOR_DELIVERY: "Bắt đầu giao hàng",
  SELLER_MARKED_DELIVERED: "Người bán đánh dấu đã giao",
  SELLER_MARKED_READY_FOR_PICKUP: "Sẵn sàng để nhận",
  SELLER_MARKED_BUYER_NO_SHOW: "Người bán báo người mua không đến",
  BUYER_CONTESTED_NO_SHOW: "Người mua phản hồi no-show",
  PROVIDER_MARKED_PERFORMED: "Dịch vụ đã được thực hiện",
  BUYER_CONFIRMED_RECEIVED: "Người mua xác nhận đã nhận",
  BUYER_CONFIRMED_SERVICE_COMPLETED: "Người mua xác nhận dịch vụ hoàn tất",
  BUYER_REPORTED_NOT_RECEIVED: "Người mua báo chưa nhận hàng",
  BUYER_REPORTED_SERVICE_NOT_COMPLETED: "Người mua báo dịch vụ chưa hoàn tất",
  ORDER_EXPIRED: "Đơn hết hạn",
  ORDER_AUTO_COMPLETED: "Hệ thống tự hoàn tất đơn",
  ORDER_CLOSED_WITH_ISSUE: "Đơn đóng với sự cố",
};

export function OrderTimeline({
  events = [],
  status,
  createdAt,
  confirmedAt,
  completedAt,
  closedAt,
}: {
  events?: TimelineEvent[];
  status?: string;
  createdAt?: string;
  confirmedAt?: string;
  completedAt?: string;
  closedAt?: string;
}) {
  return (
    <section aria-labelledby="order-timeline-title">
      <h2 id="order-timeline-title">Tiến trình</h2>
      <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
        {events.length ? events.map((event, index) => (
          <li key={event.id ?? `${event.eventType ?? "EVENT"}-${event.createdAt ?? index}`}>
            <strong>{event.eventType ? eventCopy[event.eventType] ?? "Đơn hàng được cập nhật" : "Cập nhật đơn"}</strong>
            {event.createdAt ? <time dateTime={event.createdAt}>{formatDateTime(event.createdAt)}</time> : null}
            {event.reasonCode ? <small>Lý do: {event.reasonCode}</small> : null}
            {event.note ? <p>{event.note}</p> : null}
          </li>
        )) : (
          <>
            {createdAt ? <li>Đặt đơn: {formatDateTime(createdAt)}</li> : null}
            {confirmedAt ? <li>Xác nhận: {formatDateTime(confirmedAt)}</li> : null}
            {completedAt ? <li>Hoàn thành: {formatDateTime(completedAt)}</li> : null}
            {closedAt && !completedAt ? <li>Đóng đơn: {formatDateTime(closedAt)}</li> : null}
          </>
        )}
        <li className="order-timeline__current"><span>Hiện tại: </span><OrderStatusLabel status={status} /></li>
      </ol>
    </section>
  );
}
