"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import {
  ChatCircleText,
  Check,
  House,
  ShieldCheck,
} from "@phosphor-icons/react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { QuantityStepper } from "@/components/ui/quantity-stepper";
import { commitCheckout, previewCheckout } from "./checkout-api";
import { CheckoutChangeDialog } from "./checkout-change-dialog";
import styles from "./checkout-preview.module.css";
import type { DirectCheckoutSelection, NeedCheckoutSelection } from "./checkout-source";

type Cart = components["schemas"]["CartResponse"];
type Address = components["schemas"]["UserAddressResponse"];
type Preview = components["schemas"]["CheckoutPreviewResponse"];
type Method = "PICKUP" | "SELLER_DELIVERY" | "SERVICE";

function money(value?: number) {
  return value == null ? "—" : `${value.toLocaleString("vi-VN")} ₫`;
}

export function CheckoutSafeError({ retry }: { retry?: () => void } = {}) {
  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/cart" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Xác nhận đơn</h1>
      </header>
      <section className={styles.safeError} role="alert">
        <h2>{retry ? "Không thể tải thông tin đặt hàng" : "Không thể tiếp tục đặt hàng"}</h2>
        <p>{retry ? "Chưa tải được thông tin để xác nhận đơn. Vui lòng thử lại." : "Nguồn đặt hàng không hợp lệ hoặc đã hết hiệu lực. Quay lại giỏ hàng hoặc nhu cầu để bắt đầu lại."}</p>
        {retry ? <button className={styles.previewButton} type="button" onClick={retry}>Thử lại</button> : null}
        <Link href="/cart">Quay lại giỏ hàng</Link>
      </section>
    </div>
  );
}

