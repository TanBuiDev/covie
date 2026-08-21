"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";

type CartItem = components["schemas"]["CartItemResponse"];

export function CartItemRow({ item, cartType, onQuantityChange, onRemove }: {
  item: CartItem;
  cartType?: "PRODUCT" | "SERVICE";
  onQuantityChange(quantity: number): void;
  onRemove(): void;
}) {
  const [quantity, setQuantity] = useState(item.quantity ?? 1);
  const isService = cartType === "SERVICE";
  const update = (next: number) => {
    const safe = isService ? 1 : Math.max(1, next);
    setQuantity(safe);
    onQuantityChange(safe);
  };

  return <li className="cart-item-row">
    <div><strong>{item.itemName ?? "Sản phẩm"}</strong><small>{item.priceSnapshot?.toLocaleString("vi-VN")} VND</small></div>
    <div className="cart-actions">
      <button type="button" aria-label="Giảm số lượng" disabled={isService || quantity <= 1} onClick={() => update(quantity - 1)}>−</button>
      <output aria-label="Số lượng">{quantity}</output>
      <button type="button" aria-label="Tăng số lượng" disabled={isService} onClick={() => update(quantity + 1)}>+</button>
      <button type="button" className="text-button" onClick={onRemove}>Xóa</button>
    </div>
  </li>;
}
