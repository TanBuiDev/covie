"use client";

import { ErrorState } from "@/components/ui/error-state";

export default function SellerError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  return (
    <main className="seller-entry">
      <ErrorState
        title="Không thể tải Seller Center"
        description="Dữ liệu hoặc quyền truy cập có thể đã thay đổi. Hãy thử tải lại phần này."
        action={<button type="button" onClick={reset}>Thử lại</button>}
      />
    </main>
  );
}
