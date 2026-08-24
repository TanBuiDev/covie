import { statusCopy } from "@/lib/format/status-copy";

export function OrderStatusLabel({ status }: { status?: string }) {
  const safeClass = status && /^[A-Z0-9_]+$/.test(status) ? status.toLowerCase() : "unknown";
  return <span className={`order-status order-status--${safeClass}`}>{statusCopy(status)}</span>;
}
