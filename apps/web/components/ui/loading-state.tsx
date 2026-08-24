export function LoadingState({ label = "Đang tải…" }: { label?: string }) {
  return <p className="loading-state" role="status" aria-live="polite">{label}</p>;
}
