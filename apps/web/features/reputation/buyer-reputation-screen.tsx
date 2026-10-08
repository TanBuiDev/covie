"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { getMyReputation, type BuyerReputation } from "./reputation-api";
import styles from "./buyer-reputation-screen.module.css";

function rateCopy(value: number | undefined): string {
  if (value == null) return "Chưa có dữ liệu";
  return `${(value / 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`;
}

export function BuyerReputationScreen() {
  const router = useRouter();
  const [reputation, setReputation] = useState<BuyerReputation>();
  const [error, setError] = useState(false);

  const load = useCallback(async () => {
    setError(false);
    try {
      setReputation(await getMyReputation());
    } catch {
      setError(true);
    }
  }, []);

  useEffect(() => { queueMicrotask(() => { void load(); }); }, [load]);

  return (
    <article className={styles.screen}>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button>
        <h1>Uy tín của tôi</h1>
      </header>
      <div className={styles.content}>
        <h1 className={styles.desktopTitle}>Uy tín của tôi</h1>
        {!reputation && !error ? <p className={styles.state} role="status">Đang tải uy tín…</p> : null}
        {error ? (
          <section className={styles.error} role="alert">
            <strong>Không thể tải uy tín</strong>
            <p>Vui lòng kiểm tra kết nối và thử lại.</p>
            <button type="button" onClick={() => void load()}>Thử lại</button>
          </section>
        ) : null}
        {reputation ? (
          <section className={styles.card} aria-labelledby="reputation-summary-title">
            <h2 id="reputation-summary-title">Uy tín giao dịch</h2>
            <div className={styles.metric}>
              <span>Tỷ lệ hoàn thành</span>
              <strong>{rateCopy(reputation.completionRateBasisPoints)}</strong>
            </div>
            <div className={styles.progress} aria-hidden="true"><span style={{ width: `${Math.min(100, Math.max(0, (reputation.completionRateBasisPoints ?? 0) / 100))}%` }} /></div>
            {reputation.eligibleOrders != null && reputation.completedOrders != null && reputation.eligibleOrders > 0 ? (
              <p>{reputation.completedOrders}/{reputation.eligibleOrders} giao dịch đủ điều kiện đã hoàn tất.</p>
            ) : null}
            <p>Chỉ hiển thị chỉ số uy tín được hệ thống cho phép công khai.</p>
            {reputation.warning ? <p className={styles.warning} role="status">Tỷ lệ hoàn thành đang ở mức cần lưu ý. Hãy hoàn tất các giao dịch đã xác nhận đúng thỏa thuận.</p> : null}
            <div className={styles.explanation}>
              <h3>Tỷ lệ hoàn thành là gì?</h3>
              <p>Chỉ số tóm tắt mức độ hoàn tất giao dịch theo dữ liệu hệ thống. CoVie không công khai chi tiết lịch sử giao dịch cá nhân của bạn.</p>
            </div>
          </section>
        ) : null}
      </div>
    </article>
  );
}
