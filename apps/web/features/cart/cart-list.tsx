"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { getCart, removeCartItem, updateCartItem } from "./cart-api";
import { CartItemRow } from "./cart-item-row";

type Cart = components["schemas"]["CartResponse"];

export function CartList({ initialCarts }: { initialCarts: Cart[] }) {
  const [carts, setCarts] = useState(initialCarts);
  const [message, setMessage] = useState<string>();
  async function refresh(cartId: string) {
    const cart = await getCart(cartId);
    setCarts((current) => current.map((value) => value.id === cartId ? cart : value));
  }
  async function mutate(cart: Cart, itemId: string, quantity?: number) {
    if (!cart.id || cart.version == null) return;
    try {
      if (quantity == null) await removeCartItem(cart.id, itemId, cart.version);
      else await updateCartItem(cart.id, itemId, cart.version, { quantity });
      await refresh(cart.id);
    } catch {
      await refresh(cart.id).catch(() => undefined);
      setMessage("Giỏ hàng vừa thay đổi. Đã tải lại dữ liệu mới nhất.");
    }
  }
  if (!carts.length) return <section className="empty-state"><h1>Giỏ hàng trống</h1><p>Hãy chọn sản phẩm gần bạn để bắt đầu.</p></section>;
  return <section className="cart-list" aria-label="Giỏ hàng của bạn">
    {message && <p role="status">{message}</p>}
    {carts.map((cart) => <article key={cart.id} className="cart-card">
      <header><p className="eyebrow">{cart.cartType === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</p><h2>Điểm bán {cart.merchantLocationId}</h2></header>
      <ul>{cart.items?.map((item) => item.id && <CartItemRow key={item.id} item={item} cartType={cart.cartType}
        onQuantityChange={(quantity) => mutate(cart, item.id!, quantity)} onRemove={() => mutate(cart, item.id!)} />)}</ul>
    </article>)}
  </section>;
}
