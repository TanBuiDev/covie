"use client";

import { FormEvent, useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
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

export function ReviewForm({ orderItemId, onCreated, onStale }: { orderItemId: string; onCreated?(): void | Promise<void>; onStale?(): void | Promise<void> }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string>();
  const [idempotencyKey, setIdempotencyKey] = useState<string>();

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

  if (done) return <p className={`form-success ${styles.success}`} role="status">Cảm ơn bạn đã gửi đánh giá.</p>;

  return (
    <form className={`review-form ${styles.form}`} onSubmit={submit}>
      <fieldset className={styles.fieldset} disabled={busy}>
        <legend>Đánh giá giao dịch</legend>
        <p className={`review-rating-hint ${styles.hint}`}>Chọn từ 1 đến 5 sao</p>
        <div className={styles.stars} role="radiogroup" aria-label="Điểm đánh giá">
          {[1, 2, 3, 4, 5].map((value) => (
            <button
              className={`${styles.star} ${value > rating ? styles.starInactive : ""}`}
              type="button"
              role="radio"
              aria-checked={rating === value}
              aria-label={`${value} sao`}
              key={value}
              onClick={() => setRating(value)}
            >
              <CoVieIcon name="rating-star" />
            </button>
          ))}
        </div>
        <label className={styles.comment}>
          <span>Nhận xét (không bắt buộc)</span>
          <textarea value={comment} maxLength={3000} placeholder="Chia sẻ trải nghiệm của bạn..." onChange={(event) => setComment(event.target.value)} />
          <small>Không chia sẻ thông tin nhạy cảm hoặc riêng tư.</small>
        </label>
        <button className={`button-primary ${styles.submit}`} type="submit">{busy ? "Đang gửi…" : "Gửi đánh giá"}</button>
      </fieldset>
      {error ? <p className="form-alert" role="alert">{error}</p> : null}
    </form>
  );
}
