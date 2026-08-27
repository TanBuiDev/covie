import type { ReactNode } from "react";

export type AdminTone = "neutral" | "info" | "success" | "warning" | "error";

export function AdminPageHeader({ title, subtitle, action }: { title: string; subtitle: string; action?: ReactNode }) {
  return (
    <header className="admin-page-header">
      <div>
        <h1>{title}</h1>
        <p>{subtitle}</p>
      </div>
      {action ? <div className="admin-page-header__action">{action}</div> : null}
    </header>
  );
}

export function AdminSurface({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <section className={`admin-surface ${className}`.trim()}>{children}</section>;
}

export function AdminStatusChip({ label, tone = "neutral" }: { label: string; tone?: AdminTone }) {
  return <span className={`admin-status-chip admin-status-chip--${tone}`}><span aria-hidden="true" className="admin-status-chip__dot" />{label}</span>;
}

export function AdminCapabilityState({ title, message, readOnly = false }: { title: string; message: string; readOnly?: boolean }) {
  return (
    <AdminSurface className="admin-capability-state">
      <AdminStatusChip label={readOnly ? "Chỉ đọc" : "Chưa khả dụng"} tone="neutral" />
      <h2>{title}</h2>
      <p>{message}</p>
    </AdminSurface>
  );
}

export function formatAdminDate(value: unknown) {
  if (typeof value !== "string" || !value) return "—";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("vi-VN", { dateStyle: "short", timeStyle: "short" }).format(date);
}

export function presentAccountStatus(raw: unknown): { label: string; tone: AdminTone } {
  switch (raw) {
    case "ACTIVE": return { label: "Đang hoạt động", tone: "success" };
    case "SUSPENDED": return { label: "Tạm ngưng", tone: "warning" };
    case "INACTIVE": return { label: "Không hoạt động", tone: "neutral" };
    case "DELETED": return { label: "Đã xoá", tone: "error" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

export function presentOrderStatus(raw: unknown): { label: string; tone: AdminTone } {
  switch (raw) {
    case "PENDING": return { label: "Chờ xác nhận", tone: "warning" };
    case "CONFIRMED": return { label: "Đã xác nhận", tone: "info" };
    case "PROCESSING": return { label: "Đang xử lý", tone: "info" };
    case "SHIPPING":
    case "IN_DELIVERY": return { label: "Đang giao", tone: "info" };
    case "COMPLETED":
    case "DELIVERED": return { label: "Hoàn tất", tone: "success" };
    case "CANCELLED":
    case "CANCELED": return { label: "Đã huỷ", tone: "error" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}
