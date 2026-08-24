"use client";

import { FormEvent, useState } from "react";
import { respondToNeed } from "./need-api";

export function MerchantNeedResponseForm({
  needId,
  defaultLocationId,
  maxItems,
  onCreated,
}: {
  needId: string;
  defaultLocationId: string;
  maxItems: number;
  onCreated?: () => void;
}) {
  const [merchantLocationId, setMerchantLocationId] = useState(defaultLocationId);
  const [variantIds, setVariantIds] = useState("");
  const [note, setNote] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const locationVariantIds = variantIds.split(/[\s,]+/).map((value) => value.trim()).filter(Boolean);
    if (!locationVariantIds.length || locationVariantIds.length > maxItems) {
      setMessage(`Chọn từ 1 đến ${maxItems} offer.`);
      return;
    }
    setSubmitting(true);
    setMessage(null);
    try {
      await respondToNeed(needId, { merchantLocationId, locationVariantIds, note: note || undefined });
      setMessage("Đã gửi phản hồi.");
      onCreated?.();
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Không thể gửi phản hồi.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} style={{ display: "grid", gap: 8, marginTop: 12 }}>
      <label>
        Merchant location ID
        <input required value={merchantLocationId} onChange={(event) => setMerchantLocationId(event.target.value)} />
      </label>
      <label>
        Location variant IDs (phân cách bằng dấu phẩy)
        <textarea required value={variantIds} onChange={(event) => setVariantIds(event.target.value)} />
      </label>
      <label>
        Ghi chú
        <textarea maxLength={2000} value={note} onChange={(event) => setNote(event.target.value)} />
      </label>
      {message && <p role="status">{message}</p>}
      <button disabled={submitting} type="submit">{submitting ? "Đang gửi…" : "Phản hồi nhu cầu"}</button>
    </form>
  );
}
