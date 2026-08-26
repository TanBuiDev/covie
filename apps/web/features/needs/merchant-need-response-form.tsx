"use client";

import { FormEvent, useRef, useState } from "react";
import { respondToNeed } from "./need-api";

export type NeedOfferOption = {
  locationVariantId: string;
  label: string;
  available?: boolean;
  actionable?: boolean;
};

export function MerchantNeedResponseForm({
  needId,
  merchantLocationId,
  maxItems,
  offers,
  onCreated,
}: {
  needId: string;
  merchantLocationId: string;
  maxItems: number;
  offers: NeedOfferOption[];
  onCreated?: () => void;
}) {
  const intentKeyRef = useRef<string | undefined>(undefined);
  const [selected, setSelected] = useState<string[]>([]);
  const [note, setNote] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectable = offers.filter((offer) => offer.available !== false && offer.actionable !== false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    if (!selected.length || selected.length > maxItems) {
      setMessage(`Chọn từ 1 đến ${maxItems} offer backend cho phép.`);
      return;
    }

    const key = intentKeyRef.current ?? crypto.randomUUID();
    intentKeyRef.current = key;
    setSubmitting(true);
    setMessage(null);
    try {
      await respondToNeed(needId, {
        merchantLocationId,
        locationVariantIds: selected,
        note: note.trim() || undefined,
      }, key);
      intentKeyRef.current = undefined;
      setMessage("Đã gửi phản hồi.");
      onCreated?.();
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Không thể gửi phản hồi. Bạn có thể thử lại cùng intent.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} aria-busy={submitting}>
      <fieldset disabled={submitting}>
        <legend>Chọn offer tại điểm bán</legend>
        {selectable.length ? selectable.map((offer) => (
          <label key={offer.locationVariantId}>
            <input
              type="checkbox"
              checked={selected.includes(offer.locationVariantId)}
              disabled={!selected.includes(offer.locationVariantId) && selected.length >= maxItems}
              onChange={(event) => {
                intentKeyRef.current = undefined;
                setSelected((current) => event.target.checked
                  ? [...current, offer.locationVariantId]
                  : current.filter((id) => id !== offer.locationVariantId));
              }}
            />
            {offer.label}
          </label>
        )) : <p className="seller-muted">Backend chưa cung cấp offer có thể chọn.</p>}
      </fieldset>
      <p className="seller-muted">Đã chọn {selected.length}/{maxItems}. Không có trường nhập giá tự do.</p>
      <label>
        Ghi chú
        <textarea
          maxLength={2000}
          value={note}
          disabled={submitting}
          onChange={(event) => {
            intentKeyRef.current = undefined;
            setNote(event.target.value);
          }}
        />
      </label>
      {message && <p role="status">{message}</p>}
      <button disabled={submitting || selected.length === 0} type="submit">{submitting ? "Đang gửi…" : "Phản hồi nhu cầu"}</button>
    </form>
  );
}
