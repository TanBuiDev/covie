import { CheckCircle, Clock, SealCheck, Storefront, Truck, XCircle } from "@phosphor-icons/react";
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

export function OrderStepper({
  status,
  createdAt,
  confirmedAt,
  completedAt,
  closedAt,
}: {
  status?: string;
  createdAt?: string;
  confirmedAt?: string;
  completedAt?: string;
  closedAt?: string;
}) {
  const isCancelled =
    status?.startsWith("CANCEL") ||
    status === "REJECTED_BY_SELLER" ||
    status === "EXPIRED" ||
    status === "CLOSED_WITH_ISSUE";

  const isConfirmed =
    !!confirmedAt ||
    [
      "CONFIRMED",
      "OUT_FOR_DELIVERY",
      "READY_FOR_PICKUP",
      "AWAITING_BUYER_CONFIRMATION",
      "COMPLETED",
    ].includes(status ?? "");

  const isFulfilling =
    [
      "OUT_FOR_DELIVERY",
      "READY_FOR_PICKUP",
      "AWAITING_BUYER_CONFIRMATION",
      "COMPLETED",
    ].includes(status ?? "");

  const isCompleted = !!completedAt || status === "COMPLETED";

  if (isCancelled) {
    return (
      <div className="order-stepper-tracker order-stepper-tracker--cancelled" aria-label="Tiến trình đơn đã đóng">
        <div className="order-stepper-step order-stepper-step--done">
          <div className="order-stepper-icon"><CheckCircle size={20} weight="fill" /></div>
          <div className="order-stepper-meta">
            <strong>Đã tạo đơn</strong>
            {createdAt ? <small>{formatDateTime(createdAt)}</small> : null}
          </div>
        </div>
        <div className="order-stepper-line order-stepper-line--cancelled" />
        <div className="order-stepper-step order-stepper-step--cancelled">
          <div className="order-stepper-icon"><XCircle size={20} weight="fill" /></div>
          <div className="order-stepper-meta">
            <strong>Đã hủy / Đóng</strong>
            {closedAt ? <small>{formatDateTime(closedAt)}</small> : null}
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="order-stepper-tracker" aria-label="Các mốc tiến trình đơn hàng">
      <div className="order-stepper-step order-stepper-step--done">
        <div className="order-stepper-icon"><CheckCircle size={18} weight="fill" /></div>
        <div className="order-stepper-meta">
          <strong>Đặt đơn</strong>
          {createdAt ? <small>{formatDateTime(createdAt)}</small> : null}
        </div>
      </div>

      <div className={`order-stepper-line ${isConfirmed ? "order-stepper-line--done" : ""}`} />

      <div className={`order-stepper-step ${isConfirmed ? "order-stepper-step--done" : "order-stepper-step--active"}`}>
        <div className="order-stepper-icon">
          {isConfirmed ? <CheckCircle size={18} weight="fill" /> : <Storefront size={18} weight="duotone" />}
        </div>
        <div className="order-stepper-meta">
          <strong>Xác nhận</strong>
          {confirmedAt ? <small>{formatDateTime(confirmedAt)}</small> : null}
        </div>
      </div>

      <div className={`order-stepper-line ${isFulfilling ? "order-stepper-line--done" : ""}`} />

      <div className={`order-stepper-step ${isCompleted ? "order-stepper-step--done" : isFulfilling ? "order-stepper-step--active" : "order-stepper-step--todo"}`}>
        <div className="order-stepper-icon">
          {isCompleted ? <CheckCircle size={18} weight="fill" /> : <Truck size={18} weight="duotone" />}
        </div>
        <div className="order-stepper-meta">
          <strong>Giao hàng</strong>
          {isFulfilling && !isCompleted ? <small>Đang xử lý</small> : null}
        </div>
      </div>

      <div className={`order-stepper-line ${isCompleted ? "order-stepper-line--done" : ""}`} />

      <div className={`order-stepper-step ${isCompleted ? "order-stepper-step--done" : "order-stepper-step--todo"}`}>
        <div className="order-stepper-icon">
          {isCompleted ? <SealCheck size={18} weight="fill" /> : <Clock size={18} weight="duotone" />}
        </div>
        <div className="order-stepper-meta">
          <strong>Hoàn tất</strong>
          {completedAt ? <small>{formatDateTime(completedAt)}</small> : null}
        </div>
      </div>
    </div>
  );
}

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
      <OrderStepper
        status={status}
        createdAt={createdAt}
        confirmedAt={confirmedAt}
        completedAt={completedAt}
        closedAt={closedAt}
      />
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
