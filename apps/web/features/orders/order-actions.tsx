"use client";

import { useState } from "react";
import { orderCommand, type OrderDetail } from "./order-api";

const actionRoutes: Record<string, { route: string; label: string }> = {
  CONFIRM: { route: "confirm", label: "Xác nhận đơn" }, REJECT: { route: "reject", label: "Từ chối" },
  CANCEL: { route: "cancel", label: "Hủy đơn" }, REQUEST_CANCELLATION: { route: "request-cancellation", label: "Yêu cầu hủy" },
  ACCEPT_CANCELLATION: { route: "accept-cancellation", label: "Chấp nhận hủy" }, REJECT_CANCELLATION: { route: "reject-cancellation", label: "Từ chối hủy" },
  MARK_OUT_FOR_DELIVERY: { route: "mark-out-for-delivery", label: "Bắt đầu giao" }, MARK_DELIVERED: { route: "mark-delivered", label: "Đã giao" },
  MARK_READY_FOR_PICKUP: { route: "mark-ready-for-pickup", label: "Sẵn sàng nhận" }, MARK_SERVICE_PERFORMED: { route: "mark-service-performed", label: "Đã thực hiện" },
  CONFIRM_RECEIVED: { route: "confirm-received", label: "Đã nhận hàng" }, CONFIRM_COMPLETED: { route: "confirm-completed", label: "Xác nhận hoàn thành" },
  REPORT_DELIVERY_ISSUE: { route: "report-delivery-issue", label: "Báo sự cố" }, REPORT_SERVICE_ISSUE: { route: "report-service-issue", label: "Báo sự cố" },
  REPORT_BUYER_NO_SHOW: { route: "report-buyer-no-show", label: "Báo không đến" }, CONTEST_NO_SHOW: { route: "contest-no-show", label: "Phản hồi" },
};

export function OrderActions({ order, onChanged }: { order: OrderDetail; onChanged: () => Promise<void> }) {
  const [pending, setPending] = useState<string>(); const [message, setMessage] = useState<string>();
  async function act(action: string) {
    const item = actionRoutes[action]; if (!item) return; setPending(action); setMessage(undefined);
    try {
      const body = action === "REJECT" ? { reasonCode: "SELLER_UNAVAILABLE", note: "" }
        : action === "CANCEL" ? { reasonCode: "USER_CANCELLED", note: "" }
        : action === "REQUEST_CANCELLATION" ? { reasonCode: "BUYER_REQUESTED", note: "" }
        : action.includes("ISSUE") ? { reasonText: "Cần trao đổi thêm" }
        : action === "CONTEST_NO_SHOW" ? { note: "Tôi cần trao đổi thêm." } : undefined;
      const response = await orderCommand(order.id, item.route, body);
      if (!response.ok) throw new Error("Đơn hàng vừa thay đổi.");
      await onChanged();
    } catch (error) { await onChanged().catch(() => undefined); setMessage(error instanceof Error ? error.message : "Không thể cập nhật đơn."); }
    finally { setPending(undefined); }
  }
  return <section className="order-actions" aria-label="Thao tác đơn hàng">{message && <p role="status">{message}</p>}
    {(order.allowedActions ?? []).map((action) => actionRoutes[action] && <button key={action} type="button" disabled={!!pending} onClick={() => act(action)}>{pending === action ? "Đang xử lý…" : actionRoutes[action].label}</button>)}
  </section>;
}
