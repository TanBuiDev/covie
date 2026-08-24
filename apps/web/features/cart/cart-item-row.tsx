"use client";

import type { components } from "@covie/api-client";

type CartItem = components["schemas"]["CartItemResponse"];

export function CartItemRow({ item, cartType, disabled = false, onQuantityChange, onRemove }: {
  item: CartItem;
  cartType?: "PRODUCT" | "SERVICE";
  disabled?: boolean;
  onQuantityChange(quantity: number): void;
  onRemove(): void;
}) {
  const quantity = item.quantity ?? 1;
  const isService = cartType === "SERVICE";

  const update = (next: number) => {
    const safe = isService ? 1 : Math.max(1, next);
    onQuantityChange(safe);
  };

  return (
    <li className="cart-item-row">
      <div>
        <strong>{item.itemName ?? (isService ? "Dịch vụ" : "Sản phẩm")}</strong>
        {item.priceSnapshot != null ? <small>{item.priceSnapshot.toLocaleString("vi-VN")} VND</small> : null}
      </div>
      <div className="cart-actions">
        {!isService ? (
          <>
            <button type="button" aria-label="Giảm số lượng" disabled={disabled || quantity <= 1} onClick={() => update(quantity - 1)}>−</button>
            <output aria-label="Số lượng">{quantity}</output>
            <button type="button" aria-label="Tăng số lượng" disabled={disabled} onClick={() => update(quantity + 1)}>+</button>
          </>
        ) : (
          <output aria-label="Số lượng">1</output>
        )}
        <button type="button" className="text-button" disabled={disabled} onClick={onRemove}>Xóa</button>
      </div>
    </li>
  );
}
