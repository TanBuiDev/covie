"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { commitCheckout, previewCheckout } from "./checkout-api";
import { CheckoutChangeDialog } from "./checkout-change-dialog";
import styles from "./checkout-preview.module.css";

type Cart = components["schemas"]["CartResponse"];
type Address = components["schemas"]["UserAddressResponse"];
type Preview = components["schemas"]["CheckoutPreviewResponse"];
type Method = "PICKUP" | "SELLER_DELIVERY" | "SERVICE";

function money(value?: number) {
  return value == null ? "—" : `${value.toLocaleString("vi-VN")} ₫`;
}

export function CheckoutSafeError() {
  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/cart" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Xác nhận đơn</h1>
      </header>
      <section className={styles.safeError} role="alert">
        <h2>Không thể tiếp tục đặt hàng</h2>
        <p>Nguồn đặt hàng không hợp lệ hoặc đã hết hiệu lực. Quay lại giỏ hàng hoặc nhu cầu để bắt đầu lại.</p>
        <Link href="/cart">Quay lại giỏ hàng</Link>
      </section>
    </div>
  );
}

export function CheckoutPreview({ carts, addresses, initialCartId }: { carts: Cart[]; addresses: Address[]; initialCartId?: string }) {
  const router = useRouter();
  const firstCart = carts.find((cart) => cart.id === initialCartId) ?? carts[0];
  const [cartId, setCartId] = useState(firstCart?.id ?? "");
  const [method, setMethod] = useState<Method>(firstCart?.cartType === "SERVICE" ? "SERVICE" : "PICKUP");
  const [addressId, setAddressId] = useState(addresses.find((address) => address.defaultAddress)?.id ?? "");
  const [preview, setPreview] = useState<Preview>();
  const [changed, setChanged] = useState(false);
  const [message, setMessage] = useState<string>();
  const selectedCart = carts.find((cart) => cart.id === cartId);
  const selectedAddress = addresses.find((address) => address.id === addressId);
  const isService = selectedCart?.cartType === "SERVICE";

  function chooseCart(nextId: string) {
    const next = carts.find((cart) => cart.id === nextId);
    setCartId(nextId);
    setMethod(next?.cartType === "SERVICE" ? "SERVICE" : "PICKUP");
    setPreview(undefined);
  }

  async function requestPreview() {
    if (!cartId) return;
    setMessage(undefined);
    try {
      setChanged(false);
      const value = await previewCheckout({
        sourceType: "CART",
        cartId,
        fulfillmentMethod: method,
        addressId: method === "PICKUP" ? undefined : addressId || undefined,
        paymentMethod: "COD",
      });
      setPreview(value);
    } catch {
      setMessage("Không thể tạo bản xem trước. Hãy kiểm tra lại giỏ hàng và địa chỉ.");
    }
  }

  async function commit() {
    if (!preview?.checkoutToken) return;
    try {
      const order = await commitCheckout(preview.checkoutToken, crypto.randomUUID());
      if (order.id) router.push(`/checkout/success/${order.id}`);
    } catch (error) {
      const code = typeof error === "object" && error && "code" in error ? String(error.code) : "";
      if (code === "CHECKOUT_CHANGED") {
        setPreview(undefined);
        setChanged(true);
      } else setMessage("Không thể xác nhận đơn hàng. Vui lòng thử lại.");
    }
  }

  const negotiated = preview?.shippingFee == null && preview?.fulfillmentMethod === "SELLER_DELIVERY";
  const totalLabel = negotiated ? "Khoản tiền đã biết" : "Tổng dự kiến";
  const totalValue = negotiated ? preview?.subtotal : preview?.total;

  if (!carts.length) return <CheckoutSafeError />;

  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href={cartId ? `/cart/${encodeURIComponent(cartId)}` : "/cart"} aria-label="Quay lại giỏ hàng"><CoVieIcon name="back" /></Link>
        <h1>Xác nhận đơn</h1>
      </header>
      <section className={styles.content}>
        <h1 className={styles.desktopTitle}>Xác nhận đơn</h1>
        {changed ? <CheckoutChangeDialog onPreviewAgain={requestPreview} /> : (
          <>
            {carts.length > 1 ? (
              <label className={styles.selectField}>Giỏ hàng
                <select value={cartId} onChange={(event) => chooseCart(event.target.value)}>
                  {carts.map((cart, index) => <option key={cart.id} value={cart.id}>{cart.cartType === "SERVICE" ? "Giỏ dịch vụ" : "Giỏ sản phẩm"} {index + 1}</option>)}
                </select>
              </label>
            ) : null}
            {method !== "PICKUP" ? (
              <section className={styles.addressSection}>
                <div><h2>Địa chỉ nhận hàng</h2><Link href="/me/addresses">Thay đổi</Link></div>
                {selectedAddress ? (
                  <p><strong>{selectedAddress.label ?? "Địa chỉ"}</strong> · {selectedAddress.addressText}<br />{selectedAddress.recipientName}{selectedAddress.phone ? ` · ${selectedAddress.phone}` : ""}</p>
                ) : (
                  <label>Địa chỉ
                    <select aria-label="Địa chỉ" value={addressId} onChange={(event) => { setAddressId(event.target.value); setPreview(undefined); }}>
                      <option value="">Chọn địa chỉ</option>
                      {addresses.map((address) => <option key={address.id} value={address.id}>{address.label} — {address.addressText}</option>)}
                    </select>
                  </label>
                )}
              </section>
            ) : null}
            <fieldset className={styles.fulfillment}>
              <legend>Nhận hàng</legend>
              {isService ? (
                <label className={styles.optionSelected}><input type="radio" aria-label="SERVICE" checked readOnly /><span><strong>Dịch vụ tại địa chỉ</strong><small>Người bán phối hợp lịch với bạn</small></span></label>
              ) : (
                <>
                  <label className={method === "SELLER_DELIVERY" ? styles.optionSelected : styles.option}><input type="radio" aria-label="SELLER_DELIVERY" checked={method === "SELLER_DELIVERY"} onChange={() => { setMethod("SELLER_DELIVERY"); setPreview(undefined); }} /><span><strong>Giao tận nơi</strong><small>Giao đến địa chỉ của bạn</small></span></label>
                  <label className={method === "PICKUP" ? styles.optionSelected : styles.option}><input type="radio" aria-label="PICKUP" checked={method === "PICKUP"} onChange={() => { setMethod("PICKUP"); setPreview(undefined); }} /><span><strong>Nhận tại điểm bán</strong><small>Tự đến điểm bán để nhận hàng</small></span></label>
                </>
              )}
            </fieldset>
            {preview ? (
              <>
                {preview.fulfillmentMethod === "SELLER_DELIVERY" ? (
                  <section className={styles.shipping}>
                    <div><strong>{negotiated ? "Phí giao hàng" : "Giao tiêu chuẩn"}</strong><strong>{negotiated ? "Thỏa thuận với người bán" : money(preview.shippingFee)}</strong></div>
                    <p>{negotiated ? "Người bán sẽ trao đổi chi phí giao phù hợp" : "Chi phí theo bản xem trước từ hệ thống"}</p>
                  </section>
                ) : null}
                <section className={styles.payment}><h2>Thanh toán</h2><label><input type="radio" checked readOnly /> COD</label><label><input type="radio" disabled /> Chuyển khoản — tự thỏa thuận</label></section>
                <section className={styles.summary} aria-live="polite">
                  <h2>Tóm tắt thanh toán</h2>
                  <dl><div><dt>Tạm tính</dt><dd>{money(preview.subtotal)}</dd></div><div><dt>Phí giao hàng</dt><dd>{negotiated ? "Thỏa thuận với người bán" : money(preview.shippingFee)}</dd></div><div><dt>{totalLabel}</dt><dd>{money(totalValue)}</dd></div></dl>
                  <p className={negotiated ? styles.negotiatedNote : undefined}>{negotiated ? "Phí giao sẽ được thỏa thuận; khoản tiền trên chưa phải tổng cuối cùng." : "Số tiền hiển thị theo bản xem trước từ hệ thống."}</p>
                  <button className={styles.desktopCommit} type="button" onClick={commit}>Đặt hàng</button>
                </section>
              </>
            ) : null}
            {message ? <p className={styles.error} role="alert">{message}</p> : null}
            {!preview ? <button className={styles.previewButton} type="button" onClick={requestPreview}>Xem trước</button> : null}
          </>
        )}
      </section>
      {preview && !changed ? <div className={styles.commitBar}><div><span>{totalLabel}</span><strong>{money(totalValue)}</strong></div><button type="button" onClick={commit}>Đặt hàng</button></div> : null}
    </div>
  );
}
