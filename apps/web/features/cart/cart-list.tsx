import Link from "next/link";
import type { components } from "@covie/api-client";

type Cart = components["schemas"]["CartResponse"];

export function CartList({ initialCarts }: { initialCarts: Cart[] }) {
  if (!initialCarts.length) {
    return (
      <section className="empty-state">
        <h1>Giỏ hàng của bạn đang trống.</h1>
        <p>Mỗi điểm bán và loại nội dung sẽ có một giỏ riêng.</p>
        <Link href="/search">Khám phá gần bạn</Link>
      </section>
    );
  }

  return (
    <section className="cart-list" aria-label="Giỏ hàng của bạn">
      {initialCarts.map((cart) => {
        const itemCount = (cart.items ?? []).reduce((total, item) => total + (item.quantity ?? 0), 0);
        return (
          <article key={cart.id ?? `${cart.merchantLocationId}-${cart.cartType}`} className="cart-card">
            <header>
              <p className="eyebrow">{cart.cartType === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</p>
              <h2>Điểm bán {cart.merchantLocationId ?? "hiện tại"}</h2>
            </header>
            <p>{itemCount} mục</p>
            <ul className="compact-list">
              {(cart.items ?? []).slice(0, 3).map((item) => (
                <li key={item.id ?? item.locationVariantId}>{item.itemName ?? "Nội dung"}</li>
              ))}
            </ul>
            {cart.id ? <Link href={`/cart/${encodeURIComponent(cart.id)}`}>Xem giỏ này</Link> : null}
          </article>
        );
      })}
    </section>
  );
}
