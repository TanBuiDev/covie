"use client";

import Link from "next/link";
import { useEffect, useRef, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { ErrorState } from "@/components/ui/error-state";
import { CovieApiError } from "@/lib/api/api-error";
import { cancelNeed, getMyNeed, type NeedDetail as NeedDetailModel } from "./need-api";
import { Package, Wrench } from "@phosphor-icons/react";
import { NeedResponseList } from "./need-response-list";
import styles from "./buyer-needs-mobile.module.css";

const statusCopy = {
  ACTIVE: "Đang tìm",
  FULFILLED: "Đã đáp ứng",
  EXPIRED: "Đã hết hạn",
  CANCELLED: "Đã hủy",
} as const;

export function NeedDetail({ needId }: { needId: string }) {
  return <NeedDetailContent key={needId} needId={needId} />;
}

function NeedDetailContent({ needId }: { needId: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 60_000);
    return () => window.clearInterval(timer);
  }, []);
  const [need, setNeed] = useState<NeedDetailModel | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [isAuthError, setIsAuthError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const cancelPending = useRef(false);

  useEffect(() => {
    let active = true;
    getMyNeed(needId)
      .then((value) => { if (active) { setNeed(value); setError(null); setIsAuthError(false); } })
      .catch((cause) => { if (active) {
        setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
        setError("Không thể tải nhu cầu. Vui lòng thử lại.");
      } });
    return () => { active = false; };
  }, [needId, reloadKey]);

  if (error && (!need || isAuthError)) return <ErrorState
    title={isAuthError ? "Vui lòng đăng nhập lại" : "Không thể tải nhu cầu"}
    description={isAuthError ? "Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập." : error}
    action={isAuthError
      ? <Link className="button-primary" href={`/login?returnTo=${encodeURIComponent(`/needs/${encodeURIComponent(needId)}`)}`}>Đăng nhập lại</Link>
      : <button className="button-secondary" type="button" onClick={() => {setError(null);setReloadKey(value => value + 1);}}>Thử lại</button>}
  />;
  if (!need) return <LoadingState label="Đang tải nhu cầu…" />;

  const canCancel = need.allowedActions.includes("CANCEL");

  async function handleCancel() {
    if (!need || cancelPending.current || !need.allowedActions.includes("CANCEL")) return;
    cancelPending.current = true;
    setCancelling(true);
    setError(null);
    try {
      setNeed(await cancelNeed(need.id));
      setConfirmingCancel(false);
    } catch (cause) {
      setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
      setError("Không thể hủy yêu cầu. Vui lòng thử lại.");
    } finally {
      cancelPending.current = false;
      setCancelling(false);
    }
  }

  function getExpiryBadge(expiresAtStr: string) {
    const diffMs = new Date(expiresAtStr).getTime() - now;
    if (diffMs <= 0) return "Đã hết hạn";
    const diffHours = Math.round(diffMs / (1000 * 60 * 60));
    if (diffHours < 1) {
      const diffMins = Math.max(1, Math.round(diffMs / (1000 * 60)));
      return `Còn ${diffMins} phút`;
    }
    if (diffHours < 24) return `Còn ${diffHours} giờ`;
    const diffDays = Math.round(diffHours / 24);
    return `Còn ${diffDays} ngày`;
  }

  const isService = need.type === "SERVICE";
  const expiryLabel = getExpiryBadge(need.expiresAt);

  return (
    <div className={`need-detail ${styles.detailRoot}`}>
      <div className="need-detail__layout">
        <aside className="need-detail__sidebar">
          <section className={`need-detail__summary ${styles.summary}`} aria-labelledby="need-detail-title">
            <div className={`need-detail__title-row ${styles.titleRow}`}>
              <div className="need-detail__header-text">
                <div className="need-detail__type-strip">
                  <span className="need-detail__type-icon" aria-hidden="true">
                    {isService ? <Wrench size={16} weight="bold" /> : <Package size={16} weight="bold" />}
                  </span>
                  <p className="eyebrow">{isService ? "Dịch vụ cần tìm" : "Sản phẩm cần tìm"}</p>
                </div>
                <h1 id="need-detail-title">{need.title}</h1>
              </div>
              <span className={`need-status need-status--${need.status.toLowerCase()} ${styles.detailStatus}`}>
                {statusCopy[need.status]}
              </span>
            </div>

            <p className={`need-detail__description ${styles.description}`}>{need.description || need.queryText}</p>

            <dl className={`need-detail__facts ${styles.facts}`}>
              {need.type === "PRODUCT" ? (
                <div className="need-fact-card">
                  <dt>Số lượng</dt>
                  <dd>{need.requestedQuantity.toLocaleString("vi-VN")}</dd>
                </div>
              ) : null}
              <div className="need-fact-card">
                <dt>Bán kính</dt>
                <dd>{(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd>
              </div>
              <div className="need-fact-card">
                <dt>Phản hồi</dt>
                <dd>{need.responseCount.toLocaleString("vi-VN")}</dd>
              </div>
              <div className="need-fact-card">
                <dt>Hạn chót ({expiryLabel})</dt>
                <dd>{new Date(need.expiresAt).toLocaleString("vi-VN")}</dd>
              </div>
            </dl>

            {need.fulfilledOrderId ? (
              <p className={`need-detail__fulfilled ${styles.fulfilled}`}>
                Đã tạo đơn đáp ứng: <Link href={`/orders/${need.fulfilledOrderId}`}>Xem đơn hàng</Link>
              </p>
            ) : null}

            {canCancel ? (
              <div className={`need-detail__cancel ${styles.cancelArea}`}>
                {!confirmingCancel ? (
                  <button
                    className={styles.cancelButton}
                    type="button"
                    aria-expanded="false"
                    onClick={() => setConfirmingCancel(true)}
                  >
                    Hủy yêu cầu
                  </button>
                ) : (
                  <div className={styles.cancelConfirmation} role="group" aria-label="Xác nhận hủy yêu cầu">
                    <p>Bạn có chắc muốn hủy yêu cầu này? Hành động này không thể hoàn tác.</p>
                    <div className={styles.cancelActions}>
                      <button type="button" className={styles.keepButton} disabled={cancelling} onClick={() => setConfirmingCancel(false)}>
                        Giữ yêu cầu
                      </button>
                      <button type="button" className={styles.confirmCancelButton} disabled={cancelling} onClick={handleCancel}>
                        {cancelling ? "Đang hủy…" : "Xác nhận hủy"}
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : null}
            {error ? <p className="form-alert" role="alert">{error}</p> : null}
          </section>
        </aside>

        <main className="need-detail__content">
          <NeedResponseList needId={needId} />
        </main>
      </div>
    </div>
  );
}
