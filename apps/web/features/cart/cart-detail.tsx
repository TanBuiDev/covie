"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { CovieApiError } from "@/lib/api/api-error";
import { CartItemRow } from "./cart-item-row";
import { getCart, removeCartItem, updateCartItem } from "./cart-api";
import styles from "./cart-detail.module.css";
import { CartSummary } from "./cart-summary";

type Cart = components["schemas"]["CartResponse"];

export function CartDetail({ initialCart }: { initialCart: Cart }) {
  const [cart, setCart] = useState(initialCart);
  const [message, setMessage] = useState<string>();
  const [stale, setStale] = useState(false);
  const [busyItemId, setBusyItemId] = useState<string>();

  async function refresh() {
    if (!cart.id) return;
    setCart(await getCart(cart.id));
  }

  async function mutate(itemId: string, quantity?: number) {
    if (!cart.id || cart.version == null || busyItemId) return;
    setBusyItemId(itemId);
    setMessage(undefined);
    setStale(false);

    try {
      if (quantity == null) await removeCartItem(cart.id, itemId, cart.version);
      else await updateCartItem(cart.id, itemId, cart.version, { quantity });
      await refresh();
    } catch (error) {
      await refresh().catch(() => undefined);
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") setStale(true);
      else setMessage("Không thể cập nhật giỏ hàng. Dữ liệu mới nhất đã được tải lại khi có thể.");
    } finally {
      setBusyItemId(undefined);
    }
  }

  const items = cart.items ?? [];
  const title = cart.cartType === "SERVICE" ? "Giỏ dịch vụ" : "Giỏ sản phẩm";

  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/cart" aria-label="Quay lại danh sách giỏ hàng"><CoVieIcon name="back" /></Link>
        <h1 id="cart-detail-title">{stale ? "Giỏ hàng" : title}</h1>
      </header>
      <section className={styles.content} aria-labelledby="cart-detail-title">
        {stale ? (
          <div className={styles.warning} role="status">
            <CoVieIcon name="feedback-warning" />
            <div><strong>Giỏ hàng vừa thay đổi</strong><span>CoVie đã tải lại dữ liệu mới nhất. Hãy kiểm tra số lượng và giá trước khi tiếp tục.</span></div>
          </div>
        ) : null}
        {message ? <p className={styles.errorMessage} role="status">{message}</p> : null}
        {items.length ? (
          <ul className={styles.items}>
            {items.map((item) => item.id ? (
              <CartItemRow
                key={item.id}
                item={item}
                cartType={cart.cartType}
                disabled={busyItemId === item.id}
                onQuantityChange={(quantity) => mutate(item.id!, quantity)}
                onRemove={() => mutate(item.id!)}
              />
            ) : null)}
          </ul>
        ) : (
          <div className={styles.empty}>
            <h2>Giỏ này đang trống</h2>
            <p>Bạn có thể quay lại khám phá để chọn nội dung phù hợp gần mình.</p>
            <Link href="/search">Khám phá gần bạn</Link>
          </div>
        )}
        {items.length && !stale ? <CartSummary cart={cart} /> : null}
        {stale ? <p className={styles.staleNote}>Dữ liệu mới nhất đã được tải lại. Hãy kiểm tra trước khi tiếp tục.</p> : null}
      </section>
    </div>
  );
}
