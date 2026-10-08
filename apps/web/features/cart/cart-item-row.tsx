"use client";

import type { components } from "@covie/api-client";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./cart-detail.module.css";

type CartItem = components["schemas"]["CartItemResponse"];

function formatMoney(amount: number) {
  return `${amount.toLocaleString("vi-VN")} ₫`;
}

export function CartItemRow({ item, cartType, disabled = false, onQuantityChange, onRemove }: {
  item: CartItem;
  cartType?: "PRODUCT" | "SERVICE";
  disabled?: boolean;
  onQuantityChange(quantity: number): void;
  onRemove(): void;
}) {
  const quantity = item.quantity ?? 1;
  const unitPrice = item.priceSnapshot ?? 0;
  const isService = cartType === "SERVICE";
  const update = (next: number) => onQuantityChange(isService ? 1 : Math.max(1, next));

  return (
    <li className={styles.item}>
      <div className={styles.thumbnail} aria-hidden="true" />
      <div className={styles.itemBody}>
        <strong className={styles.itemName}>{item.itemName ?? (isService ? "Dịch vụ" : "Sản phẩm")}</strong>
        <span className={styles.variant}>{isService ? "Dịch vụ tại địa chỉ" : "Sản phẩm đã chọn"}</span>
        <div className={styles.prices}>
          <span>{formatMoney(unitPrice)}</span>
          <strong>{formatMoney(unitPrice * quantity)}</strong>
        </div>
        <div className={styles.actions}>
          {isService ? (
            <span aria-label="Số lượng">Số lượng: 1 (cố định)</span>
          ) : (
            <div className={styles.stepper}>
              <button type="button" aria-label="Giảm số lượng" disabled={disabled || quantity <= 1} onClick={() => update(quantity - 1)}>
                <CoVieIcon name="minus" className={styles.stepperIcon} />
              </button>
              <output aria-label="Số lượng">{quantity}</output>
              <button type="button" aria-label="Tăng số lượng" disabled={disabled} onClick={() => update(quantity + 1)}>
                <CoVieIcon name="plus" className={styles.stepperIcon} />
              </button>
            </div>
          )}
          <button type="button" className={styles.remove} disabled={disabled} onClick={onRemove}>Xóa</button>
        </div>
      </div>
    </li>
  );
}
