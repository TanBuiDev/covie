"use client";

import { useId, useState } from "react";
import { useSocialDraftNavigation } from "./use-social-draft-navigation";

export function SocialCaptionField({ initialValue = "", disabled = false, onChange }: {
  initialValue?: string; disabled?: boolean; onChange?: (value: string) => void;
}) {
  const [caption, setCaption] = useState(initialValue);
  const descriptionId = useId();
  const dirty = caption !== initialValue;
  useSocialDraftNavigation(dirty);
  function change(value: string) { setCaption(value); onChange?.(value); }
  return <fieldset className="seller-attribute-fields" disabled={disabled}>
    <legend>Nội dung hiển thị trên Khám phá</legend>
    <label><span className="seller-field-title">Caption</span>
      <textarea name="socialCaption" value={caption} onChange={event => change(event.target.value)}
        maxLength={3000} rows={5} aria-describedby={descriptionId}
        placeholder="Chia sẻ nội dung giới thiệu; không thay thế mô tả thương mại." />
    </label>
    <p id={descriptionId} className="seller-contract-note">Caption riêng với mô tả thương mại; giữ xuống dòng, hiển thị dạng văn bản.</p>
    <output aria-label="Số ký tự caption" aria-live="polite">{caption.length} / 3000 UTF-16 units</output>
    {dirty && <p role="status" className="seller-contract-note">Caption chưa lưu.</p>}
    <div className="seller-actions">
      <button type="button" className="seller-button seller-button--secondary" disabled={disabled || !caption}
        onClick={() => change("")}>Xóa caption</button>
      <button type="button" className="seller-button seller-button--secondary" disabled={disabled || !dirty}
        onClick={() => change(initialValue)}>Hoàn nguyên caption</button>
    </div>
  </fieldset>;
}
