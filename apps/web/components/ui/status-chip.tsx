import { statusCopy } from "@/lib/format/status-copy";

export function StatusChip({ status }: { status: string | null | undefined }) {
  return <span className="status-chip" data-status={status ?? "UNKNOWN"}>{statusCopy(status)}</span>;
}
