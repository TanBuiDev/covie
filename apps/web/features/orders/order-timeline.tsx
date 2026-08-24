import { formatDateTime } from "@/lib/format/time";
import { OrderStatusLabel } from "./order-status";

export function OrderTimeline({
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
  return (
    <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
      {createdAt ? <li>Đặt đơn: {formatDateTime(createdAt)}</li> : null}
      {confirmedAt ? <li>Xác nhận: {formatDateTime(confirmedAt)}</li> : null}
      {completedAt ? <li>Hoàn thành: {formatDateTime(completedAt)}</li> : null}
      {closedAt && !completedAt ? <li>Đóng đơn: {formatDateTime(closedAt)}</li> : null}
      <li>Hiện tại: <OrderStatusLabel status={status} /></li>
    </ol>
  );
}
