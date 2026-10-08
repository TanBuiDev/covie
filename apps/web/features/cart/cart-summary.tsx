import Link from "next/link";
import { ArrowRight, ShieldCheck, Sparkle } from "@phosphor-icons/react/dist/ssr";
import type { components } from "@covie/api-client";
import styles from "./cart-detail.module.css";

type Cart = components["schemas"]["CartResponse"];

function formatMoney(amount: number) {
  return `${amount.toLocaleString("vi-VN")} ₫`;
}

const FREESHIP_THRESHOLD = 150_000;

export function CartSummary({ cart }: { cart: Cart }) {
  const isService = cart.cartType === "SERVICE";
  const subtotal = (cart.items ?? []).reduce(
    (total, item) => total + (item.priceSnapshot ?? 0) * (item.quantity ?? 1),
    0,
  );

  const freeshipProgress = Math.min(100, Math.round((subtotal / FREESHIP_THRESHOLD) * 100));
  const remainingForFreeship = Math.max(0, FREESHIP_THRESHOLD - subtotal);

  return (
    <aside className={styles.summary} aria-label="Tóm tắt giỏ hàng">
      <h2 className={styles.summaryTitle}>Tóm tắt giỏ hàng</h2>

      {/* Dynamic Community Freeship Perk Bar (Only for Products) */}
      {!isService && (
        <div className={styles.freeshipWidget}>
          <div className={styles.freeshipHeader}>
            <Sparkle size={16} weight="fill" className={styles.freeshipIcon} />
            <span className={styles.freeshipText}>
              {subtotal >= FREESHIP_THRESHOLD
                ? "Đơn hàng đủ điều kiện nhận ưu đãi giao tận cửa!"
                : `Mua thêm ${remainingForFreeship.toLocaleString("vi-VN")} ₫ để nhận ưu đãi giao tận cửa!`}
            </span>
          </div>
          <div
            className={styles.freeshipTrack}
            role="progressbar"
            aria-valuenow={freeshipProgress}
            aria-valuemin={0}
            aria-valuemax={100}
          >
            <div
              className={styles.freeshipFill}
              style={{ width: `${freeshipProgress}%` }}
            />
          </div>
        </div>
      )}

      <p className={styles.serviceRule}>Giá và khả dụng sẽ được kiểm tra lại trước khi đặt hàng.</p>

      {isService ? (
        <>
          <p className={styles.serviceRule}>
            Số lượng: 1 (cố định). Quy mô dịch vụ đến từ lựa chọn dịch vụ, không dùng +/- trong giỏ.
          </p>
          <div className={styles.subtotal}>
            <strong>Tạm tính hiện tại: {formatMoney(subtotal)}</strong>
            <span>Giá sẽ được kiểm tra lại trước khi bạn đặt dịch vụ.</span>
          </div>
        </>
      ) : (
        <div className={styles.shippingNote}>
          Phí giao hàng sẽ được hiển thị khi bạn xác nhận đơn.
        </div>
      )}

      {cart.id ? (
        <Link
          className={styles.primaryAction}
          href={`/checkout?source=CART&cartId=${encodeURIComponent(cart.id)}`}
        >
          <span>Tiếp tục đặt hàng</span>
          <ArrowRight size={18} weight="bold" />
        </Link>
      ) : null}

      {/* Resident Trust Assurance */}
      <div className={styles.trustBadge}>
        <ShieldCheck size={18} weight="duotone" className={styles.trustIcon} />
        <span>Giao dịch an toàn &amp; trực tiếp giữa các cư dân CoVie</span>
      </div>
    </aside>
  );
}
