"use client";

import { useEffect, useState } from "react";

export function useBillingExpiry(expiresAt: string | undefined): number | null {
  const deadline = expiresAt ? Date.parse(expiresAt) : NaN;
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!Number.isFinite(deadline)) return;
    if (deadline <= Date.now()) {
      const refresh = setTimeout(() => setNow(Date.now()), 0);
      return () => clearTimeout(refresh);
    }
    const timer = setInterval(() => {
      const current = Date.now();
      setNow(current);
      if (current >= deadline) clearInterval(timer);
    }, 1000);
    return () => clearInterval(timer);
  }, [deadline]);
  return Number.isFinite(deadline) ? Math.max(0, Math.ceil((deadline - now) / 1000)) : null;
}

export function BillingExpiry({ expiresAt, label }: { expiresAt: string | undefined; label: string }) {
  const remaining = useBillingExpiry(expiresAt);
  if (remaining === null) return <span>Chưa xác định được thời hạn. Vui lòng kiểm tra trạng thái từ máy chủ.</span>;
  const minutes = Math.floor(remaining / 60).toString().padStart(2, "0");
  const seconds = (remaining % 60).toString().padStart(2, "0");
  return <>
    <span role="timer" aria-label={label} aria-live="off">{label}: {minutes}:{seconds}.</span>
    {remaining === 0 ? <span role="status"> Thời hạn hiển thị đã hết. Hãy tải lại trạng thái; không tạo thêm giao dịch khi hóa đơn còn chờ xác nhận.</span> : null}
  </>;
}

export function BillingRemainingDays({ expiresAt }: { expiresAt: string | undefined }) {
  const remaining = useBillingExpiry(expiresAt);
  if (remaining === null) return null;
  return <p aria-live="off">Còn khoảng {Math.ceil(remaining / 86400)} ngày (mỗi ngày 24 giờ). Quyền mở bán vẫn theo xác minh từ máy chủ.</p>;
}
