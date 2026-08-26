"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { getMyNeed, type NeedDetail as NeedDetailModel } from "./need-api";
import { NeedResponseList } from "./need-response-list";

const statusCopy = {
  ACTIVE: "Đang tìm",
  FULFILLED: "Đã đáp ứng",
  EXPIRED: "Đã hết hạn",
  CANCELLED: "Đã hủy",
} as const;

export function NeedDetail({ needId }: { needId: string }) {
  const [need, setNeed] = useState<NeedDetailModel | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    getMyNeed(needId)
      .then((value) => { if (active) setNeed(value); })
      .catch((cause) => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải nhu cầu."); });
    return () => { active = false; };
  }, [needId]);

  if (error && !need) return <p className="form-alert" role="alert">{error}</p>;
  if (!need) return <LoadingState label="Đang tải nhu cầu…" />;

  return (
    <div className="need-detail">
      <section className="need-detail__summary" aria-labelledby="need-detail-title">
        <div className="need-detail__title-row">
          <div>
            <p className="eyebrow">{need.type === "SERVICE" ? "Dịch vụ cần tìm" : "Sản phẩm cần tìm"}</p>
            <h1 id="need-detail-title">{need.title}</h1>
          </div>
          <span className={`need-status need-status--${need.status.toLowerCase()}`}>{statusCopy[need.status]}</span>
        </div>

        <p className="need-detail__description">{need.description || need.queryText}</p>
        <dl className="need-detail__facts">
          {need.type === "PRODUCT" ? <div><dt>Số lượng</dt><dd>{need.requestedQuantity.toLocaleString("vi-VN")}</dd></div> : null}
          <div><dt>Bán kính</dt><dd>{(need.radiusMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd></div>
          <div><dt>Phản hồi</dt><dd>{need.responseCount.toLocaleString("vi-VN")}</dd></div>
          <div><dt>Hết hạn</dt><dd>{new Date(need.expiresAt).toLocaleString("vi-VN")}</dd></div>
        </dl>

        {need.fulfilledOrderId ? <p className="need-detail__fulfilled">Đã tạo đơn đáp ứng: <Link href={`/orders/${need.fulfilledOrderId}`}>Xem đơn hàng</Link></p> : null}
        {error ? <p className="form-alert" role="alert">{error}</p> : null}
      </section>

      <NeedResponseList needId={needId} />
    </div>
  );
}
