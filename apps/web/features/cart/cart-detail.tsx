"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { CartItemRow } from "./cart-item-row";
import { getCart, removeCartItem, updateCartItem } from "./cart-api";
import { CartSummary } from "./cart-summary";

type Cart = components["schemas"]["CartResponse"];

export function CartDetail({ initialCart }: { initialCart: Cart }) {
  const [cart, setCart] = useState(initialCart);
  const [message, setMessage] = useState<string>();
  const [busyItemId, setBusyItemId] = useState<string>();

  async function refresh() {
    if (!cart.id) return;
    setCart(await getCart(cart.id));
  }

  async function mutate(itemId: string, quantity?: number) {
    if (!cart.id || cart.version == null || busyItemId) return;
    setBusyItemId(itemId);
    setMessage(undefined);

    try {
      if (quantity == null) {
        await removeCartItem(cart.id, itemId, cart.version);
      } else {
        await updateCartItem(cart.id, itemId, cart.version, { quantity });
      }
      await refresh();
    } catch (error) {
      await refresh().catch(() => undefined);
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Giỏ hàng đã thay đổi ở nơi khác. CoVie đã tải lại dữ liệu mới nhất để bạn kiểm tra.");
      } else {
        setMessage("Không thể cập nhật giỏ hàng. Dữ liệu mới nhất đã được tải lại khi có thể.");
      }
    } finally {
      setBusyItemId(undefined);
    }
  }

  const items = cart.items ?? [];

  return (
    <div className="cart-detail-layout">
      <section aria-labelledby="cart-detail-title">
        <header className="surface-header">
          <p className="eyebrow">{cart.cartType === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</p>
          <h1 id="cart-detail-title">Giỏ tại điểm bán {cart.merchantLocationId ?? "hiện tại"}</h1>
        </header>
        {message ? <p role="status">{message}</p> : null}
        {items.length ? (
          <ul className="cart-items">
            {items.map((item) =>
              item.id ? (
                <CartItemRow
                  key={item.id}
                  item={item}
                  cartType={cart.cartType}
                  disabled={busyItemId === item.id}
                  onQuantityChange={(quantity) => mutate(item.id!, quantity)}
                  onRemove={() => mutate(item.id!)}
                />
              ) : null,
            )}
          </ul>
        ) : (
          <div className="empty-state">
            <h2>Giỏ này đang trống</h2>
            <p>Bạn có thể quay lại khám phá để chọn nội dung phù hợp gần mình.</p>
          </div>
        )}
      </section>
      {items.length ? <CartSummary cart={cart} /> : null}
    </div>
  );
}