export function CheckoutPreview({ carts, addresses, initialCartId, needSelection, directSelection, initialAddressId }: {
  carts: Cart[]; addresses: Address[]; initialCartId?: string; needSelection?: NeedCheckoutSelection; directSelection?: DirectCheckoutSelection; initialAddressId?: string;
}) {
  const router = useRouter();
  const selection = needSelection ?? directSelection;
  const [quantity, setQuantity] = useState(selection?.type === "SERVICE" || selection?.fixedQuantity ? 1 : selection?.quantity ?? 1);
  const firstCart = carts.find((cart) => cart.id === initialCartId) ?? carts[0];
  const [cartId, setCartId] = useState(firstCart?.id ?? "");
  const [method, setMethod] = useState<Method>(selection?.type === "SERVICE" || firstCart?.cartType === "SERVICE" ? "SERVICE" : initialAddressId ? "SELLER_DELIVERY" : "PICKUP");
  const [addressId, setAddressId] = useState(addresses.find(address => address.id === initialAddressId)?.id ?? addresses.find((address) => address.defaultAddress)?.id ?? "");
  const [preview, setPreview] = useState<Preview>();
  const [addressEditing, setAddressEditing] = useState(false);
  const [orderNote, setOrderNote] = useState("");
  const [commitKey, setCommitKey] = useState<string>();
  const [committing, setCommitting] = useState(false);
  const commitPending = useRef(false);
  const [changed, setChanged] = useState(false);
  const [message, setMessage] = useState<string>();
  const [previewing, setPreviewing] = useState(false);
  const previewGeneration = useRef(0);
  const previewPending = useRef(false);
  useEffect(() => () => { previewGeneration.current = -1; }, []);
  const selectedCart = carts.find((cart) => cart.id === cartId);
  const selectedAddress = addresses.find((address) => address.id === addressId);
  const isService = selection ? selection.type === "SERVICE" : selectedCart?.cartType === "SERVICE";

  function invalidatePreview() {
    previewGeneration.current++;
    previewPending.current = false;
    setPreviewing(false);
    setPreview(undefined);
    setCommitKey(undefined);
  }

  function chooseCart(nextId: string) {
    if (commitPending.current) return;
    const next = carts.find((cart) => cart.id === nextId);
    setCartId(nextId);
    setMethod(next?.cartType === "SERVICE" ? "SERVICE" : "PICKUP");
    invalidatePreview();
  }

  function changeQuantity(next: number) {
    if (committing || !selection || selection.type === "SERVICE" || selection.fixedQuantity || !Number.isSafeInteger(next) || next < 1) return;
    setQuantity(next);
    setMessage(undefined);
    invalidatePreview();
  }

  async function requestPreview() {
    if ((!cartId && !selection) || commitPending.current || previewPending.current || (method !== "PICKUP" && !selectedAddress)) return;
    const generation = ++previewGeneration.current;
    previewPending.current = true;
    setPreviewing(true);
    setMessage(undefined);
    try {
      setChanged(false);
      const value = await previewCheckout(selection ? {
        source: needSelection ? {type:"NEED_REQUEST", needRequestId:needSelection.needRequestId, needResponseItemId:needSelection.needResponseItemId} : {type:"DIRECT"},
        items:[{locationVariantId:selection.locationVariantId,quantity}],
        fulfillmentMethod:method,
        addressId:method === "PICKUP" ? undefined : addressId || undefined,
        paymentMethod:"COD",
      } : {
        sourceType: "CART",
        cartId,
        fulfillmentMethod: method,
        addressId: method === "PICKUP" ? undefined : addressId || undefined,
        paymentMethod: "COD",
      });
      if (generation !== previewGeneration.current) return;
      if (!value.checkoutToken) {
        setMessage("Chưa nhận được bản xem trước hợp lệ. Vui lòng thử lại.");
        return;
      }
      setPreview(value);
      setCommitKey(crypto.randomUUID());
    } catch {
      if (generation === previewGeneration.current) setMessage("Không thể tạo bản xem trước. Hãy kiểm tra lại món đã chọn và địa chỉ.");
    } finally {
      if (generation === previewGeneration.current) {
        previewPending.current = false;
        setPreviewing(false);
      }
    }
  }

  useEffect(() => {
    if (typeof window !== "undefined" && process.env.NODE_ENV !== "test") {
      if (!preview && !previewPending.current && (method === "PICKUP" || selectedAddress)) {
        void requestPreview();
      }
    }
  }, [method, selectedAddress, cartId, quantity, preview]);

  async function commit() {
    if (!preview?.checkoutToken || !commitKey || commitPending.current) return;
    commitPending.current = true;
    setCommitting(true);
    setMessage(undefined);
    try {
      const order = await commitCheckout(preview.checkoutToken, commitKey);
      if (order.id) {
        router.push(`/checkout/success/${order.id}`);
        return;
      }
      setMessage("Đơn hàng đã được xử lý nhưng chưa nhận được mã đơn. Vui lòng kiểm tra danh sách đơn hàng.");
    } catch (error) {
      const code = typeof error === "object" && error && "code" in error ? String(error.code) : "";
      if (code === "CHECKOUT_CHANGED") {
        invalidatePreview();
        setChanged(true);
      } else {
        setMessage("Không thể xác nhận đơn hàng. Vui lòng thử lại.");
      }
    } finally {
      commitPending.current = false;
      setCommitting(false);
    }
  }

  const negotiated = preview?.shippingFee == null && preview?.fulfillmentMethod === "SELLER_DELIVERY";
  const totalLabel = negotiated ? "Khoản tiền đã biết" : "Tổng dự kiến";
  const totalValue = negotiated ? preview?.subtotal : preview?.total;

  if (!carts.length && !selection) return <CheckoutSafeError />;

  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href={needSelection ? `/needs/${encodeURIComponent(needSelection.needRequestId)}` : directSelection ? `/items/${encodeURIComponent(directSelection.itemId)}?locationId=${encodeURIComponent(directSelection.locationId)}` : cartId ? `/cart/${encodeURIComponent(cartId)}` : "/cart"} aria-label={needSelection ? "Quay lại nhu cầu" : directSelection ? "Quay lại sản phẩm" : "Quay lại giỏ hàng"}><CoVieIcon name="back" /></Link>
        <h1>Xác nhận đơn</h1>
      </header>

      <section className={styles.content}>
        <h1 className={styles.desktopTitle}>Xác nhận đơn</h1>

        {/* 3-Step Checkout Stepper */}
        <div className={styles.stepperContainer}>
          <div className={styles.stepperWrap} aria-label="Tiến trình đặt hàng">
            <div className={`${styles.stepperStep} ${styles.stepperStepCompleted}`}>
              <span className={styles.stepperNum}>
                <Check size={12} weight="bold" />
              </span>
              <span className={styles.stepperText}>Thông tin giao nhận</span>
            </div>
            <div className={`${styles.stepperLine} ${styles.stepperLineCompleted}`} />
            <div className={`${styles.stepperStep} ${preview ? styles.stepperStepCompleted : styles.stepperStepActive}`}>
              <span className={styles.stepperNum}>
                {preview ? <Check size={12} weight="bold" /> : "2"}
              </span>
              <span className={styles.stepperText}>Kiểm tra chi phí</span>
            </div>
            <div className={`${styles.stepperLine} ${preview ? styles.stepperLineCompleted : ""}`} />
            <div className={`${styles.stepperStep} ${preview ? styles.stepperStepActive : ""}`}>
              <span className={styles.stepperNum}>3</span>
              <span className={styles.stepperText}>Đặt hàng</span>
            </div>
          </div>
        </div>

        {changed ? <CheckoutChangeDialog onPreviewAgain={requestPreview} /> : (
          <>
            {selection ? (
              <section className={styles.addressSection}>
                <div className={styles.selectionTitleRow}>
                  <h2>Món đã chọn</h2>
                  <span className={styles.itemBadge}>Mua ngay</span>
                </div>
                <p>
                  <strong>{selection.itemName}</strong>
                  {selection.variantName ? <><br /><span>Phân loại: {selection.variantName}</span></> : null}
                  <br />Số lượng: {quantity.toLocaleString("vi-VN")}
                </p>
                {selection.type === "PRODUCT" && !selection.fixedQuantity ? (
                  <div className={styles.quantityRow}>
                    <strong>Số lượng</strong>
                    <QuantityStepper quantity={quantity} onChange={changeQuantity} disabled={committing} />
                  </div>
                ) : null}
              </section>
            ) : null}

            {!selection && carts.length > 1 ? (
              <label className={styles.selectField}>
                <span>Giỏ hàng</span>
                <select
                  aria-label="Giỏ hàng"
                  value={cartId}
                  disabled={committing}
                  onChange={(event) => chooseCart(event.target.value)}
                >
                  {carts.map((cart, index) => (
                    <option key={cart.id} value={cart.id}>
                      {cart.cartType === "SERVICE" ? "Giỏ dịch vụ" : "Giỏ sản phẩm"} {index + 1}
                    </option>
                  ))}
                </select>
              </label>
            ) : null}

            {method !== "PICKUP" ? (
              <section className={styles.addressSection}>
                <div>
                  <h2>Địa chỉ nhận hàng</h2>
                  {addresses.length > 0 ? (
                    <button
                      className={styles.addressAction}
                      type="button"
                      disabled={committing}
                      aria-expanded={addressEditing}
                      onClick={() => setAddressEditing(!addressEditing)}
                    >
                      Thay đổi
                    </button>
                  ) : null}
                </div>

                {selectedAddress && !addressEditing ? (
                  <div className={styles.addressCard}>
                    <div className={styles.addressCardHeader}>
                      <span className={styles.addressLabel}>
                        <House size={16} weight="duotone" />
                        <strong>{selectedAddress.label ?? "Địa chỉ"}</strong>
                      </span>
                      {selectedAddress.defaultAddress && (
                        <span className={styles.defaultBadge}>Mặc định</span>
                      )}
                    </div>
                    <p>
                      <strong>{selectedAddress.label ?? "Địa chỉ"}</strong> · {selectedAddress.addressText}
                      <br />
                      {selectedAddress.recipientName}
                      {selectedAddress.phone ? ` · ${selectedAddress.phone}` : ""}
                    </p>
                  </div>
                ) : addresses.length > 0 ? (
                  <label>
                    Địa chỉ
                    <select
                      aria-label="Địa chỉ"
                      value={addressId}
                      disabled={committing}
                      onChange={(event) => {
                        setAddressId(event.target.value);
                        setAddressEditing(false);
                        setMessage(undefined);
                        invalidatePreview();
                      }}
                    >
                      <option value="">Chọn địa chỉ</option>
                      {addresses.map((address) => (
                        <option key={address.id} value={address.id}>
                          {address.label} — {address.addressText}
                        </option>
                      ))}
                    </select>
                  </label>
                ) : (
                  <Link href="/me/addresses/new" className={styles.addAddressLink}>
                    Thêm địa chỉ nhận hàng
                  </Link>
                )}
              </section>
            ) : null}

            <fieldset className={styles.fulfillment} disabled={committing}>
              <legend>Nhận hàng</legend>
              {isService ? (
                <label className={styles.optionSelected}>
                  <input type="radio" aria-label="SERVICE" checked readOnly />
                  <span>
                    <strong>Dịch vụ tại địa chỉ</strong>
                    <small>Người bán phối hợp lịch với bạn</small>
                  </span>
                </label>
              ) : (
                <>
                  <label className={method === "SELLER_DELIVERY" ? styles.optionSelected : styles.option}>
                    <input
                      type="radio"
                      aria-label="SELLER_DELIVERY"
                      checked={method === "SELLER_DELIVERY"}
                      onChange={() => {
                        setMethod("SELLER_DELIVERY");
                        invalidatePreview();
                      }}
                    />
                    <span>
                      <strong>Giao tận nơi</strong>
                      <small>Người bán giao trực tiếp đến địa chỉ của bạn (15-30 phút)</small>
                    </span>
                  </label>
                  <label className={method === "PICKUP" ? styles.optionSelected : styles.option}>
                    <input
                      type="radio"
                      aria-label="PICKUP"
                      checked={method === "PICKUP"}
                      onChange={() => {
                        setMethod("PICKUP");
                        invalidatePreview();
                      }}
                    />
                    <span>
                      <strong>Nhận tại điểm bán</strong>
                      <small>Tự đến căn hộ/điểm bán của hàng xóm để nhận hàng</small>
                    </span>
                  </label>
                </>
              )}
            </fieldset>

            {/* Buyer Order Note to Neighbor */}
            <div className={styles.orderNoteWrap}>
              <label htmlFor="order-note-input">
                <span className={styles.orderNoteLabel}>
                  <ChatCircleText size={16} weight="duotone" />
                  <span>Lời nhắn cho người bán (tùy chọn)</span>
                </span>
                <input
                  id="order-note-input"
                  className={styles.orderNoteInput}
                  type="text"
                  placeholder="Ví dụ: Giao sau 18h, gọi điện trước khi lên sảnh..."
                  value={orderNote}
                  disabled={committing}
                  onChange={(e) => setOrderNote(e.target.value)}
                />
              </label>
            </div>

            {preview?.fulfillmentMethod === "SELLER_DELIVERY" ? (
              <section className={styles.shipping}>
                <div>
                  <strong>{negotiated ? "Phí giao hàng" : "Giao tiêu chuẩn"}</strong>
                  <strong>{negotiated ? "Thỏa thuận với người bán" : money(preview.shippingFee)}</strong>
                </div>
                <p>{negotiated ? "Người bán sẽ trao đổi chi phí giao phù hợp" : "Chi phí theo bản xem trước từ hệ thống"}</p>
              </section>
            ) : null}

            <section className={styles.payment}>
              <h2>Thanh toán</h2>
              <label className={styles.paymentOption}>
                <input type="radio" checked readOnly />
                <span>COD — Thanh toán khi nhận hàng (tiền mặt / chuyển khoản)</span>
              </label>
              <label className={`${styles.paymentOption} ${styles.paymentDisabled}`}>
                <input type="radio" disabled />
                <span>Ví điện tử CoVie (Sắp ra mắt)</span>
              </label>
            </section>

            <section className={styles.summary} aria-live="polite">
              <h2>Tóm tắt thanh toán</h2>
              <dl>
                <div>
                  <dt>Tạm tính</dt>
                  <dd>{preview ? money(preview.subtotal) : (previewing ? "Đang tính…" : "—")}</dd>
                </div>
                <div>
                  <dt>Phí giao hàng</dt>
                  <dd>
                    {preview
                      ? (negotiated ? "Thỏa thuận với người bán" : money(preview.shippingFee))
                      : (method === "PICKUP" ? "0 ₫" : (previewing ? "Đang tính…" : "—"))}
                  </dd>
                </div>
                <div>
                  <dt>{totalLabel}</dt>
                  <dd>{preview ? money(totalValue) : (previewing ? "Đang tính…" : "—")}</dd>
                </div>
              </dl>
              <p className={negotiated ? styles.negotiatedNote : undefined}>
                {preview
                  ? (negotiated
                      ? "Phí giao sẽ được thỏa thuận; khoản tiền trên chưa phải tổng cuối cùng."
                      : "Số tiền hiển thị theo bản xem trước từ hệ thống.")
                  : (method !== "PICKUP" && !selectedAddress
                      ? "Chọn địa chỉ nhận hàng để xem giá chính xác."
                      : "Số tiền hiển thị theo bản xem trước từ hệ thống.")}
              </p>

              {preview ? (
                <button
                  className={styles.desktopCommit}
                  type="button"
                  onClick={commit}
                  disabled={committing}
                >
                  {committing ? "Đang đặt hàng…" : "Đặt hàng"}
                </button>
              ) : (
                <button
                  className={`${styles.desktopCommit} ${styles.previewButton}`}
                  type="button"
                  onClick={requestPreview}
                  disabled={previewing || (method !== "PICKUP" && !selectedAddress)}
                  aria-busy={previewing}
                >
                  {previewing ? "Đang kiểm tra…" : "Xem trước"}
                </button>
              )}

              <div className={styles.guaranteeCard}>
                <ShieldCheck size={18} weight="duotone" className={styles.guaranteeIcon} />
                <span>Kiểm tra hàng trước khi thanh toán • Hỗ trợ nội khu 24/7</span>
              </div>
            </section>

            {message ? <p className={styles.error} role="alert">{message}</p> : null}
            {method !== "PICKUP" && !selectedAddress ? (
              <p className={styles.error}>Chọn địa chỉ nhận hàng trước khi xem giá.</p>
            ) : null}
          </>
        )}
      </section>

      {preview && !changed ? (
        <div className={styles.commitBar}>
          <div>
            <span>{totalLabel}</span>
            <strong>{money(totalValue)}</strong>
          </div>
          <button type="button" onClick={commit} disabled={committing}>
            {committing ? "Đang đặt hàng…" : "Đặt hàng"}
          </button>
        </div>
      ) : null}
    </div>
  );
}
