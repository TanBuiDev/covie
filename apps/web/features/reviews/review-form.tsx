"use client";

import { FormEvent, useState } from "react";
import { SealCheck, ShieldCheck, Star } from "@phosphor-icons/react";
import { createReview } from "./review-api";
import styles from "./review-form.module.css";

type ReviewInput = Parameters<typeof createReview>[0];
type SubmitResult = "done" | "stale" | "error";

export async function submitReviewIntent(
  input: ReviewInput,
  key: string,
  callbacks: { onCreated?(): void | Promise<void>; onStale?(): void | Promise<void> },
  submitReview: typeof createReview = createReview,
): Promise<SubmitResult> {
  try {
    await submitReview(input, key);
  } catch (cause) {
    const code = cause && typeof cause === "object" && "code" in cause ? String(cause.code) : "";
    if (code === "REVIEW_ALREADY_EXISTS" || code === "REVIEW_CREATION_DISABLED") {
      try {
        await callbacks.onStale?.();
        return "stale";
      } catch {
        return "error";
      }
    }
    return "error";
  }
  try {
    await callbacks.onCreated?.();
    return "done";
  } catch {
    return "error";
  }
}

const RATING_LABELS: Record<number, string> = {
  1: "Rất không hài lòng",
  2: "Chưa hài lòng",
  3: "Bình thường, tạm ổn",
  4: "Hài lòng, chất lượng tốt",
  5: "Rất hài lòng & Tuyệt vời",
};

const QUICK_TAGS = [
  "Giao hàng đúng hẹn",
  "Đóng gói cẩn thận",
  "Chất lượng tươi ngon",
  "Hàng xóm thân thiện",
  "Đúng như mô tả",
];

export function ReviewForm({ orderItemId, onCreated, onStale }: { orderItemId: string; onCreated?(): void | Promise<void>; onStale?(): void | Promise<void> }) {
  const [rating, setRating] = useState(5);
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string>();
  const [idempotencyKey, setIdempotencyKey] = useState<string>();

  const activeRating = hoverRating ?? rating;
  const ratingEmotion = RATING_LABELS[activeRating] ?? "";

  function toggleTag(tag: string) {
    const isSelected = selectedTags.includes(tag);
    if (isSelected) {
      setSelectedTags((prev) => prev.filter((t) => t !== tag));
      setComment((prev) =>
        prev
          .replace(tag, "")
          .replace(/,\s*,/g, ",")
          .replace(/\.\s*\./g, ".")
          .trim(),
      );
    } else {
      setSelectedTags((prev) => [...prev, tag]);
      setComment((prev) => {
        const trimmed = prev.trim();
        if (!trimmed) return tag;
        if (trimmed.includes(tag)) return trimmed;
        return `${trimmed}. ${tag}`;
      });
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || done) return;
    const key = idempotencyKey ?? crypto.randomUUID();
    if (!idempotencyKey) setIdempotencyKey(key);
    setBusy(true);
    setError(undefined);
    void submitReviewIntent(
      { orderItemId, rating, comment: comment.trim() || undefined },
      key,
      { onCreated, onStale },
    ).then((result) => {
      if (result === "done") setDone(true);
      if (result === "error") setError("Chưa thể gửi đánh giá. Vui lòng thử lại.");
    })
      .finally(() => setBusy(false));
  }

  if (done) {
    return (
      <div className={`review-done-card ${styles.success}`}>
        <SealCheck size={48} weight="fill" className="review-done-icon" />
        <p className={`form-success ${styles.successCopy}`} role="status">Cảm ơn bạn đã gửi đánh giá.</p>
        <small className="review-done-note">Đánh giá của bạn đã được ghi nhận và đóng góp vào điểm tin cậy của cộng đồng CoVie.</small>
      </div>
    );
  }

  return (
    <form className={`review-form ${styles.form}`} onSubmit={submit}>
      <fieldset className={styles.fieldset} disabled={busy}>
        <legend>Đánh giá giao dịch</legend>
        <p className={`review-rating-hint ${styles.hint}`}>Chọn từ 1 đến 5 sao</p>

        <div className="review-rating-selector">
          <div className={styles.stars} role="radiogroup" aria-label="Điểm đánh giá">
            {[1, 2, 3, 4, 5].map((value) => (
              <button
                className={`${styles.star} ${value > activeRating ? styles.starInactive : ""}`}
                type="button"
                role="radio"
                aria-checked={rating === value}
                aria-label={`${value} sao`}
                key={value}
                onMouseEnter={() => setHoverRating(value)}
                onMouseLeave={() => setHoverRating(null)}
                onClick={() => {
                  setRating(value);
                  setHoverRating(null);
                }}
              >
                <Star size={28} weight={value <= activeRating ? "fill" : "regular"} className="review-star-icon" />
              </button>
            ))}
          </div>
          <div className="review-emotion-label" aria-live="polite">
            <span className="review-emotion-score">{activeRating} sao</span>
            <span className="review-emotion-text">· {ratingEmotion}</span>
          </div>
        </div>

        <div className="review-quick-tags" aria-label="Gợi ý đánh giá nhanh">
          <span className="review-quick-tags-title">Gợi ý đánh giá nhanh:</span>
          <div className="review-quick-tags-list">
            {QUICK_TAGS.map((tag) => {
              const isSelected = selectedTags.includes(tag);
              return (
                <button
                  key={tag}
                  type="button"
                  onClick={() => toggleTag(tag)}
                  className={`review-tag-pill ${isSelected ? "review-tag-pill--active" : ""}`}
                >
                  {tag}
                </button>
              );
            })}
          </div>
        </div>

        <label className={styles.comment}>
          <div className="review-comment-header">
            <span>Nhận xét (không bắt buộc)</span>
            <span className="review-char-count">{comment.length} / 3000</span>
          </div>
          <textarea
            value={comment}
            maxLength={3000}
            placeholder="Chia sẻ trải nghiệm cụ thể về món hàng hoặc thái độ phục vụ của người bán..."
            onChange={(event) => setComment(event.target.value)}
          />
          <div className="review-trust-hint">
            <ShieldCheck size={16} weight="duotone" className="review-trust-icon" />
            <small>Đánh giá trung thực của bạn giúp xây dựng cộng đồng cư dân CoVie thêm tin cậy.</small>
          </div>
        </label>
        <button className={`button-primary ${styles.submit}`} type="submit">{busy ? "Đang gửi…" : "Gửi đánh giá"}</button>
      </fieldset>
      {error ? <p className="form-alert" role="alert">{error}</p> : null}
    </form>
  );
}
