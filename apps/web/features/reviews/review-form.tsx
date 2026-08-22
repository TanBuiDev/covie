"use client";
import { useState } from "react";
import { createReview } from "./review-api";
export function ReviewForm({ orderItemId, onCreated }: { orderItemId: string; onCreated?(): void }) {
  const [rating, setRating] = useState(5); const [comment, setComment] = useState(""); const [busy, setBusy] = useState(false); const [done, setDone] = useState(false); const [error, setError] = useState<string>();
  async function submit(event: React.FormEvent) { event.preventDefault(); if (busy || done) return; setBusy(true); setError(undefined); try { await createReview({ orderItemId, rating, comment: comment.trim() || undefined }, crypto.randomUUID()); setDone(true); onCreated?.(); } catch { setError("Chưa thể gửi đánh giá. Vui lòng thử lại."); } finally { setBusy(false); } }
  if (done) return <p role="status">Cảm ơn bạn đã gửi đánh giá.</p>;
  return <form className="review-form" onSubmit={submit}><fieldset disabled={busy}><legend>Đánh giá giao dịch</legend><label>Điểm <select aria-label="Điểm đánh giá" value={rating} onChange={(e) => setRating(Number(e.target.value))}>{[5,4,3,2,1].map((value) => <option key={value} value={value}>{value} sao</option>)}</select></label><label>Chia sẻ thêm (không bắt buộc)<textarea value={comment} maxLength={2000} onChange={(e) => setComment(e.target.value)} /></label><button type="submit">{busy ? "Đang gửi…" : "Gửi đánh giá"}</button></fieldset>{error && <p role="alert">{error}</p>}</form>;
}
