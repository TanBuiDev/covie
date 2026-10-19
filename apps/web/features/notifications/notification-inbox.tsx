"use client";
/* eslint-disable react-hooks/set-state-in-effect */

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { CovieApiError } from "@/lib/api/api-error";
import { ErrorState } from "@/components/ui/error-state";
import {
  ArrowRight,
  Bell,
  BellSimpleSlash,
  CheckCircle,
  Package,
  Sparkle,
  Star,
} from "@phosphor-icons/react";
import { inbox, markAllRead, markRead, type Notification } from "./notification-api";
import { notificationDeepLink } from "./notification-deep-link";
import { notificationBillingSummary, notificationLabel } from "./notification-types";
import styles from "./notification-inbox.module.css";

function getNotificationIcon(type?: string) {
  if (!type) return { icon: Bell, color: "blue" };
  if (type.startsWith("ORDER_")) return { icon: Package, color: "emerald" };
  if (type.startsWith("NEED_")) return { icon: Sparkle, color: "amber" };
  if (type.startsWith("REVIEW_")) return { icon: Star, color: "purple" };
  return { icon: Bell, color: "blue" };
}

export function NotificationInbox() {
  const router = useRouter();
  const [items, setItems] = useState<Notification[]>([]);
  const [cursor, setCursor] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [pending, setPending] = useState<string | null>(null);
  const [failedCursor, setFailedCursor] = useState<string>();
  const [filter, setFilter] = useState<"ALL" | "UNREAD">("ALL");
  const loadPending = useRef(false);
  const mutationPending = useRef(false);
  const [authRequired, setAuthRequired] = useState(false);
  const authExpired = useRef(false);

  async function load(next?: string) {
    if (loadPending.current || mutationPending.current || authExpired.current) return;
    loadPending.current = true;
    setLoading(true);
    setError(undefined);
    try {
      const page = await inbox(next);
      setItems((old) => {
        const candidates = next ? [...old, ...(page.items ?? [])] : page.items ?? [];
        const ids = new Set<string>();
        return candidates.filter((item) => {
          if (!item.id || ids.has(item.id)) return false;
          ids.add(item.id);
          return true;
        });
      });
      setCursor(page.nextCursor);
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) {
        authExpired.current = true;
        setAuthRequired(true);
      }
      setFailedCursor(next);
      setError("Không thể tải thông báo.");
    } finally {
      loadPending.current = false;
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  async function openNotification(notification: Notification) {
    if (!notification.id || mutationPending.current || loadPending.current || authRequired) return;
    mutationPending.current = true;
    setPending(notification.id);
    try {
      if (!notification.read) {
        await markRead(notification.id);
        setItems((old) => old.map((item) => (item.id === notification.id ? { ...item, read: true } : item)));
      }
      const href = notificationDeepLink(notification.data);
      if (href) router.push(href);
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) {
        authExpired.current = true;
        setAuthRequired(true);
      }
      setError("Không thể cập nhật thông báo.");
    } finally {
      mutationPending.current = false;
      setPending(null);
    }
  }

  async function all() {
    if (mutationPending.current || loadPending.current || authRequired) return;
    mutationPending.current = true;
    setPending("all");
    try {
      await markAllRead();
      setItems((old) => old.map((item) => ({ ...item, read: true })));
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) {
        authExpired.current = true;
        setAuthRequired(true);
      }
      setError("Không thể cập nhật thông báo.");
    } finally {
      mutationPending.current = false;
      setPending(null);
    }
  }

  const unreadCount = items.filter((item) => !item.read).length;

  if (authRequired)
    return (
      <ErrorState
        title="Vui lòng đăng nhập lại"
        description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
        action={
          <Link className="button-primary" href="/login?returnTo=%2Fme%2Fnotifications">
            Đăng nhập lại
          </Link>
        }
      />
    );

  const displayedItems = filter === "UNREAD" ? items.filter((item) => !item.read) : items;

  return (
    <section className={styles.screen} aria-labelledby="notification-title">
      <div className={styles.appBar}>
        <Link href="/me" aria-label="Quay lại hồ sơ">
          <CoVieIcon name="back" />
        </Link>
        <h1 id="notification-title">Thông báo</h1>
      </div>

      <header className={styles.header}>
        <div className={styles.headerText}>
          <h2>Thông báo gần đây</h2>
          <p>
            {loading && !items.length
              ? "Đang tải thông báo…"
              : error && !items.length
                ? "Chưa thể đọc thông báo của bạn."
                : unreadCount
                  ? `${unreadCount} thông báo chưa đọc${cursor ? " đang hiển thị" : ""}`
                  : items.length
                    ? cursor
                      ? "Các thông báo đang hiển thị đều đã đọc."
                      : "Bạn đã đọc tất cả thông báo hiện có."
                    : "Các cập nhật sẽ xuất hiện tại đây."}
          </p>
        </div>

        <button
          className={styles.markAll}
          type="button"
          onClick={() => void all()}
          disabled={loading || !unreadCount || Boolean(pending)}
        >
          <CheckCircle size={15} weight="bold" />
          <span>{pending === "all" ? "Đang cập nhật…" : "Đánh dấu đã đọc"}</span>
        </button>
      </header>

      {/* Tabs lọc nhanh: Tất cả / Chưa đọc */}
      {items.length > 0 ? (
        <div className={styles.filterTabs} role="group" aria-label="Lọc thông báo">
          <button
            type="button"
            className={`${styles.filterTab} ${filter === "ALL" ? styles.filterTabActive : ""}`}
            onClick={() => setFilter("ALL")}
          >
            <span>Tất cả ({items.length})</span>
          </button>
          <button
            type="button"
            className={`${styles.filterTab} ${filter === "UNREAD" ? styles.filterTabActive : ""}`}
            onClick={() => setFilter("UNREAD")}
          >
            <span>Chưa đọc ({unreadCount})</span>
          </button>
        </div>
      ) : null}

      {error ? (
        <div className={styles.error} role="alert">
          <p>{error}</p>
          <button type="button" disabled={loading} onClick={() => void load(failedCursor)}>
            Thử lại tải thông báo
          </button>
        </div>
      ) : null}

      {loading && !items.length ? <p className={styles.loading}>Đang tải thông báo…</p> : null}

      {!loading && !error && !items.length ? (
        <div className={styles.empty}>
          <div className={styles.emptyIconWrap} aria-hidden="true">
            <BellSimpleSlash size={48} weight="duotone" />
          </div>
          <h2>Chưa có thông báo</h2>
          <p>Các cập nhật từ đơn hàng, nhu cầu và tài khoản sẽ xuất hiện tại đây.</p>
        </div>
      ) : null}

      {displayedItems.length ? (
        <ol className={styles.list}>
          {displayedItems.map((notification) => {
            const href = notificationDeepLink(notification.data);
            const unread = !notification.read;
            const { icon: IconComp, color } = getNotificationIcon(notification.type);
            const billingSummary = notificationBillingSummary(notification.type, notification.data);

            return (
              <li key={notification.id}>
                <button
                  className={`${styles.item} ${unread ? styles.itemUnread : ""}`}
                  disabled={loading || Boolean(pending)}
                  type="button"
                  onClick={() => void openNotification(notification)}
                  aria-label={`${notificationLabel(notification.type ?? "", notification.data)}. ${
                    href ? "Mở chi tiết" : unread ? "Đánh dấu đã đọc" : "Đã đọc"
                  }`}
                >
                  <div className={`${styles.iconBox} ${styles[`iconBox--${color}`]}`} aria-hidden="true">
                    <IconComp size={20} weight="duotone" />
                  </div>

                  <span className={styles.content}>
                    <span className={styles.titleRow}>
                      <strong>{notificationLabel(notification.type ?? "", notification.data)}</strong>
                      {unread ? <span className={styles.indicator} aria-hidden="true" /> : null}
                    </span>
                    {billingSummary ? <span>{billingSummary}</span> : null}
                    <small>
                      {notification.createdAt
                        ? new Date(notification.createdAt).toLocaleString("vi-VN")
                        : ""}
                    </small>
                  </span>

                  <span className={styles.state}>
                    {href ? (
                      <span className={styles.openWithArrow}>
                        <span>Mở</span>
                        <ArrowRight size={13} weight="bold" />
                      </span>
                    ) : unread ? (
                      "Chưa đọc"
                    ) : (
                      "Đã đọc"
                    )}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      ) : null}

      {cursor ? (
        <button
          className={styles.more}
          disabled={loading || Boolean(pending)}
          type="button"
          onClick={() => void load(cursor)}
        >
          {loading ? "Đang tải…" : "Tải thêm"}
        </button>
      ) : null}
    </section>
  );
}
