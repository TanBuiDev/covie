"use client";

import Link from "next/link";
import { useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { markRead } from "@/features/notifications/notification-api";
import { formatDateTime } from "@/lib/format/time";
import {
  ArrowRight,
  CheckCircle,
  Clock,
  Package,
  ShieldCheck,
  Storefront,
} from "@phosphor-icons/react";

export type SellerModerationNotice = {
  id: string;
  read: boolean;
  createdAt?: string;
  targetType: "MERCHANT" | "CATALOG_ITEM";
};

function noticeTitle(targetType: SellerModerationNotice["targetType"]) {
  return targetType === "MERCHANT"
    ? "Có cập nhật kiểm duyệt cho cửa hàng"
    : "Có cập nhật kiểm duyệt cho nội dung đăng bán";
}

function noticeBody(targetType: SellerModerationNotice["targetType"]) {
  return targetType === "MERCHANT"
    ? "Hệ thống đã cập nhật trạng thái kiểm duyệt của cửa hàng."
    : "Hệ thống đã cập nhật trạng thái kiểm duyệt của một nội dung trong Danh mục.";
}

export function SellerModerationNotices({
  merchantId,
  initialNotices,
}: {
  merchantId: string;
  initialNotices: SellerModerationNotice[];
}) {
  const [notices, setNotices] = useState(initialNotices);
  const [pendingId, setPendingId] = useState<string>();
  const [error, setError] = useState<string>();
  const [statusFilter, setStatusFilter] = useState<"ALL" | "UNREAD" | "READ">("ALL");
  const catalogHref = `/seller/${encodeURIComponent(merchantId)}/catalog`;

  const unreadCount = notices.filter((n) => !n.read).length;
  const readCount = notices.filter((n) => n.read).length;

  const filteredNotices = notices.filter((n) => {
    if (statusFilter === "UNREAD") return !n.read;
    if (statusFilter === "READ") return n.read;
    return true;
  });

  async function handleMarkRead(notificationId: string) {
    if (pendingId) return;
    setPendingId(notificationId);
    setError(undefined);
    try {
      await markRead(notificationId);
      setNotices((current) => current.map((notice) => notice.id === notificationId ? { ...notice, read: true } : notice));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể cập nhật thông báo.");
    } finally {
      setPendingId(undefined);
    }
  }

  return (
    <section className="seller-notices" aria-label="Thông báo kiểm duyệt cho Seller">
      {error ? <p className="seller-notices__error" role="alert">{error}</p> : null}

      {notices.length ? (
        <div className="seller-notice-tabs" role="tablist" aria-label="Lọc thông báo kiểm duyệt" style={{ display: "flex", gap: "6px" }}>
          <button
            type="button"
            role="tab"
            aria-selected={statusFilter === "ALL"}
            className={`seller-order-tab${statusFilter === "ALL" ? " seller-order-tab--active" : ""}`}
            onClick={() => setStatusFilter("ALL")}
          >
            <span>Tất cả</span>
            <span className="seller-order-tab__badge">{notices.length}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={statusFilter === "UNREAD"}
            className={`seller-order-tab${statusFilter === "UNREAD" ? " seller-order-tab--active" : ""}`}
            onClick={() => setStatusFilter("UNREAD")}
          >
            <span>Chưa đọc</span>
            <span className="seller-order-tab__badge">{unreadCount}</span>
          </button>
          <button
            type="button"
            role="tab"
            aria-selected={statusFilter === "READ"}
            className={`seller-order-tab${statusFilter === "READ" ? " seller-order-tab--active" : ""}`}
            onClick={() => setStatusFilter("READ")}
          >
            <span>Đã đọc</span>
            <span className="seller-order-tab__badge">{readCount}</span>
          </button>
        </div>
      ) : null}

      {filteredNotices.length ? (
        <div className="seller-notice-list">
          {filteredNotices.map((notice) => (
            <article className={`seller-notice${notice.read ? "" : " seller-notice--unread"}`} key={notice.id}>
              <span className="seller-notice__visual" aria-hidden="true">
                {notice.targetType === "MERCHANT" ? (
                  <Storefront size={20} weight="duotone" />
                ) : (
                  <Package size={20} weight="duotone" />
                )}
              </span>
              <div className="seller-notice__content">
                <div className="seller-notice__heading">
                  <h2>{noticeTitle(notice.targetType)}</h2>
                  <time dateTime={notice.createdAt} style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                    <Clock size={12} weight="bold" />
                    <span>{notice.createdAt ? formatDateTime(notice.createdAt) : "Thời gian đang được cập nhật"}</span>
                  </time>
                </div>
                <p>{noticeBody(notice.targetType)}</p>
                <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginTop: "4px", flexWrap: "wrap", gap: "8px" }}>
                  {!notice.read ? (
                    <button
                      className="seller-notice__unread"
                      type="button"
                      disabled={pendingId === notice.id}
                      aria-label="Đánh dấu thông báo kiểm duyệt là đã đọc"
                      onClick={() => void handleMarkRead(notice.id)}
                    >
                      <span aria-hidden="true" />
                      {pendingId === notice.id ? "Đang cập nhật…" : "Chưa đọc"}
                    </button>
                  ) : (
                    <span style={{ display: "inline-flex", alignItems: "center", gap: "4px", fontSize: "12px", color: "#047857", fontWeight: "600" }}>
                      <CheckCircle size={14} weight="fill" />
                      <span>Đã đọc</span>
                    </span>
                  )}
                  <Link
                    href={catalogHref}
                    style={{ display: "inline-flex", alignItems: "center", gap: "4px", fontSize: "12px", color: "var(--seller-brand, #047857)", fontWeight: "600", textDecoration: "none" }}
                  >
                    <span>Xem nội dung</span>
                    <ArrowRight size={12} weight="bold" />
                  </Link>
                </div>
              </div>
            </article>
          ))}
        </div>
      ) : notices.length ? (
        <div style={{ background: "#ffffff", border: "1px dashed var(--seller-border, #e2e8f0)", borderRadius: "12px", padding: "28px 16px", textAlign: "center", gridColumn: 1 }}>
          <p style={{ color: "var(--seller-text-secondary, #64748b)", margin: 0, fontSize: "14px" }}>Không có thông báo trong mục đang chọn.</p>
        </div>
      ) : (
        <EmptyState
          title="Chưa có thông báo nội dung"
          description="Các cập nhật kiểm duyệt có thể xác định thuộc cửa hàng này sẽ xuất hiện tại đây."
        />
      )}

      <aside className="seller-trust-note seller-notices__safe-copy">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <ShieldCheck size={18} weight="duotone" style={{ color: "#047857" }} />
          <h2 style={{ margin: 0 }}>Thông báo về nội dung</h2>
        </div>
        <p>Mở Danh mục để xem trạng thái hiện tại hoặc chỉnh sửa khi thao tác đó khả dụng.</p>
        <Link className="seller-button seller-button--secondary" href={catalogHref} style={{ display: "inline-flex", alignItems: "center", gap: "6px", width: "fit-content" }}>
          <span>Mở Danh mục</span>
          <ArrowRight size={14} weight="bold" />
        </Link>
      </aside>
    </section>
  );
}
