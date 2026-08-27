"use client";
/* eslint-disable react-hooks/set-state-in-effect */

import Link from "next/link";
import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { inbox, markAllRead, markRead, type Notification } from "./notification-api";
import { notificationDeepLink } from "./notification-deep-link";
import { notificationLabel } from "./notification-types";
import styles from "./notification-inbox.module.css";

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
    <section className={styles.screen} aria-labelledby="notification-title">
      <div className={styles.appBar}>
        <Link href="/me" aria-label="Quay lại hồ sơ"><CoVieIcon name="back" /></Link>
        <h1 id="notification-title">Thông báo</h1>
      </div>
      <header className={styles.header}>
        <div className={styles.headerText}>
          <h2>Thông báo gần đây</h2>
          <p>{unreadCount ? `${unreadCount} thông báo chưa đọc` : "Bạn đã đọc tất cả thông báo hiện có."}</p>
        </div>
        <button className={styles.markAll} type="button" onClick={() => void all()} disabled={!unreadCount || Boolean(pending)}>
          {pending === "all" ? "Đang cập nhật…" : "Đánh dấu đã đọc"}
        </button>
      </header>

      {error ? <p className={styles.error} role="alert">{error}</p> : null}
      {loading && !items.length ? <p className={styles.loading}>Đang tải thông báo…</p> : null}

      {!loading && !items.length ? (
        <div className={styles.empty}>
          <h2>Chưa có thông báo</h2>
          <p>Các cập nhật từ đơn hàng, nhu cầu và tài khoản sẽ xuất hiện tại đây.</p>
        </div>
      ) : null}

      {items.length ? (
        <ol className={styles.list}>
          {items.map((notification) => {
            const href = notificationDeepLink(notification.data);
            const unread = !notification.read;
            return (
              <li key={notification.id}>
                <button
                  className={`${styles.item}${unread ? ` ${styles.itemUnread}` : ""}`}
                  disabled={pending === notification.id}
                  type="button"
                  onClick={() => void openNotification(notification)}
                  aria-label={`${notificationLabel(notification.type ?? "")}. ${href ? "Mở chi tiết" : unread ? "Đánh dấu đã đọc" : "Đã đọc"}`}
                >
                  <span className={styles.indicator} aria-hidden="true" />
                  <span className={styles.content}>
                    <strong>{notificationLabel(notification.type ?? "")}</strong>
                    <small>{notification.createdAt ? new Date(notification.createdAt).toLocaleString("vi-VN") : ""}</small>
                  </span>
                  <span className={styles.state}>{href ? "Mở" : unread ? "Chưa đọc" : "Đã đọc"}</span>
                </button>
              </li>
            );
          })}
        </ol>
      ) : null}

      {cursor ? (
        <button className={styles.more} disabled={loading} type="button" onClick={() => void load(cursor)}>
          {loading ? "Đang tải…" : "Tải thêm"}
        </button>
      ) : null}
    </section>
  );
}
