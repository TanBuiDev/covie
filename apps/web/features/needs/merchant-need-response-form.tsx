"use client";

import { FormEvent, useRef, useState } from "react";
import { formatMoney } from "@/lib/format/money";
import { respondToNeed, type NeedEligibleItem } from "./need-api";

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
  offers: NeedEligibleItem[];
  onCreated?: () => void;
}) {
  const intentKeyRef = useRef<string | undefined>(undefined);
  const [selection, setSelection] = useState<{ offers: NeedEligibleItem[]; ids: string[] }>(() => ({ offers, ids: [] }));
  const [note, setNote] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const selectable = offers.filter((offer): offer is NeedEligibleItem & { locationVariantId: string; available: true } =>
    offer.available === true && Boolean(offer.locationVariantId));
  if (selection.offers !== offers) {
    setSelection({ offers, ids: [] });
  }
  const availableIds = new Set(selectable.map((offer) => offer.locationVariantId));
  const validSelected = (selection.offers === offers ? selection.ids : []).filter((id) => availableIds.has(id));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    if (!validSelected.length || validSelected.length > maxItems) {
      setMessage(`Chọn từ 1 đến ${maxItems} nội dung phù hợp.`);
      return;
    }

    const key = intentKeyRef.current ?? crypto.randomUUID();
    intentKeyRef.current = key;
    setSubmitting(true);
    setMessage(null);
    try {
      await respondToNeed(needId, {
        merchantLocationId,
        locationVariantIds: validSelected,
        note: note.trim() || undefined,
      }, key);
      intentKeyRef.current = undefined;
      setMessage("Đã gửi phản hồi.");
      onCreated?.();
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Không thể gửi phản hồi. Vui lòng thử lại.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} aria-busy={submitting}>
      <fieldset disabled={submitting}>
        <legend>Chọn nội dung tại điểm bán</legend>
        {selectable.length ? selectable.map((offer) => (
          <label className="seller-need-offer" key={offer.locationVariantId}>
            <input
              type="checkbox"
              checked={validSelected.includes(offer.locationVariantId)}
              disabled={!validSelected.includes(offer.locationVariantId) && validSelected.length >= maxItems}
              onChange={(event) => {
                intentKeyRef.current = undefined;
                setSelection((current) => {
                  const stillAvailable = (current.offers === offers ? current.ids : []).filter((id) => availableIds.has(id));
                  return { offers, ids: event.target.checked
                    ? [...stillAvailable, offer.locationVariantId]
                    : stillAvailable.filter((id) => id !== offer.locationVariantId) };
                });
              }}
            />
            <span className="seller-need-offer__content">
              <strong>{offer.catalogItemName || "Nội dung"}</strong>
              {offer.variantName ? <small>{offer.variantName}</small> : null}
              {offer.currentPrice?.amount != null && offer.currentPrice.currency
                ? <span className="seller-need-offer__price">{formatMoney({ amount: offer.currentPrice.amount, currency: offer.currentPrice.currency })}</span>
                : null}
            </span>
          </label>
        )) : <p className="seller-muted">Chưa có nội dung phù hợp để chọn.</p>}
      </fieldset>
      <p className="seller-muted" role="status">Đã chọn {validSelected.length}/{maxItems}.</p>
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
      <button disabled={submitting || validSelected.length === 0} type="submit">{submitting ? "Đang gửi…" : "Phản hồi nhu cầu"}</button>
    </form>
  );
}
