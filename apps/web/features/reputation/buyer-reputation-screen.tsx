"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import {
  CheckCircle,
  Clock,
  Handshake,
  Info,
  ShieldCheck,
  WarningCircle,
} from "@phosphor-icons/react";
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

  useEffect(() => {
    queueMicrotask(() => {
      void load();
    });
  }, [load]);

  const completionPercent = Math.min(
    100,
    Math.max(0, (reputation?.completionRateBasisPoints ?? 0) / 100)
  );

  return (
    <article className={styles.screen}>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}>
          <CoVieIcon name="back" />
        </button>
        <h1>Uy tín của tôi</h1>
      </header>

      <div className={styles.content}>
        <div className={styles.desktopHeader}>
          <h1 className={styles.desktopTitle}>Uy tín của tôi</h1>
          <p className={styles.desktopSubtitle}>
            Chỉ số đo lường mức độ hoàn thành giao dịch văn minh trong cộng đồng CoVie
          </p>
        </div>

        {!reputation && !error ? (
          <div className={styles.state} role="status">
            <p>Đang tải uy tín…</p>
          </div>
        ) : null}

        {error ? (
          <section className={styles.error} role="alert">
            <div className={styles.errorIconWrap}>
              <WarningCircle size={28} weight="bold" />
            </div>
            <strong>Không thể tải uy tín</strong>
            <p>Vui lòng kiểm tra kết nối và thử lại.</p>
            <button type="button" onClick={() => void load()}>
              Thử lại
            </button>
          </section>
        ) : null}

        {reputation ? (
          <div className={styles.trustDashboard}>
            <section className={styles.card} aria-labelledby="reputation-summary-title">
              <div className={styles.cardTop}>
                <div>
                  <span className={styles.cardEyebrow}>Chỉ số cư dân</span>
                  <h2 id="reputation-summary-title">Uy tín giao dịch</h2>
                </div>

                {reputation.warning ? (
                  <span className={styles.tierBadgeWarning}>
                    <WarningCircle size={14} weight="bold" />
                    <span>Cần lưu ý</span>
                  </span>
                ) : (reputation.completionRateBasisPoints ?? 0) >= 9000 ? (
                  <span className={styles.tierBadgeHigh}>
                    <ShieldCheck size={14} weight="fill" />
                    <span>Tin cậy cao</span>
                  </span>
                ) : (
                  <span className={styles.tierBadgeNormal}>
                    <CheckCircle size={14} weight="fill" />
                    <span>Cư dân tiêu chuẩn</span>
                  </span>
                )}
              </div>

              <div className={styles.metric}>
                <span>Tỷ lệ hoàn thành</span>
                <strong>{rateCopy(reputation.completionRateBasisPoints)}</strong>
              </div>

              <div className={styles.progress} aria-hidden="true">
                <span style={{ width: `${completionPercent}%` }} />
              </div>

              {reputation.eligibleOrders != null &&
              reputation.completedOrders != null &&
              reputation.eligibleOrders > 0 ? (
                <p className={styles.orderSummary}>
                  {reputation.completedOrders}/{reputation.eligibleOrders} giao dịch đủ điều kiện đã hoàn tất.
                </p>
              ) : null}

              <p className={styles.publicNote}>
                Chỉ hiển thị chỉ số uy tín được hệ thống cho phép công khai.
              </p>

              {reputation.warning ? (
                <div className={styles.warning} role="status">
                  <WarningCircle size={18} weight="bold" className={styles.warningIcon} />
                  <p>
                    Tỷ lệ hoàn thành đang ở mức cần lưu ý. Hãy hoàn tất các giao dịch đã xác nhận đúng thỏa thuận.
                  </p>
                </div>
              ) : null}

              <div className={styles.explanation}>
                <div className={styles.explanationHeader}>
                  <Info size={16} weight="duotone" />
                  <h3>Tỷ lệ hoàn thành là gì?</h3>
                </div>
                <p>
                  Chỉ số tóm tắt mức độ hoàn tất giao dịch theo dữ liệu hệ thống. CoVie không công khai chi tiết lịch sử giao dịch cá nhân của bạn.
                </p>
              </div>
            </section>

            {/* 3 Trụ cột uy tín cộng đồng */}
            <section className={styles.pillarsCard}>
              <h3>Nguyên tắc giao dịch văn minh tại CoVie</h3>
              <div className={styles.pillarsGrid}>
                <div className={styles.pillarItem}>
                  <div className={styles.pillarIcon}>
                    <Handshake size={20} weight="duotone" />
                  </div>
                  <div>
                    <strong>Cam kết nhận hàng</strong>
                    <p>Hoàn tất đơn hàng đã chốt, không hủy đơn đột xuất sau khi người bán đã chuẩn bị hàng.</p>
                  </div>
                </div>

                <div className={styles.pillarItem}>
                  <div className={styles.pillarIcon}>
                    <Clock size={20} weight="duotone" />
                  </div>
                  <div>
                    <strong>Nhận hàng đúng hẹn</strong>
                    <p>Có mặt đúng giờ hoặc có hướng dẫn rõ ràng (ví dụ: gửi sảnh chính) để người bán thuận tiện giao.</p>
                  </div>
                </div>

                <div className={styles.pillarItem}>
                  <div className={styles.pillarIcon}>
                    <ShieldCheck size={20} weight="duotone" />
                  </div>
                  <div>
                    <strong>Bảo mật & minh bạch</strong>
                    <p>Dữ liệu giao dịch được bảo mật tuyệt đối, chỉ dùng để nâng cao độ tin cậy trong cộng đồng nội khu.</p>
                  </div>
                </div>
              </div>
            </section>
          </div>
        ) : null}
      </div>
    </article>
  );
}
