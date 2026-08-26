import Link from "next/link";
import type { components } from "@covie/api-client";
import styles from "./cart-detail.module.css";

type Cart = components["schemas"]["CartResponse"];

function formatMoney(amount: number) {
  return `${amount.toLocaleString("vi-VN")} ₫`;
}

export function CartSummary({ cart }: { cart: Cart }) {
  const isService = cart.cartType === "SERVICE";
  const subtotal = (cart.items ?? []).reduce(
    (total, item) => total + (item.priceSnapshot ?? 0) * (item.quantity ?? 1),
    0,
  );

  return (
    <aside className={styles.summary} aria-label="Tóm tắt giỏ hàng">
      {isService ? (
        <>
          <p className={styles.serviceRule}>Số lượng: 1 (cố định). Quy mô dịch vụ đến từ lựa chọn dịch vụ, không dùng +/- trong giỏ.</p>
          <div className={styles.subtotal}>
            <strong>Tạm tính hiện tại: {formatMoney(subtotal)}</strong>
            <span>Giá sẽ được kiểm tra lại trước khi bạn đặt dịch vụ.</span>
          </div>
        </>
      ) : (
        <div className={styles.shippingNote}>Phí giao hàng sẽ được hiển thị khi bạn xác nhận đơn.</div>
      )}
      {cart.id ? (
        <Link className={styles.primaryAction} href={`/checkout?source=CART&cartId=${encodeURIComponent(cart.id)}`}>
          Tiếp tục đặt hàng
        </Link>
      ) : null}
    </aside>
  );
}
