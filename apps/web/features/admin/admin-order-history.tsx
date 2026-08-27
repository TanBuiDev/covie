"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, formatAdminMoney, presentOrderStatus } from "./admin-ui";

type EventRow = { id?: string; type?: string; actorUserId?: string; actorContext?: string; fromStatus?: string; toStatus?: string; reasonCode?: string; createdAt?: string };
type OrderDetail = { id?: string; orderCode?: string; status?: string; buyer?: { userId?: string }; merchant?: { merchantId?: string; locationId?: string }; pricing?: { totalAmount?: number; knownTotal?: number; currency?: string } };

const eventCopy: Record<string, [string, string]> = {
  ORDER_CREATED: ["Đơn hàng đã được tạo", "Đang chờ người bán xác nhận."],
  SELLER_CONFIRMED: ["Người bán đã xác nhận", "Đơn hàng đã được người bán xác nhận."],
  SELLER_REJECTED: ["Người bán đã từ chối", "Đơn hàng đã kết thúc theo phản hồi của người bán."],
  BUYER_CANCELLED_BEFORE_CONFIRMATION: ["Người mua đã huỷ đơn", "Đơn được huỷ trước khi người bán xác nhận."],
  BUYER_REQUESTED_CANCELLATION: ["Người mua yêu cầu huỷ", "Yêu cầu huỷ đang chờ phản hồi phù hợp."],
  SELLER_ACCEPTED_CANCELLATION: ["Người bán chấp nhận huỷ", "Yêu cầu huỷ đã được chấp nhận."],
  SELLER_REJECTED_CANCELLATION: ["Người bán từ chối yêu cầu huỷ", "Đơn tiếp tục theo trạng thái hệ thống."],
  SELLER_CANCELLED_AFTER_CONFIRMATION: ["Người bán đã huỷ đơn", "Đơn bị huỷ sau khi đã xác nhận."],
  SELLER_MARKED_OUT_FOR_DELIVERY: ["Đơn đang được giao", "Theo dõi cập nhật từ người bán."],
  SELLER_MARKED_DELIVERED: ["Người bán đánh dấu đã giao", "Đang chờ bước xác nhận tiếp theo nếu có."],
  SELLER_MARKED_READY_FOR_PICKUP: ["Đơn sẵn sàng để nhận", "Người mua có thể nhận tại điểm bán theo thông tin đơn."],
  SELLER_MARKED_BUYER_NO_SHOW: ["Người bán báo người mua không đến", "Sự kiện vắng mặt đã được ghi nhận."],
  BUYER_CONTESTED_NO_SHOW: ["Người mua phản hồi vắng mặt", "Trạng thái tranh chấp vắng mặt đã được ghi nhận."],
  PROVIDER_MARKED_PERFORMED: ["Nhà cung cấp đã hoàn thành dịch vụ", "Đang chờ xác nhận hoàn tất nếu cần."],
  BUYER_CONFIRMED_RECEIVED: ["Người mua xác nhận đã nhận", "Đơn hàng đã nhận được xác nhận từ người mua."],
  BUYER_CONFIRMED_SERVICE_COMPLETED: ["Người mua xác nhận dịch vụ hoàn tất", "Dịch vụ đã được xác nhận hoàn thành."],
  BUYER_REPORTED_NOT_RECEIVED: ["Người mua báo chưa nhận hàng", "Vấn đề giao hàng đã được ghi nhận."],
  BUYER_REPORTED_SERVICE_NOT_COMPLETED: ["Người mua báo dịch vụ chưa hoàn tất", "Vấn đề dịch vụ đã được ghi nhận."],
  ORDER_EXPIRED: ["Đơn hàng đã hết hạn", "Hệ thống đóng đơn theo thời hạn cấu hình."],
  ORDER_AUTO_COMPLETED: ["Đơn được tự động hoàn tất", "Hệ thống hoàn tất theo chính sách thời hạn."],
  ORDER_CLOSED_WITH_ISSUE: ["Đơn đóng với vấn đề", "Đơn đã được đóng nhưng vẫn ghi nhận ngữ cảnh vấn đề."],
};

