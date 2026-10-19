"use client";

import { Warning, ArrowClockwise, MagnifyingGlass } from "@phosphor-icons/react";
import styles from "./clarity-ops.module.css";

export function ClaritySkeleton() {
  return (
    <div
      className={styles.dashboardStack}
      aria-busy="true"
      aria-label="Đang nạp dữ liệu bảng điều khiển"
      style={{ opacity: 0.7 }}
    >
      <div style={{ height: "64px", background: "var(--clarity-subtle)", borderRadius: "var(--clarity-radius-card)" }} />
      <div className={styles.kpiGrid}>
        {[1, 2, 3, 4].map((i) => (
          <div
            key={i}
            style={{
              height: "120px",
              background: "var(--clarity-subtle)",
              borderRadius: "var(--clarity-radius-card)",
              border: "1px solid var(--clarity-border)",
            }}
          />
        ))}
      </div>
      <div className={styles.understandRow}>
        <div style={{ height: "340px", background: "var(--clarity-subtle)", borderRadius: "var(--clarity-radius-card)" }} />
        <div style={{ height: "340px", background: "var(--clarity-subtle)", borderRadius: "var(--clarity-radius-card)" }} />
      </div>
      <div style={{ height: "280px", background: "var(--clarity-subtle)", borderRadius: "var(--clarity-radius-card)" }} />
    </div>
  );
}

interface ClarityEmptyStateProps {
  title?: string;
  description?: string;
  isFilterResult?: boolean;
  onClearFilter?: () => void;
}

export function ClarityEmptyState({
  title = "Không có kết quả",
  description = "Không tìm thấy dữ liệu nào phù hợp với bộ lọc hiện tại.",
  isFilterResult = true,
  onClearFilter,
}: ClarityEmptyStateProps) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "48px 24px",
        textAlign: "center",
        background: "var(--clarity-surface)",
        border: "1px dashed var(--clarity-border)",
        borderRadius: "var(--clarity-radius-card)",
      }}
    >
      <div
        style={{
          width: "48px",
          height: "48px",
          borderRadius: "50%",
          background: "var(--clarity-subtle)",
          display: "flex",
          alignItems: "center",
          justifyContent: "center",
          color: "var(--clarity-text-muted)",
          marginBottom: "12px",
        }}
      >
        <MagnifyingGlass size={24} />
      </div>
      <strong style={{ fontSize: "16px", color: "var(--clarity-text-primary)", marginBottom: "4px" }}>
        {title}
      </strong>
      <p style={{ fontSize: "13px", color: "var(--clarity-text-secondary)", maxWidth: "400px", margin: "0 0 16px 0" }}>
        {description}
      </p>
      {isFilterResult && onClearFilter && (
        <button type="button" className={styles.btnPrimary} onClick={onClearFilter}>
          <span>Xóa bộ lọc</span>
        </button>
      )}
    </div>
  );
}

interface ClarityErrorStateProps {
  message?: string;
  onRetry?: () => void;
}

export function ClarityErrorState({
  message = "Không thể tải dữ liệu phân tích. Vui lòng thử lại.",
  onRetry,
}: ClarityErrorStateProps) {
  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        padding: "36px 20px",
        textAlign: "center",
        background: "var(--clarity-state-danger-subtle)",
        border: "1px solid var(--clarity-state-danger)",
        borderRadius: "var(--clarity-radius-card)",
        color: "var(--clarity-state-danger)",
      }}
      role="alert"
    >
      <Warning size={32} weight="bold" style={{ marginBottom: "8px" }} />
      <strong style={{ fontSize: "15px", marginBottom: "4px" }}>Đã xảy ra lỗi khi tải dữ liệu</strong>
      <p style={{ fontSize: "13px", margin: "0 0 16px 0", maxWidth: "420px" }}>{message}</p>
      {onRetry && (
        <button
          type="button"
          className={styles.btnPrimary}
          style={{ background: "var(--clarity-state-danger)", borderColor: "var(--clarity-state-danger)" }}
          onClick={onRetry}
        >
          <ArrowClockwise size={16} weight="bold" />
          <span>Thử lại</span>
        </button>
      )}
    </div>
  );
}
