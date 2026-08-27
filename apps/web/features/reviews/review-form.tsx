"use client";

import { FormEvent, useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { createReview } from "./review-api";
import styles from "./review-form.module.css";

export function ReviewForm({ orderItemId, onCreated }: { orderItemId: string; onCreated?(): void }) {
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState("");
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string>();
  const [idempotencyKey, setIdempotencyKey] = useState<string>();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || done) return;
    const key = idempotencyKey ?? crypto.randomUUID();
    if (!idempotencyKey) setIdempotencyKey(key);
    setBusy(true);
    setError(undefined);
    try {
      await createReview({ orderItemId, rating, comment: comment.trim() || undefined }, key);
      setDone(true);
      onCreated?.();
    } catch {
      setError("Chưa thể gửi đánh giá. Vui lòng thử lại.");
    } finally {
      setBusy(false);
    }
  }

  if (done) return <p className={`form-success ${styles.success}`} role="status">Cảm ơn bạn đã gửi đánh giá.</p>;

  return (
    <form className={`review-form ${styles.form}`} onSubmit={submit}>
      <fieldset className={styles.fieldset} disabled={busy}>
        <legend>Đánh giá giao dịch</legend>
        <p className={styles.hint}>Chọn từ 1 đến 5 sao</p>
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
