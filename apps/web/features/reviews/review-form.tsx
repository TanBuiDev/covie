"use client";

import { FormEvent, useState } from "react";
import { createReview } from "./review-api";

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

  if (done) return <p className="form-success" role="status">Cảm ơn bạn đã gửi đánh giá.</p>;

  return (
    <form className="review-form" onSubmit={submit}>
      <fieldset disabled={busy}>
        <legend>Đánh giá giao dịch</legend>
        <label>
          <span>Điểm</span>
          <select aria-label="Điểm đánh giá" value={rating} onChange={(event) => setRating(Number(event.target.value))}>
            {[5, 4, 3, 2, 1].map((value) => <option key={value} value={value}>{value} sao</option>)}
          </select>
        </label>
        <label>
          <span>Chia sẻ thêm (không bắt buộc)</span>
          <textarea value={comment} maxLength={3000} onChange={(event) => setComment(event.target.value)} />
        </label>
        <button className="button-primary" type="submit">{busy ? "Đang gửi…" : "Gửi đánh giá"}</button>
      </fieldset>
      {error ? <p className="form-alert" role="alert">{error}</p> : null}
    </form>
  );
}
