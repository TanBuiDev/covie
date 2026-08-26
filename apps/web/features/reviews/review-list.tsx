"use client";
/* eslint-disable react-hooks/set-state-in-effect */

import type { components } from "@covie/api-client";
import { useEffect, useState } from "react";
import { ReportButton } from "@/features/reports/report-button";
import { listCatalogReviews, listMerchantReviews, type PublicReview } from "./review-api";

type ReviewPage = components["schemas"]["ReviewPageResponse"];

type ReviewListProps = {
  targetId: string;
  targetType: "CATALOG_ITEM" | "MERCHANT";
  contextMerchantId?: string;
  initialPage?: ReviewPage;
};

function starLabel(rating?: number) {
  if (rating == null) return "Chưa có điểm";
  const safe = Math.max(0, Math.min(5, Math.round(rating)));
  return `${"★".repeat(safe)}${"☆".repeat(5 - safe)}`;
}

export function ReviewList({ targetId, targetType, contextMerchantId, initialPage }: ReviewListProps) {
  const [items, setItems] = useState<PublicReview[]>(initialPage?.items ?? []);
  const [cursor, setCursor] = useState<string | undefined>(initialPage?.page?.nextCursor);
  const [loading, setLoading] = useState(!initialPage);
  const [error, setError] = useState<string>();

  async function fetchPage(next?: string) {
    return targetType === "MERCHANT"
      ? listMerchantReviews(targetId, next)
      : listCatalogReviews(targetId, next);
  }

  async function load(next?: string) {
    setLoading(true);
    setError(undefined);
    try {
      const page = await fetchPage(next);
      setItems((current) => next ? [...current, ...(page.items ?? [])] : (page.items ?? []));
      setCursor(page.page?.nextCursor);
    } catch {
      setError("Không thể tải đánh giá.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (initialPage) return;
    void load();
    // target changes remount at route level; avoid duplicate request from function identity.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [targetId, targetType]);

  const rating = initialPage?.rating;

  return (
    <section className="review-list" aria-labelledby={`review-list-title-${targetId}`}>
      <header className="review-list__header">
        <div>
          <h2 id={`review-list-title-${targetId}`}>Đánh giá từ người mua</h2>
          {rating?.reviewCount ? <p>{rating.averageRating?.toLocaleString("vi-VN") ?? "—"}/5 · {rating.reviewCount.toLocaleString("vi-VN")} đánh giá</p> : null}
        </div>
      </header>

      {error ? <p className="form-alert" role="alert">{error}</p> : null}
      {loading && !items.length ? <p className="review-list__loading">Đang tải đánh giá…</p> : null}
      {!loading && !items.length && !error ? <p className="review-list__empty">Chưa có đánh giá công khai.</p> : null}

      <div className="review-list__items">
        {items.map((review) => (
          <article className="review-card" key={review.id}>
            <div className="review-card__topline">
              <div>
                <strong className="review-card__stars" aria-label={review.rating != null ? `${review.rating} trên 5 sao` : "Chưa có điểm đánh giá"}>{starLabel(review.rating)}</strong>
                <span>{review.reviewer?.displayName ?? "Người mua CoVie"}</span>
              </div>
              {review.id ? <ReportButton targetType="REVIEW" targetId={review.id} contextMerchantId={contextMerchantId} label="Báo cáo đánh giá" /> : null}
            </div>
            {review.comment ? <p>{review.comment}</p> : <p className="review-card__muted">Người mua không để lại bình luận.</p>}
            <small>{review.verifiedPurchase ? "Đã mua hàng" : "Đánh giá công khai"}{review.createdAt ? ` · ${new Date(review.createdAt).toLocaleDateString("vi-VN")}` : ""}</small>
          </article>
        ))}
      </div>

      {cursor ? (
        <button className="button-secondary review-list__more" type="button" disabled={loading} onClick={() => void load(cursor)}>
          {loading ? "Đang tải…" : "Xem thêm đánh giá"}
        </button>
      ) : null}
    </section>
  );
}
