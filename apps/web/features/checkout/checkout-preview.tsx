"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { useRouter } from "next/navigation";
import { commitCheckout, previewCheckout } from "./checkout-api";
import { CheckoutChangeDialog } from "./checkout-change-dialog";

type Cart = components["schemas"]["CartResponse"];
type Address = components["schemas"]["UserAddressResponse"];
type Preview = components["schemas"]["CheckoutPreviewResponse"];

export function CheckoutPreview({ carts, addresses }: { carts: Cart[]; addresses: Address[] }) {
  const router = useRouter();
  const [cartId, setCartId] = useState(carts[0]?.id ?? "");
  const [method, setMethod] = useState<"PICKUP" | "SELLER_DELIVERY" | "SERVICE">("PICKUP");
  const [addressId, setAddressId] = useState("");
  const [preview, setPreview] = useState<Preview>();
  const [changed, setChanged] = useState(false);
  const [message, setMessage] = useState<string>();

  async function requestPreview() {
    if (!cartId) return;
    try {
      setChanged(false);
      const value = await previewCheckout({ sourceType: "CART", cartId, fulfillmentMethod: method,
        addressId: method === "PICKUP" ? undefined : addressId || undefined, paymentMethod: "COD" });
      setPreview(value);
    } catch { setMessage("Không thể tạo bản xem trước. Hãy kiểm tra lại giỏ hàng và địa chỉ."); }
  }
  async function commit() {
    if (!preview?.checkoutToken) return;
    try {
      const order = await commitCheckout(preview.checkoutToken, crypto.randomUUID());
      if (order.id) router.push(`/checkout/success/${order.id}`);
    } catch (error) {
      const code = typeof error === "object" && error && "code" in error ? String(error.code) : "";
      if (code === "CHECKOUT_CHANGED") { setPreview(undefined); setChanged(true); }
      else setMessage("Không thể xác nhận đơn hàng. Vui lòng thử lại.");
    }
  }
  const negotiated = preview?.shippingFee == null && preview?.fulfillmentMethod === "SELLER_DELIVERY";
  return <section className="checkout-preview">
    <label>Giỏ hàng <select value={cartId} onChange={(event) => { setCartId(event.target.value); setPreview(undefined); }}>
      {carts.map((cart) => <option key={cart.id} value={cart.id}>{cart.cartType} · {cart.merchantLocationId}</option>)}</select></label>
    <fieldset><legend>Nhận hàng</legend>{(["PICKUP", "SELLER_DELIVERY", "SERVICE"] as const).map((value) =>
      <label key={value}><input type="radio" checked={method === value} onChange={() => { setMethod(value); setPreview(undefined); }} /> {value}</label>)}</fieldset>
    {method !== "PICKUP" && <label>Địa chỉ <select value={addressId} onChange={(event) => { setAddressId(event.target.value); setPreview(undefined); }}><option value="">Chọn địa chỉ</option>{addresses.map((address) => <option key={address.id} value={address.id}>{address.label} — {address.addressText}</option>)}</select></label>}
    <button type="button" onClick={requestPreview}>Xem trước</button>
    {message && <p role="alert">{message}</p>}
    {changed && <CheckoutChangeDialog onPreviewAgain={requestPreview} />}
    {preview && <section aria-live="polite"><h2>Xem trước đơn hàng</h2><dl><dt>Tạm tính</dt><dd>{preview.subtotal?.toLocaleString("vi-VN")} {preview.currency}</dd><dt>Giao hàng</dt><dd>{negotiated ? "Phí giao hàng thỏa thuận" : `${preview.shippingFee?.toLocaleString("vi-VN")} ${preview.currency}`}</dd><dt>Tổng</dt><dd>{negotiated ? "Sẽ thỏa thuận với người bán" : `${preview.total?.toLocaleString("vi-VN")} ${preview.currency}`}</dd></dl><button type="button" onClick={commit}>Xác nhận đặt hàng</button></section>}
  </section>;
}
