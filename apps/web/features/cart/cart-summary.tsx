import Link from "next/link";
import type { components } from "@covie/api-client";

type Cart = components["schemas"]["CartResponse"];

export function CartSummary({ cart }: { cart: Cart }) {
  const itemCount = (cart.items ?? []).reduce((total, item) => total + (item.quantity ?? 0), 0);

  return (
    <aside className="cart-summary" aria-label="Tóm tắt giỏ hàng">
      <h2>Tóm tắt</h2>
      <p>{itemCount} mục trong giỏ này.</p>
      <p className="muted-copy">
        Giá, phí giao và tổng cuối cùng sẽ được backend xác nhận lại ở bước xem trước đơn hàng.
      </p>
      {cart.id ? (
        <Link className="primary-link" href={`/checkout?source=CART&cartId=${encodeURIComponent(cart.id)}`}>
          Tiếp tục đặt hàng
        </Link>
      ) : null}
    </aside>
  );
}
