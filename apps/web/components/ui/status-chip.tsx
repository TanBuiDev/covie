import { statusCopy } from "@/lib/format/status-copy";

export type StatusTone = "neutral" | "info" | "success" | "warning" | "error";

export function StatusChip({
  label,
  tone = "neutral",
  status,
}: {
  label?: string;
  tone?: StatusTone;
  /** Legacy compatibility for non-Seller surfaces. Seller callers should supply label + tone. */
  status?: string | null;
}) {
  const resolvedLabel = label ?? statusCopy(status);
  return <span className="status-chip" data-tone={tone}>{resolvedLabel || "Trạng thái đã được cập nhật"}</span>;
}
