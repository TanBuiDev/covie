"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import {
  X,
  Copy,
  Check,
  ArrowSquareOut,
  User,
  MapPin,
  CreditCard,
  Package,
  ClockCounterClockwise,
} from "@phosphor-icons/react";
import type { TransactionRecord } from "./clarity-types";
import { formatVnd } from "./clarity-data";
import styles from "./clarity-ops.module.css";

interface ClarityDetailDrawerProps {
  record: TransactionRecord | null;
  onClose: () => void;
}

export function ClarityDetailDrawer({ record, onClose }: ClarityDetailDrawerProps) {
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        onClose();
      }
    };
    if (record) {
      window.addEventListener("keydown", handleKeyDown);
    }
    return () => {
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [record, onClose]);

  if (!record) return null;

  const handleCopy = () => {
    void navigator.clipboard.writeText(record.orderCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getStatusBadge = () => {
    switch (record.status) {
      case "COMPLETED":
        return <span className={`${styles.statusBadge} ${styles.statusCompleted}`}>● Hoàn tất</span>;
      case "PENDING":
        return <span className={`${styles.statusBadge} ${styles.statusPending}`}>● Chờ xử lý</span>;
      case "REFUNDED":
        return <span className={`${styles.statusBadge} ${styles.statusRefunded}`}>● Đã hoàn tiền</span>;
      case "ISSUE":
        return <span className={`${styles.statusBadge} ${styles.statusIssue}`}>● Có vấn đề</span>;
      default:
        return <span className={styles.statusBadge}>● {record.status}</span>;
    }
  };

  return (
    <div
      className={styles.drawerOverlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-labelledby="drawer-title"
    >
      <div className={styles.drawerPanel}>
        {/* Drawer Header */}
        <div className={styles.drawerHeader}>
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "4px" }}>
              <h2 id="drawer-title" className={styles.drawerTitle}>
                {record.orderCode}
              </h2>
              {getStatusBadge()}
            </div>
            <span style={{ fontSize: "12px", color: "var(--clarity-text-muted)" }}>
              Khởi tạo lúc {record.formattedDate} · Kênh: {record.channel}
            </span>
          </div>

          <button
            type="button"
            className={styles.drawerCloseBtn}
            onClick={onClose}
            aria-label="Đóng bảng chi tiết"
          >
            <X size={20} weight="bold" />
          </button>
        </div>

        {/* Drawer Body */}
        <div className={styles.drawerContent}>
          {/* Quick Actions Row */}
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
            <button
              type="button"
              className={styles.btnOutline}
              style={{ fontSize: "12px", padding: "6px 12px", minHeight: "34px" }}
              onClick={handleCopy}
            >
              {copied ? <Check size={14} weight="bold" style={{ color: "var(--clarity-state-success)" }} /> : <Copy size={14} />}
              <span>{copied ? "Đã chép mã" : "Sao chép mã đơn"}</span>
            </button>

            <Link
              href={`/admin/orders?orderCode=${encodeURIComponent(record.orderCode)}`}
              className={styles.btnOutline}
              style={{ fontSize: "12px", padding: "6px 12px", minHeight: "34px" }}
            >
              <ArrowSquareOut size={14} />
              <span>Xem trên Đơn hàng sàn</span>
            </Link>
          </div>

          {/* Customer & Delivery Section */}
          <div style={{ border: "1px solid var(--clarity-border)", borderRadius: "8px", padding: "14px", background: "var(--clarity-subtle)" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px", color: "var(--clarity-text-primary)", fontWeight: 600, fontSize: "13px" }}>
              <User size={16} weight="duotone" style={{ color: "var(--clarity-action-primary)" }} />
              <span>Thông tin khách hàng & Địa chỉ</span>
            </div>
            <div style={{ display: "flex", flexDirection: "column", gap: "4px", fontSize: "13px" }}>
              <div><strong>{record.customerName}</strong> ({record.customerEmail})</div>
              {record.customerPhone && <div style={{ color: "var(--clarity-text-secondary)" }}>SĐT: {record.customerPhone}</div>}
              {record.address && (
                <div style={{ display: "flex", alignItems: "flex-start", gap: "6px", color: "var(--clarity-text-secondary)", marginTop: "4px" }}>
                  <MapPin size={16} style={{ flexShrink: 0, marginTop: "2px" }} />
                  <span>{record.address}</span>
                </div>
              )}
            </div>
          </div>

          {/* Ordered Items Section */}
          <div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "10px" }}>
              <Package size={18} weight="duotone" style={{ color: "var(--clarity-action-primary)" }} />
              <h3 className={styles.drawerSectionTitle} style={{ margin: 0 }}>
                Chi tiết mặt hàng ({record.items.length})
              </h3>
            </div>
            <div style={{ border: "1px solid var(--clarity-border)", borderRadius: "8px", overflow: "hidden" }}>
              {record.items.map((item, i) => (
                <div
                  key={i}
                  style={{
                    display: "flex",
                    justifyContent: "space-between",
                    padding: "10px 14px",
                    borderBottom: i < record.items.length - 1 ? "1px solid var(--clarity-border-subtle)" : "none",
                    fontSize: "13px",
                  }}
                >
                  <div style={{ maxWidth: "70%" }}>
                    <div style={{ fontWeight: 500, color: "var(--clarity-text-primary)" }}>{item.name}</div>
                    <div style={{ fontSize: "12px", color: "var(--clarity-text-muted)" }}>
                      {formatVnd(item.unitPrice)} × {item.quantity}
                    </div>
                  </div>
                  <div style={{ fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>
                    {formatVnd(item.total)}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Pricing Summary */}
          <div style={{ borderTop: "1px solid var(--clarity-border)", paddingTop: "14px" }}>
            <div style={{ display: "flex", flexDirection: "column", gap: "8px", fontSize: "13px" }}>
              <div style={{ display: "flex", justifyContent: "space-between", color: "var(--clarity-text-secondary)" }}>
                <span>Tiền hàng</span>
                <span>{formatVnd(record.subtotal)}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between", color: "var(--clarity-text-secondary)" }}>
                <span>Phí vận chuyển</span>
                <span>{record.shippingFee > 0 ? formatVnd(record.shippingFee) : "Miễn phí"}</span>
              </div>
              {record.discount > 0 && (
                <div style={{ display: "flex", justifyContent: "space-between", color: "var(--clarity-state-success)" }}>
                  <span>Chiết khấu cư dân</span>
                  <span>-{formatVnd(record.discount)}</span>
                </div>
              )}
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: "16px", fontWeight: 700, color: "var(--clarity-text-primary)", borderTop: "1px dashed var(--clarity-border)", paddingTop: "8px", marginTop: "4px" }}>
                <span>Tổng thanh toán</span>
                <span style={{ color: "var(--clarity-action-primary)" }}>{record.formattedAmount}</span>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "12px", color: "var(--clarity-text-muted)", marginTop: "4px" }}>
                <CreditCard size={16} />
                <span>Phương thức: {record.paymentMethod}</span>
              </div>
            </div>
          </div>

          {/* Status Timeline */}
          <div style={{ borderTop: "1px solid var(--clarity-border)", paddingTop: "14px" }}>
            <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "14px" }}>
              <ClockCounterClockwise size={18} weight="duotone" style={{ color: "var(--clarity-action-primary)" }} />
              <h3 className={styles.drawerSectionTitle} style={{ margin: 0 }}>
                Lịch sử tiến trình giao dịch
              </h3>
            </div>
            <div className={styles.timeline}>
              {record.events.map((ev, idx) => (
                <div key={idx} className={styles.timelineItem}>
                  <div className={styles.timelineMarker} />
                  <div style={{ fontSize: "11px", fontWeight: 600, color: "var(--clarity-text-muted)" }}>
                    {ev.time}
                  </div>
                  <div style={{ fontSize: "13px", fontWeight: 600, color: "var(--clarity-text-primary)" }}>
                    {ev.title}
                  </div>
                  <div style={{ fontSize: "12px", color: "var(--clarity-text-secondary)" }}>
                    {ev.description}
                  </div>
                </div>
              ))}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
