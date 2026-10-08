import { statusCopy } from "@/lib/format/status-copy";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import styles from "./order-status.module.css";

const tones: Record<string, StatusTone> = {
  PENDING_CONFIRMATION: "info", OUT_FOR_DELIVERY: "warning", COMPLETED: "success",
};

export function OrderStatusLabel({ status }: { status?: string }) {
  const safeClass = status && /^[A-Z0-9_]+$/.test(status) ? status.toLowerCase() : "unknown";
  const label = status === "COMPLETED" ? "Hoàn tất" : status === "PENDING_CONFIRMATION" ? "Chờ người bán xác nhận" : statusCopy(status);
  return <span className={`order-status order-status--${safeClass} ${styles.status}`}><StatusChip label={label} tone={tones[status ?? ""] ?? "neutral"} /></span>;
}
