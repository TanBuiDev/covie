import { OrderStatusLabel } from "./order-status";

export function OrderTimeline({ status, createdAt, confirmedAt, completedAt }: { status: string; createdAt: string; confirmedAt?: string; completedAt?: string }) {
  return <ol className="order-timeline" aria-label="Tiến trình đơn hàng">
    <li>Đặt đơn: {new Date(createdAt).toLocaleString("vi-VN")}</li>
    {confirmedAt && <li>Xác nhận: {new Date(confirmedAt).toLocaleString("vi-VN")}</li>}
    {completedAt && <li>Hoàn thành: {new Date(completedAt).toLocaleString("vi-VN")}</li>}
    <li>Hiện tại: <OrderStatusLabel status={status} /></li>
  </ol>;
}