function presentEvent(type: unknown) {
  if (typeof type === "string" && eventCopy[type]) return eventCopy[type];
  return ["Sự kiện đơn hàng đã được cập nhật", "Chi tiết sự kiện nội bộ không được hiển thị."] as [string, string];
}

function actorLabel(context: unknown) {
  switch (context) {
    case "BUYER": return "Người mua";
    case "MERCHANT": return "Người bán / Nhà cung cấp";
    case "SYSTEM": return "Hệ thống";
    case "ADMIN": return "Quản trị viên";
    default: return "Nguồn sự kiện";
  }
}

export function AdminOrderHistory({ orderId }: { orderId: string }) {
  const [events, setEvents] = useState<EventRow[]>([]);
  const [order, setOrder] = useState<OrderDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const [eventsResponse, orderResponse] = await Promise.all([
        requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(orderId)}/events`),
        requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(orderId)}`),
      ]);
      if (!eventsResponse.ok) throw await readApiError(eventsResponse);
      if (!orderResponse.ok) throw await readApiError(orderResponse);
      setEvents(await eventsResponse.json() as EventRow[]);
      setOrder(await orderResponse.json() as OrderDetail);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải lịch sử đơn hàng");
    } finally { setLoading(false); }
  }, [orderId]);

  useEffect(() => { void load(); }, [load]);
  const status = presentOrderStatus(order?.status);

  return <div className="admin-page-stack">
    <AdminPageHeader title={`Lịch sử đơn ${order?.orderCode ?? orderId}`} subtitle="CoVie Admin · Tra cứu đơn hàng" action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <Link className="admin-inline-link" href="/admin/orders">← Quay lại tra cứu đơn hàng</Link>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải lịch sử…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Tải lại</button></AdminSurface> : null}
    {!loading && !error ? <>
      <div className="admin-history-grid">
        <div className="admin-order-timeline">{events.length ? events.map((event, index) => { const copy = presentEvent(event.type); return <article className="admin-order-timeline__event" key={event.id ?? index}><span className="admin-order-timeline__marker" aria-hidden="true" /><div><strong>{copy[0]}</strong><small>{formatAdminDate(event.createdAt)}</small><p>{copy[1]}</p><p className="admin-caption">{actorLabel(event.actorContext)}{event.actorUserId ? ` · ${event.actorUserId}` : ""}{event.reasonCode ? ` · Lý do: ${event.reasonCode}` : ""}</p></div></article>; }) : <AdminSurface><p className="admin-muted">Chưa có sự kiện lịch sử được API trả về.</p></AdminSurface>}</div>
        <AdminSurface className="admin-history-meta"><h2>Thông tin tham chiếu</h2><dl><div><dt>Người mua</dt><dd>{order?.buyer?.userId ?? "—"}</dd></div><div><dt>Người bán</dt><dd>{order?.merchant?.merchantId ?? "—"}</dd></div><div><dt>Điểm bán</dt><dd>{order?.merchant?.locationId ?? "—"}</dd></div><div><dt>Trạng thái</dt><dd><AdminStatusChip label={status.label} tone={status.tone} /></dd></div><div><dt>Tổng theo đơn</dt><dd>{formatAdminMoney(order?.pricing?.totalAmount ?? order?.pricing?.knownTotal, order?.pricing?.currency)}</dd></div></dl><strong>Dòng thời gian ghi lại các sự kiện đã xảy ra và chỉ dùng để tra cứu.</strong></AdminSurface>
      </div>
      <AdminSurface className="admin-contract-note"><strong>Không có điều khiển trạng thái</strong><p>Nếu trạng thái vừa thay đổi, hãy tải lại đơn hàng và dòng thời gian để xem dữ liệu mới nhất.</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Tải lại dữ liệu</button></AdminSurface>
    </> : null}
  </div>;
}
