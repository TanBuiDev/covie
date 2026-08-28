"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { cancelNeed, getMyNeed, type NeedDetail as NeedDetailModel } from "./need-api";
import { NeedResponseList } from "./need-response-list";
import styles from "./buyer-needs-mobile.module.css";

const statusCopy = {
  ACTIVE: "Đang tìm",
  FULFILLED: "Đã đáp ứng",
  EXPIRED: "Đã hết hạn",
  CANCELLED: "Đã hủy",
} as const;

export function NeedDetail({ needId }: { needId: string }) {
  const [need, setNeed] = useState<NeedDetailModel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirmCancel, setConfirmCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    let active = true;
    getMyNeed(needId)
      .then((value) => { if (active) setNeed(value); })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu."); });
    return () => { active = false; };
  }, [needId]);

  if (error && !need) return <p className="form-alert" role="alert">{error}</p>;
  if (!need) return <LoadingState label="Đang tải nhu cầu…" />;

  async function submitCancellation() {
    if (!need || cancelling) return;
    setCancelling(true); setError(null);
    try {
      setNeed(await cancelNeed(need.id));
      setConfirmCancel(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể hủy nhu cầu.");
      try { setNeed(await getMyNeed(need.id)); } catch { /* Keep the last truthful snapshot. */ }
      setConfirmCancel(false);
    } finally { setCancelling(false); }
  }

  return (
    <div className={`need-detail ${styles.detailRoot}`}>
      <section className={`need-detail__summary ${styles.summary}`} aria-labelledby="need-detail-title">
        <div className={`need-detail__title-row ${styles.titleRow}`}>
          <div>
            <p className="eyebrow">{need.type === "SERVICE" ? "Dịch vụ cần tìm" : "Sản phẩm cần tìm"}</p>
            <h1 id="need-detail-title">{need.title}</h1>
          </div>
          <span className={`need-status need-status--${need.status.toLowerCase()} ${styles.detailStatus}`}>{statusCopy[need.status]}</span>
        </div>

        <p className={`need-detail__description ${styles.description}`}>{need.description || need.queryText}</p>
        <dl className={`need-detail__facts ${styles.facts}`}>
          {need.type === "PRODUCT" ? <div><dt>Số lượng</dt><dd>{need.requestedQuantity.toLocaleString("vi-VN")}</dd></div> : null}
          <div><dt>Bán kính</dt><dd>{(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd></div>
          <div><dt>Phản hồi</dt><dd>{need.responseCount.toLocaleString("vi-VN")}</dd></div>
          <div><dt>Hết hạn</dt><dd>{new Date(need.expiresAt).toLocaleString("vi-VN")}</dd></div>
        </dl>

        {need.fulfilledOrderId ? <p className={`need-detail__fulfilled ${styles.fulfilled}`}>Đã tạo đơn đáp ứng: <Link href={`/orders/${need.fulfilledOrderId}`}>Xem đơn hàng</Link></p> : null}
        {need.canCancel ? <button className="button-danger" type="button" onClick={() => setConfirmCancel(true)}>Hủy nhu cầu</button> : null}
        {error ? <p className="form-alert" role="alert">{error}</p> : null}
      </section>

      <NeedResponseList needId={needId} />
      <ConfirmDialog open={confirmCancel} title="Hủy nhu cầu này?" description="Các phản hồi hiện có sẽ không còn dùng để tạo đơn." confirmLabel={cancelling ? "Đang hủy…" : "Xác nhận hủy"} dangerous confirmDisabled={cancelling} cancelDisabled={cancelling} onConfirm={() => void submitCancellation()} onCancel={() => setConfirmCancel(false)} />
    </div>
  );
}
