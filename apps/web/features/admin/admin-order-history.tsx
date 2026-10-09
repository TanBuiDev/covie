"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  ArrowLeft,
  ArrowsClockwise,
  Clock,
  Package,
  ShieldCheck,
  User,
} from "@phosphor-icons/react";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  formatAdminMoney,
  presentOrderStatus,
} from "./admin-ui";

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

  const load = useCallback(async (isActive: () => boolean = () => true) => {
    try {
      const [eventsResponse, orderResponse] = await Promise.all([
        requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(orderId)}/events`),
        requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(orderId)}`),
      ]);
      if (!eventsResponse.ok) throw await readApiError(eventsResponse);
      if (!orderResponse.ok) throw await readApiError(orderResponse);
      const [nextEvents, nextOrder] = await Promise.all([
        eventsResponse.json() as Promise<EventRow[]>,
        orderResponse.json() as Promise<OrderDetail>,
      ]);
      if (!isActive()) return;
      setEvents(nextEvents);
      setOrder(nextOrder);
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải lịch sử đơn hàng");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, [orderId]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(() => active); });
    return () => { active = false; };
  }, [load]);

  function reload() {
    setLoading(true);
    setError(undefined);
    void load();
  }

  const status = presentOrderStatus(order?.status);

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title={`Lịch sử đơn ${order?.orderCode ?? orderId}`}
        subtitle="CoVie Admin · Tra cứu tiến trình và dòng sự kiện đơn hàng"
        action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />}
      />

      <Link
        className="admin-inline-link"
        href="/admin/orders"
        style={{ display: "inline-flex", alignItems: "center", gap: "6px", width: "fit-content", fontWeight: "600", color: "#047857" }}
      >
        <ArrowLeft size={16} weight="bold" />
        <span>Quay lại tra cứu đơn hàng</span>
      </Link>

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải lịch sử…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
          <button className="admin-button admin-button--secondary" onClick={reload} style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
            <ArrowsClockwise size={16} weight="bold" />
            <span>Tải lại</span>
          </button>
        </AdminSurface>
      ) : null}

      {!loading && !error ? (
        <>
          <div className="admin-history-grid">
            <div className="admin-order-timeline">
              {events.length ? (
                events.map((event, index) => {
                  const copy = presentEvent(event.type);
                  return (
                    <article className="admin-order-timeline__event" key={event.id ?? index}>
                      <span className="admin-order-timeline__marker" aria-hidden="true" />
                      <div>
                        <strong>{copy[0]}</strong>
                        <small style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                          <Clock size={12} weight="bold" />
                          <span>{formatAdminDate(event.createdAt)}</span>
                        </small>
                        <p>{copy[1]}</p>
                        <p className="admin-caption">
                          {actorLabel(event.actorContext)}
                          {event.actorUserId ? ` · ${event.actorUserId}` : ""}
                          {event.reasonCode ? ` · Lý do: ${event.reasonCode}` : ""}
                        </p>
                      </div>
                    </article>
                  );
                })
              ) : (
                <AdminSurface>
                  <p className="admin-muted">Chưa có sự kiện lịch sử được API trả về.</p>
                </AdminSurface>
              )}
            </div>

            <AdminSurface className="admin-history-meta">
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <Package size={20} weight="duotone" style={{ color: "#047857" }} />
                <h2 style={{ margin: 0 }}>Thông tin tham chiếu</h2>
              </div>
              <dl>
                <div>
                  <dt>Người mua</dt>
                  <dd style={{ fontFamily: "monospace" }}>{order?.buyer?.userId ?? "—"}</dd>
                </div>
                <div>
                  <dt>Người bán</dt>
                  <dd style={{ fontFamily: "monospace" }}>{order?.merchant?.merchantId ?? "—"}</dd>
                </div>
                <div>
                  <dt>Điểm bán</dt>
                  <dd style={{ fontFamily: "monospace" }}>{order?.merchant?.locationId ?? "—"}</dd>
                </div>
                <div>
                  <dt>Trạng thái</dt>
                  <dd>
                    <AdminStatusChip label={status.label} tone={status.tone} />
                  </dd>
                </div>
                <div>
                  <dt>Tổng theo đơn</dt>
                  <dd style={{ color: "#047857", fontWeight: "700" }}>
                    {formatAdminMoney(order?.pricing?.totalAmount ?? order?.pricing?.knownTotal, order?.pricing?.currency)}
                  </dd>
                </div>
              </dl>
              <div style={{ display: "flex", alignItems: "center", gap: "6px", borderTop: "1px solid #f1f5f9", paddingTop: "8px" }}>
                <ShieldCheck size={16} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
                <strong style={{ fontSize: "12px", color: "#334155" }}>
                  Dòng thời gian ghi lại các sự kiện đã xảy ra và chỉ dùng để tra cứu.
                </strong>
              </div>
            </AdminSurface>
          </div>

          <AdminSurface className="admin-contract-note">
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <ShieldCheck size={18} weight="duotone" style={{ color: "#2563eb" }} />
              <strong>Không có điều khiển trạng thái</strong>
            </div>
            <p>Nếu trạng thái vừa thay đổi, hãy tải lại đơn hàng và dòng thời gian để xem dữ liệu mới nhất.</p>
            <button
              className="admin-button admin-button--secondary"
              onClick={reload}
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <ArrowsClockwise size={16} weight="bold" />
              <span>Tải lại dữ liệu</span>
            </button>
          </AdminSurface>
        </>
      ) : null}
    </div>
  );
}
