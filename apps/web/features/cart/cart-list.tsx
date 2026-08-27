import Link from "next/link";
import type { components } from "@covie/api-client";
import styles from "./cart-list.module.css";

type Cart = components["schemas"]["CartResponse"];
const formatMoney = (amount: number) => `${new Intl.NumberFormat("vi-VN").format(amount)} ₫`;

export function CartList({ initialCarts }: { initialCarts: Cart[] }) {
  if (!initialCarts.length) return <section className={styles.empty}><h2>Giỏ hàng của bạn đang trống</h2><p>Khám phá sản phẩm, dịch vụ và đồ thanh lý gần bạn để bắt đầu.</p><Link href="/search">Khám phá gần bạn</Link></section>;

  return <section className={styles.content} aria-label="Giỏ hàng của bạn">
    <h2>Giỏ hàng của bạn</h2>
    <div className={styles.cards}>{initialCarts.map(cart => {
      const service = cart.cartType === "SERVICE";
      const count = (cart.items ?? []).reduce((sum, item) => sum + (item.quantity ?? 0), 0);
      const total = (cart.items ?? []).reduce((sum, item) => sum + (item.priceSnapshot ?? 0) * (item.quantity ?? 0), 0);
      const label = service ? "Dịch vụ" : "Sản phẩm";
      return <article key={cart.id ?? `${cart.merchantLocationId}-${cart.cartType}`} className={`${styles.card} ${service ? styles.service : ""}`}>
        <h3>Giỏ {label.toLocaleLowerCase("vi-VN")}</h3>
        <p><span>{count} {label.toLocaleLowerCase("vi-VN")}</span><strong>{formatMoney(total)}</strong></p>
        <span className={styles.type}>{label}</span>
        {cart.id && <Link href={`/cart/${encodeURIComponent(cart.id)}`}>{service ? "Xem giỏ dịch vụ" : "Xem và thanh toán giỏ này"}</Link>}
      </article>;
    })}</div>
    <p className={styles.note}>Mỗi giỏ được thanh toán riêng. Sản phẩm và dịch vụ được tách thành các giỏ khác nhau.</p>
  </section>;
}
