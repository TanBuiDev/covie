"use client";

import Link from "next/link";
import { useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { markRead } from "@/features/notifications/notification-api";
import { formatDateTime } from "@/lib/format/time";

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
  const catalogHref = `/seller/${encodeURIComponent(merchantId)}/catalog`;

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
        <div className="seller-notice-list">
          {notices.map((notice) => (
            <article className={`seller-notice${notice.read ? "" : " seller-notice--unread"}`} key={notice.id}>
              <span className="seller-notice__visual" aria-hidden="true" />
              <div className="seller-notice__content">
                <div className="seller-notice__heading">
                  <h2>{noticeTitle(notice.targetType)}</h2>
                  <time dateTime={notice.createdAt}>{notice.createdAt ? formatDateTime(notice.createdAt) : "Thời gian đang được cập nhật"}</time>
                </div>
                <p>{noticeBody(notice.targetType)}</p>
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
                ) : null}
              </div>
            </article>
          ))}
        </div>
      ) : (
        <EmptyState
          title="Chưa có thông báo nội dung"
          description="Các cập nhật kiểm duyệt có thể xác định thuộc cửa hàng này sẽ xuất hiện tại đây."
        />
      )}

      <aside className="seller-trust-note seller-notices__safe-copy">
        <h2>Thông báo về nội dung</h2>
        <p>Mở Danh mục để xem trạng thái hiện tại hoặc chỉnh sửa khi thao tác đó khả dụng.</p>
        <Link className="seller-button seller-button--secondary" href={catalogHref}>Mở Danh mục</Link>
      </aside>
    </section>
  );
}
