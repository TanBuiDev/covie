"use client";

import { useEffect, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import {
  Bell,
  MagnifyingGlass,
  Moon,
  Sun,
  ShieldCheck,
  Check,
} from "@phosphor-icons/react";
import styles from "./clarity-ops.module.css";

interface NotificationItem {
  id: string;
  title: string;
  time: string;
  unread: boolean;
  href: string;
}

const initialNotifications: NotificationItem[] = [
  {
    id: "notif-1",
    title: "5 báo cáo vi phạm nội dung đang chờ kiểm duyệt",
    time: "10 phút trước",
    unread: true,
    href: "/admin/reports",
  },
  {
    id: "notif-2",
    title: "Đơn hàng CV-2026-9045 có phản ánh cần hỗ trợ",
    time: "45 phút trước",
    unread: true,
    href: "/admin/orders",
  },
  {
    id: "notif-3",
    title: "Hệ thống hoàn tất đồng bộ nhật ký kiểm toán",
    time: "2 giờ trước",
    unread: false,
    href: "/admin/audit",
  },
];

let themeFallback = false;

function readTheme(): boolean {
  try {
    const saved = typeof localStorage !== "undefined" ? localStorage.getItem("covie-admin-theme") : null;
    if (saved === "dark") return true;
    if (saved === "light") return false;
    return typeof window !== "undefined" && typeof window.matchMedia === "function"
      ? window.matchMedia("(prefers-color-scheme: dark)").matches
      : false;
  } catch {
    return themeFallback;
  }
}

function subscribeTheme(listener: () => void) {
  if (typeof window === "undefined") return () => undefined;
  window.addEventListener("storage", listener);
  window.addEventListener("covie-admin-theme-change", listener);
  return () => {
    window.removeEventListener("storage", listener);
    window.removeEventListener("covie-admin-theme-change", listener);
  };
}

interface ClarityTopbarProps {
  onSearch?: (query: string) => void;
}

export function ClarityTopbar({ onSearch }: ClarityTopbarProps) {
  const isDark = useSyncExternalStore(subscribeTheme, readTheme, () => false);
  const [showNotifications, setShowNotifications] = useState(false);
  const [notifications, setNotifications] = useState(initialNotifications);
  const [searchVal, setSearchVal] = useState("");

  useEffect(() => {
    if (isDark) {
      document.documentElement.setAttribute("data-theme", "dark");
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
  }, [isDark]);

  const toggleTheme = () => {
    const nextDark = !isDark;
    themeFallback = nextDark;
    try {
      localStorage.setItem("covie-admin-theme", nextDark ? "dark" : "light");
    } catch {
      // storage unavailable
    }
    if (nextDark) {
      document.documentElement.setAttribute("data-theme", "dark");
    } else {
      document.documentElement.removeAttribute("data-theme");
    }
    window.dispatchEvent(new Event("covie-admin-theme-change"));
  };

  const unreadCount = notifications.filter((n) => n.unread).length;

  const markAllRead = () => {
    setNotifications((prev) => prev.map((n) => ({ ...n, unread: false })));
  };

  const handleSearchChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSearchVal(e.target.value);
    if (onSearch) {
      onSearch(e.target.value);
    }
  };

  return (
    <header className={styles.topbar} aria-label="Thanh điều khiển đầu trang Admin">
      <nav aria-label="Breadcrumb" className={styles.breadcrumb}>
        <Link href="/admin" style={{ display: "flex", alignItems: "center", gap: "6px", textDecoration: "none" }}>
          <ShieldCheck size={18} weight="fill" style={{ color: "var(--clarity-action-primary)" }} />
          <span>Admin</span>
        </Link>
        <span aria-hidden="true" style={{ color: "var(--clarity-text-muted)" }}>/</span>
        <span className={styles.breadcrumbCurrent}>Tổng quan</span>
      </nav>

      <div className={styles.topbarActions}>
        {/* Global Search */}
        <div className={styles.searchBox}>
          <MagnifyingGlass size={16} style={{ color: "var(--clarity-text-muted)" }} />
          <input
            type="text"
            className={styles.searchInput}
            placeholder="Tìm kiếm nhanh trên hệ thống..."
            value={searchVal}
            onChange={handleSearchChange}
            aria-label="Tìm kiếm nhanh toàn hệ thống"
          />
          <kbd className={styles.kbdShortcut}>Ctrl K</kbd>
        </div>

        {/* Theme Toggle */}
        <button
          type="button"
          className={styles.themeToggle}
          onClick={toggleTheme}
          title={isDark ? "Chuyển sang giao diện Sáng" : "Chuyển sang giao diện Tối (Dark mode)"}
          aria-label={isDark ? "Chuyển sang giao diện Sáng" : "Chuyển sang giao diện Tối"}
        >
          {isDark ? <Sun size={18} weight="bold" /> : <Moon size={18} weight="bold" />}
        </button>

        {/* Notifications */}
        <div style={{ position: "relative" }}>
          <button
            type="button"
            className={styles.themeToggle}
            onClick={() => setShowNotifications(!showNotifications)}
            title="Thông báo hệ thống"
            aria-label={`Thông báo hệ thống (${unreadCount} chưa đọc)`}
            aria-expanded={showNotifications}
          >
            <Bell size={18} weight="bold" />
            {unreadCount > 0 && (
              <span
                style={{
                  position: "absolute",
                  top: "6px",
                  right: "6px",
                  width: "8px",
                  height: "8px",
                  borderRadius: "50%",
                  background: "var(--clarity-state-danger)",
                }}
              />
            )}
          </button>

          {showNotifications && (
            <div
              style={{
                position: "absolute",
                right: 0,
                top: "44px",
                width: "320px",
                background: "var(--clarity-surface)",
                border: "1px solid var(--clarity-border)",
                borderRadius: "var(--clarity-radius-card)",
                boxShadow: "var(--clarity-shadow-elevated)",
                zIndex: 50,
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  padding: "12px 14px",
                  borderBottom: "1px solid var(--clarity-border)",
                }}
              >
                <strong style={{ fontSize: "13px", color: "var(--clarity-text-primary)" }}>
                  Thông báo ({unreadCount})
                </strong>
                {unreadCount > 0 && (
                  <button
                    type="button"
                    style={{
                      background: "transparent",
                      border: "none",
                      color: "var(--clarity-action-primary)",
                      fontSize: "11px",
                      fontWeight: 600,
                      cursor: "pointer",
                      display: "flex",
                      alignItems: "center",
                      gap: "3px",
                    }}
                    onClick={markAllRead}
                  >
                    <Check size={12} weight="bold" />
                    <span>Đánh dấu đã đọc</span>
                  </button>
                )}
              </div>

              <div style={{ display: "flex", flexDirection: "column" }}>
                {notifications.map((n) => (
                  <Link
                    key={n.id}
                    href={n.href}
                    style={{
                      padding: "10px 14px",
                      borderBottom: "1px solid var(--clarity-border-subtle)",
                      textDecoration: "none",
                      background: n.unread ? "var(--clarity-action-subtle)" : "transparent",
                      display: "flex",
                      flexDirection: "column",
                      gap: "2px",
                    }}
                    onClick={() => setShowNotifications(false)}
                  >
                    <span style={{ fontSize: "12px", fontWeight: n.unread ? 600 : 500, color: "var(--clarity-text-primary)" }}>
                      {n.title}
                    </span>
                    <span style={{ fontSize: "11px", color: "var(--clarity-text-muted)" }}>
                      {n.time}
                    </span>
                  </Link>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </header>
  );
}
