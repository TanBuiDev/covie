"use client";

import { CoVieIcon } from "./covie-icon";
import styles from "./quantity-stepper.module.css";

/** Product quantity control; inventory eligibility remains owned by the API. */
export function QuantityStepper({ quantity, disabled = false, onChange }: {
  quantity: number;
  disabled?: boolean;
  onChange(quantity: number): void;
}) {
  return <div className={styles.stepper}>
    <button type="button" aria-label="Giảm số lượng" disabled={disabled || quantity <= 1} onClick={()=>onChange(quantity-1)}><CoVieIcon name="minus"/></button>
    <output aria-label="Số lượng" aria-live="polite">{quantity.toLocaleString("vi-VN")}</output>
    <button type="button" aria-label="Tăng số lượng" disabled={disabled || quantity >= Number.MAX_SAFE_INTEGER} onClick={()=>onChange(quantity+1)}><CoVieIcon name="plus"/></button>
  </div>;
}
