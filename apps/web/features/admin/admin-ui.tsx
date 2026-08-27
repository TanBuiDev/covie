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

export function formatAdminMoney(amount: unknown, currency: unknown = "VND") {
  if (typeof amount !== "number") return "—";
  const code = typeof currency === "string" && currency ? currency : "VND";
  try { return new Intl.NumberFormat("vi-VN", { style: "currency", currency: code, maximumFractionDigits: 0 }).format(amount); }
  catch { return `${new Intl.NumberFormat("vi-VN").format(amount)} ${code}`; }
}

export function presentAccountStatus(raw: unknown): { label: string; tone: AdminTone } {
  switch (raw) {
    case "ACTIVE": return { label: "Đang hoạt động", tone: "success" };
    case "PAUSED": return { label: "Tạm dừng", tone: "warning" };
    case "SUSPENDED": return { label: "Tạm ngưng", tone: "warning" };
    case "BANNED": return { label: "Bị cấm", tone: "error" };
    case "INACTIVE": return { label: "Không hoạt động", tone: "neutral" };
    case "DELETED": return { label: "Đã xoá", tone: "error" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

export function presentModerationStatus(raw: unknown): { label: string; tone: AdminTone } {
  switch (raw) {
    case "NOT_REQUIRED": return { label: "Không yêu cầu", tone: "neutral" };
    case "PENDING": return { label: "Chờ kiểm duyệt", tone: "warning" };
    case "APPROVED": return { label: "Đã duyệt", tone: "success" };
    case "REJECTED": return { label: "Đã từ chối", tone: "error" };
    case "HIDDEN": return { label: "Đã ẩn", tone: "warning" };
    case "OPEN": return { label: "Đang mở", tone: "warning" };
    case "RESOLVED": return { label: "Đã xử lý", tone: "success" };
    case "CLOSED": return { label: "Đã đóng", tone: "neutral" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

export function presentOrderStatus(raw: unknown): { label: string; tone: AdminTone } {
  switch (raw) {
    case "PENDING_CONFIRMATION": return { label: "Chờ xác nhận", tone: "warning" };
    case "CONFIRMED": return { label: "Đã xác nhận", tone: "info" };
    case "OUT_FOR_DELIVERY": return { label: "Đang giao", tone: "info" };
    case "READY_FOR_PICKUP": return { label: "Sẵn sàng nhận", tone: "info" };
    case "AWAITING_BUYER_CONFIRMATION": return { label: "Chờ người mua xác nhận", tone: "warning" };
    case "CANCELLATION_REQUESTED": return { label: "Đang yêu cầu huỷ", tone: "warning" };
    case "DELIVERY_ISSUE_REPORTED": return { label: "Có vấn đề giao hàng", tone: "error" };
    case "SERVICE_ISSUE_REPORTED": return { label: "Có vấn đề dịch vụ", tone: "error" };
    case "BUYER_NO_SHOW": return { label: "Người mua không đến", tone: "warning" };
    case "NO_SHOW_CONTESTED": return { label: "Đang tranh chấp vắng mặt", tone: "warning" };
    case "COMPLETED": return { label: "Hoàn tất", tone: "success" };
    case "EXPIRED": return { label: "Hết hạn", tone: "neutral" };
    case "REJECTED_BY_SELLER": return { label: "Người bán từ chối", tone: "error" };
    case "CANCELLED_BEFORE_CONFIRMATION": return { label: "Đã huỷ trước xác nhận", tone: "neutral" };
    case "CANCELLED_BY_BUYER_AFTER_CONFIRMATION": return { label: "Người mua đã huỷ", tone: "neutral" };
    case "CANCELLED_BY_SELLER_AFTER_CONFIRMATION": return { label: "Người bán đã huỷ", tone: "neutral" };
    case "CLOSED_WITH_ISSUE": return { label: "Đóng với vấn đề", tone: "error" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}
