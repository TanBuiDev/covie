"use client";
/* eslint-disable react-hooks/set-state-in-effect */

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { inbox, markAllRead, markRead, type Notification } from "./notification-api";
import { notificationDeepLink } from "./notification-deep-link";
import { notificationLabel } from "./notification-types";

export function NotificationInbox() {
  const router = useRouter();
  const [items, setItems] = useState<Notification[]>([]);
  const [cursor, setCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<string | null>(null);

  async function load(next?: string) {
    setLoading(true);
    setError(undefined);
    try {
      const page = await inbox(next);
      setItems((old) => next ? [...old, ...(page.items ?? [])] : page.items ?? []);
      setCursor(page.nextCursor);
    } catch {
      setError("Không thể tải thông báo.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => { void load(); }, []);

  async function openNotification(notification: Notification) {
    if (!notification.id || pending) return;
    setPending(notification.id);
    try {
      if (!notification.read) {
        await markRead(notification.id);
        setItems((old) => old.map((item) => item.id === notification.id ? { ...item, read: true } : item));
      }
      const href = notificationDeepLink(notification.data);
      if (href) router.push(href);
    } catch {
      setError("Không thể cập nhật thông báo.");
    } finally {
      setPending(null);
    }
  }

  async function all() {
    if (pending) return;
    setPending("all");
    try {
      await markAllRead();
      setItems((old) => old.map((item) => ({ ...item, read: true })));
    } catch {
      setError("Không thể cập nhật thông báo.");
    } finally {
      setPending(null);
    }
  }

  const unreadCount = items.filter((item) => !item.read).length;

  return (
    <section className="notification-inbox">
      <header className="notification-inbox__header">
        <div>
          <h1>Thông báo</h1>
          <p>{unreadCount ? `${unreadCount} thông báo chưa đọc` : "Bạn đã đọc tất cả thông báo hiện có."}</p>
        </div>
        <button className="button-secondary" type="button" onClick={() => void all()} disabled={!unreadCount || Boolean(pending)}>
          Đánh dấu tất cả đã đọc
        </button>
      </header>

      {error ? <p className="form-alert" role="alert">{error}</p> : null}
      {loading && !items.length ? <p className="notification-inbox__loading">Đang tải thông báo…</p> : null}

      {!loading && !items.length ? (
        <div className="notification-empty">
          <h2>Chưa có thông báo</h2>
          <p>Các cập nhật từ đơn hàng, nhu cầu và tài khoản sẽ xuất hiện tại đây.</p>
        </div>
      ) : null}

      {items.length ? (
        <ol className="notification-list">
          {items.map((notification) => {
            const href = notificationDeepLink(notification.data);
            return (
              <li key={notification.id}>
                <button
                  className={`notification-item${notification.read ? "" : " notification-item--unread"}`}
                  disabled={pending === notification.id}
                  type="button"
                  onClick={() => void openNotification(notification)}
                >
                  <span className="notification-item__indicator" aria-hidden="true" />
                  <span className="notification-item__content">
                    <strong>{notificationLabel(notification.type ?? "")}</strong>
                    <small>{notification.createdAt ? new Date(notification.createdAt).toLocaleString("vi-VN") : ""}</small>
                  </span>
                  <span className="notification-item__action">{href ? "Mở" : notification.read ? "Đã đọc" : "Đánh dấu đã đọc"}</span>
                </button>
              </li>
            );
          })}
        </ol>
      ) : null}

      {cursor ? (
        <button className="button-secondary notification-inbox__more" disabled={loading} type="button" onClick={() => void load(cursor)}>
          {loading ? "Đang tải…" : "Tải thêm"}
        </button>
      ) : null}
    </section>
  );
}
