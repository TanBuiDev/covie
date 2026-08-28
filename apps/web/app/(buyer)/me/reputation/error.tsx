"use client";
import { ErrorState } from "@/components/ui/error-state";
export default function ErrorPage({ reset }: { error: Error; reset: () => void }) {
  return <ErrorState title="Không thể tải uy tín" description="Vui lòng thử lại." action={<button type="button" onClick={reset}>Tải lại</button>} />;
}
