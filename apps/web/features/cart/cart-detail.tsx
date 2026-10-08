"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRef, useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { CovieApiError } from "@/lib/api/api-error";
import { ErrorState } from "@/components/ui/error-state";
import { CartItemRow } from "./cart-item-row";
import { getCart, removeCartItem, updateCartItem } from "./cart-api";
import styles from "./cart-detail.module.css";
import { CartSummary } from "./cart-summary";

type Cart = components["schemas"]["CartResponse"];

export function CartDetail({ initialCart }: { initialCart: Cart }) {
  return <CartDetailContent key={initialCart.id} initialCart={initialCart} />;
}

function CartDetailContent({ initialCart }: { initialCart: Cart }) {
  const [cart, setCart] = useState(initialCart);
  const [message, setMessage] = useState<string>();
  const [stale, setStale] = useState(false);
  const [busyItemId, setBusyItemId] = useState<string>();
  const mutationPending = useRef(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [authRequired, setAuthRequired] = useState(false);

  async function retryRefresh() {
    if (mutationPending.current || authRequired) return;
    mutationPending.current = true;
    setRefreshing(true);
    try {
      await refresh();
      setRefreshFailed(false);
      setStale(false);
      setMessage(undefined);
    } catch (error) {
      if (error instanceof CovieApiError && error.status === 401) setAuthRequired(true);
      setRefreshFailed(true);
    } finally {
      mutationPending.current = false;
      setRefreshing(false);
    }
  }

  async function refresh() {
    if (!cart.id) return;
    setCart(await getCart(cart.id));
  }

  async function mutate(itemId: string, quantity?: number) {
    if (!cart.id || cart.version == null || mutationPending.current || refreshFailed || authRequired) return;
    mutationPending.current = true;
    setBusyItemId(itemId);
    setMessage(undefined);
    setStale(false);

    try {
      let commandError: unknown;
      try {
        if (quantity == null) await removeCartItem(cart.id, itemId, cart.version);
        else await updateCartItem(cart.id, itemId, cart.version, { quantity });
      } catch (error) { commandError = error; }
      if (commandError instanceof CovieApiError && commandError.status === 401) {
        setAuthRequired(true);
        return;
      }
      try {
        await refresh();
        setRefreshFailed(false);
        if (commandError instanceof CovieApiError && commandError.code === "RESOURCE_VERSION_CONFLICT") setStale(true);
        else if (commandError) setMessage("Không thể cập nhật giỏ hàng. Hãy kiểm tra dữ liệu vừa được tải lại.");
      } catch (error) {
        if (error instanceof CovieApiError && error.status === 401) setAuthRequired(true);
        setRefreshFailed(true);
      }
    } finally {
      mutationPending.current = false;
      setBusyItemId(undefined);
    }
  }

  const items = cart.items ?? [];
  const title = cart.cartType === "SERVICE" ? "Giỏ dịch vụ" : "Giỏ sản phẩm";

  if (authRequired) return <ErrorState
    title="Vui lòng đăng nhập lại"
    description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
    action={<Link className="button-primary" href={`/login?returnTo=${encodeURIComponent(`/cart/${encodeURIComponent(cart.id ?? "")}`)}`}>Đăng nhập lại</Link>}
  />;

  return (
    <div className={`cart-detail-surface ${styles.screen}`}>
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
        {refreshFailed ? <div className={styles.errorMessage} role="alert">
          <p>Không thể tải dữ liệu giỏ hàng mới nhất. Vui lòng tải lại trước khi tiếp tục.</p>
          <button className="button-secondary" type="button" disabled={refreshing} onClick={() => void retryRefresh()}>Thử lại tải giỏ hàng</button>
        </div> : null}
        {items.length ? (
          <ul className={styles.items}>
            {items.map((item) => item.id ? (
              <CartItemRow
                key={item.id}
                item={item}
                cartType={cart.cartType}
                disabled={Boolean(busyItemId) || refreshFailed || refreshing}
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
        {items.length && !stale && !busyItemId && !refreshFailed && !refreshing ? <CartSummary cart={cart} /> : null}
        {stale ? <p className={styles.staleNote}>Dữ liệu mới nhất đã được tải lại. Hãy kiểm tra trước khi tiếp tục.</p> : null}
      </section>
    </div>
  );
}
