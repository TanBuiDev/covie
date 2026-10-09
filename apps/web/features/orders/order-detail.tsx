"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  ArrowLeft,
  CheckCircle,
  Clock,
  ForkKnife,
  Package,
  Phone,
  ShieldCheck,
  ShoppingBag,
  Storefront,
  Truck,
  Wrench,
} from "@phosphor-icons/react";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { CovieApiError } from "@/lib/api/api-error";
import { formatDateTime } from "@/lib/format/time";
import { formatMoney, negotiatedShippingCopy } from "@/lib/format/money";
import { statusCopy } from "@/lib/format/status-copy";
import { getOrder, type OrderDetail } from "./order-api";
import { OrderActions } from "./order-actions";
import { OrderDetailMobile } from "./order-detail-mobile";
import mobileStyles from "./order-detail-mobile.module.css";
import { OrderStatusLabel } from "./order-status";
import { OrderTimeline } from "./order-timeline";
import { OrderReviewSection } from "@/features/reviews/order-review-section";

function getItemVisual(name?: string | null, orderType?: string | null) {
  const lower = (name ?? "").toLowerCase();
  if (orderType === "SERVICE" || lower.includes("sửa") || lower.includes("dịch vụ") || lower.includes("bảo dưỡng")) {
    return {
      icon: <Wrench size={18} weight="duotone" />,
      bg: "#ecfdf5",
      color: "#059669",
    };
  }
  if (
    lower.includes("bánh") ||
    lower.includes("chè") ||
    lower.includes("trà") ||
    lower.includes("cà phê") ||
    lower.includes("cơm") ||
    lower.includes("phở") ||
    lower.includes("ăn") ||
    lower.includes("kem") ||
    lower.includes("nước") ||
    lower.includes("món") ||
    lower.includes("thực phẩm")
  ) {
    return {
      icon: <ForkKnife size={18} weight="duotone" />,
      bg: "#fffbeb",
      color: "#d97706",
    };
  }
  return {
    icon: <Package size={18} weight="duotone" />,
    bg: "#f1f5f9",
    color: "#475569",
  };
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function firstText(value: unknown, keys: string[]): string | undefined {
  const record = asRecord(value);
  if (!record) return undefined;
  for (const key of keys) {
    const candidate = record[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate.trim();
  }
  return undefined;
}

function itemName(snapshot: unknown): string {
  return firstText(snapshot, ["itemName", "name", "title", "variantName"]) ?? "Mục đơn hàng";
}

function money(amount: number | null | undefined, currency: string | null | undefined) {
  return amount != null && currency ? formatMoney({ amount, currency }) : "—";
}

function fulfillmentCopy(order: OrderDetail) {
  if (order.type === "SERVICE") return "Dịch vụ";
  if (order.fulfillment?.productMethod === "PICKUP") return "Nhận tại điểm bán";
  if (order.fulfillment?.productMethod === "SELLER_DELIVERY") return "Người bán giao";
  return "Theo thông tin hiện có";
}

function BuyerOrderDetail({ order, error, refresh }: { order: OrderDetail; error?: string; refresh: () => Promise<void> }) {
  const address = firstText(order.fulfillment?.address, ["addressText", "formattedAddress", "label"]);
  const pickup = firstText(order.fulfillment?.pickupLocation, ["addressText", "name", "label"]);
  const contact = firstText(order.fulfillment?.merchantContact, ["value", "phone", "zalo", "messenger", "label"]);
  const currency = order.pricing?.currency ?? order.items?.find((item) => item.currency)?.currency;
  const total = order.pricing?.totalAmount ?? order.pricing?.knownTotal;

  return (
    <>
      <OrderDetailMobile order={order} error={error} onChanged={refresh} />
      <article className={`order-detail ${mobileStyles.desktopOnly}`}>
        <div className="order-detail__top-nav">
          <Link href="/orders" className="order-detail__back-link">
            <ArrowLeft size={16} weight="bold" />
            <span>Quay lại danh sách đơn</span>
          </Link>
        </div>
        <header className="order-detail__page-header">
          <div className="order-detail__title-wrap">
            <h1>Đơn {order.orderCode ?? order.id ?? ""}</h1>
            <OrderStatusLabel status={order.status} />
          </div>
          {order.createdAt ? (
            <p className="order-detail__created-date">Đặt lúc {formatDateTime(order.createdAt)}</p>
          ) : null}
        </header>
        {error ? <p className="order-detail__alert" role="status">{error}</p> : null}
        <div className="order-detail__columns">
          <section className="order-detail__summary" aria-labelledby="order-summary-title">
            <div className="order-detail__identity">
              <h2 id="order-summary-title">{order.orderCode ?? "Đơn hàng"}</h2>
              <OrderStatusLabel status={order.status} />
            </div>

            <OrderTimeline
              events={order.timeline}
              status={order.status}
              createdAt={order.createdAt}
              confirmedAt={order.confirmedAt}
              completedAt={order.completedAt}
              closedAt={order.closedAt}
            />

            <div className="order-detail__card-block">
              <h3>{order.type === "SERVICE" ? "Chi tiết dịch vụ" : "Sản phẩm trong đơn"}</h3>
              <div className="order-detail__items">
                {(order.items ?? []).map((item, index) => {
                  const name = itemName(item.snapshot);
                  const visual = getItemVisual(name, item.type ?? order.type);
                  return (
                    <div className="order-detail__item" key={item.id ?? `${item.locationVariantId ?? "item"}-${index}`}>
                      <div className="order-detail__item-info">
                        <span
                          className="order-detail__item-icon"
                          style={{ backgroundColor: visual.bg, color: visual.color, borderColor: visual.bg }}
                          aria-hidden="true"
                        >
                          {visual.icon}
                        </span>
                        <div className="order-detail__item-text">
                          <strong>{name}</strong>
                          <span className="order-detail__item-qty">
                            {item.quantity ?? 1} × {name} ({money(item.unitPrice, item.currency ?? currency)})
                          </span>
                        </div>
                      </div>
                      <strong className="order-detail__item-total">
                        {money(item.lineTotal ?? (item.unitPrice != null && item.quantity != null ? item.unitPrice * item.quantity : item.unitPrice), item.currency ?? currency)}
                      </strong>
                    </div>
                  );
                })}
                {!order.items?.length ? <p>Chi tiết sản phẩm chưa có trong phản hồi hiện tại.</p> : null}
              </div>
            </div>

            <div className="order-detail__card-block">
              <h3>Thông tin nhận hàng & giao nhận</h3>
              <div className="order-detail__fulfillment-info">
                <p className="order-detail__fulfillment">
                  <strong>Hình thức: </strong>
                  {fulfillmentCopy(order)}
                  {order.pricing?.shippingFee != null && currency ? ` · Phí giao ${money(order.pricing.shippingFee, currency)}` : ""}
                </p>
                {address ? <p><strong>Địa chỉ nhận:</strong> {address}</p> : pickup ? <p><strong>Điểm nhận:</strong> {pickup}</p> : null}
              </div>
            </div>

            {order.cancellation ? (
              <div className="order-detail__context-card" aria-label="Yêu cầu hủy">
                <strong>Yêu cầu hủy đơn</strong>
                <p>Trạng thái: {order.cancellation.status ?? "—"}</p>
                {order.cancellation.reasonText || order.cancellation.reasonCode ? (
                  <p>{order.cancellation.reasonText ?? order.cancellation.reasonCode}</p>
                ) : null}
                {order.cancellation.responseNote ? <p>{order.cancellation.responseNote}</p> : null}
              </div>
            ) : null}
            {order.issue ? (
              <div className="order-detail__context-card order-detail__context-card--issue" aria-label="Vấn đề đơn hàng">
                <strong>Vấn đề đang được ghi nhận</strong>
                <p>{order.issue.reasonText ?? order.issue.type ?? "CoVie đang lưu trạng thái do hệ thống trả về."}</p>
                {order.issue.status ? <small>{order.issue.status}</small> : null}
              </div>
            ) : null}
          </section>

          <aside className="order-detail__aside" aria-label="Sản phẩm và thao tác">
            <div className="order-detail__merchant-box">
              <div className="order-detail__merchant-header">
                <Storefront size={22} weight="duotone" className="order-detail__merchant-icon" />
                <div>
                  <p className="order-detail__merchant">{order.merchant?.merchantId ? `Người bán · ${order.merchant.merchantId}` : "Người bán"}</p>
                  <small className="order-detail__merchant-note">Cư dân nội khu CoVie</small>
                </div>
              </div>
              {contact ? (
                <div className="order-detail__merchant-contact">
                  <span>Liên hệ: {contact}</span>
                  <a href={`tel:${contact.replace(/\D/g, "")}`} className="order-detail__merchant-call-btn">
                    <Phone size={14} weight="fill" /> Gọi
                  </a>
                </div>
              ) : null}
            </div>

            <div className="order-detail__pricing-box">
              <h3>Tóm tắt thanh toán</h3>
              <div className="order-detail__pricing-row">
                <span>Tiền hàng</span>
                <strong>{money(order.pricing?.subtotal, currency)}</strong>
              </div>
              <div className="order-detail__pricing-row">
                <span>Phí giao hàng</span>
                <span>
                  {order.pricing?.shippingFeeType === "NEGOTIATED"
                    ? negotiatedShippingCopy()
                    : money(order.pricing?.shippingFee, currency)}
                </span>
              </div>
              {order.paymentMethod ? (
                <div className="order-detail__pricing-row">
                  <span>Thanh toán</span>
                  <span>{order.paymentMethod === "COD" ? "COD khi nhận" : "Chuyển khoản thỏa thuận"}</span>
                </div>
              ) : null}
              <div className="order-detail__total">
                <span>Tổng theo đơn</span>
                <strong>{money(total, currency)}</strong>
              </div>
            </div>

            <div className="order-detail__actions-box">
              <h3>Hành động khả dụng</h3>
              <OrderActions order={order} actorView="BUYER" onChanged={refresh} />
              {!order.allowedActions?.length ? (
                <p className="order-detail__no-actions">Không có thao tác nào được backend cho phép ở trạng thái hiện tại.</p>
              ) : null}
            </div>

            {(order.items ?? []).map((item, index) => (
              <OrderReviewSection key={item.id ?? `review-${index}`} item={item} orderId={order.id} onChanged={refresh} />
            ))}

            <Link href="/" className="button-secondary order-detail__reorder-cta">
              <ShoppingBag size={16} weight="bold" />
              <span>Tiếp tục mua sắm</span>
            </Link>
          </aside>
        </div>
      </article>
    </>
  );
}

export function SellerOrderDetail({ order, merchantId, error, refresh }: { order: OrderDetail; merchantId: string; error?: string; refresh: () => Promise<void> }) {
  const currency = order.pricing?.currency ?? order.items?.find((item) => item.currency)?.currency;
  const buyerReputation = order.reputation?.buyer;
  const hasActions = (order.allowedActions?.length ?? 0) > 0;
  return (
    <article className="order-detail seller-order-detail">
      <header className="surface-header seller-order-detail__hero">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "8px" }}>
          <p className="eyebrow" style={{ margin: 0 }}>Đơn {order.orderCode ?? order.id ?? ""}</p>
          <Link href={`/seller/${encodeURIComponent(merchantId)}/orders`} className="seller-order-back-link" style={{ display: "inline-flex", alignItems: "center", gap: "6px", fontSize: "13px", fontWeight: "600", color: "var(--seller-brand, #047857)", textDecoration: "none" }}>
            <ArrowLeft size={14} weight="bold" />
            <span>Quay lại danh sách đơn</span>
          </Link>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "12px", flexWrap: "wrap" }}>
          <div style={{ width: "38px", height: "38px", borderRadius: "10px", background: order.type === "SERVICE" ? "rgba(4, 120, 87, 0.08)" : "rgba(71, 85, 105, 0.08)", color: order.type === "SERVICE" ? "#047857" : "#475569", display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
            {order.type === "SERVICE" ? <Wrench size={22} weight="duotone" /> : <Package size={22} weight="duotone" />}
          </div>
          <h1 style={{ margin: 0 }}>{order.type === "SERVICE" ? "Đơn dịch vụ" : "Đơn sản phẩm"}</h1>
          <OrderStatusLabel status={order.status} />
        </div>
        {order.createdAt ? (
          <p style={{ margin: "6px 0 0", fontSize: "12px", color: "var(--seller-text-secondary, #64748b)", display: "flex", alignItems: "center", gap: "4px" }}>
            <Clock size={13} weight="bold" />
            <span>Đặt lúc {formatDateTime(order.createdAt)}</span>
          </p>
        ) : null}
        {error ? <p role="status">Dữ liệu gần nhất vẫn đang hiển thị. Lần làm mới vừa rồi thất bại: {error}</p> : null}
      </header>

      <div className="seller-order-detail__timeline">
        <OrderTimeline events={order.timeline ?? []} status={order.status} createdAt={order.createdAt} confirmedAt={order.confirmedAt} completedAt={order.completedAt} closedAt={order.closedAt} />
      </div>

      <section className="seller-order-detail__snapshot" aria-labelledby="order-items-title">
        <h2 id="order-items-title">Nội dung đơn</h2>
        <div className="seller-order-detail__snapshot-grid">
          <div>
            {order.items?.length ? (
              <ul className="seller-order-item-list">
                {order.items.map((item, index) => {
                  const name = itemName(item.snapshot);
                  const visual = getItemVisual(name, item.type);
                  return (
                    <li key={item.id ?? `${item.catalogItemId ?? "item"}-${index}`}>
                      <div style={{ display: "flex", gap: "10px", alignItems: "flex-start" }}>
                        <div style={{ width: "32px", height: "32px", borderRadius: "8px", background: visual.bg, color: visual.color, display: "flex", alignItems: "center", justifyContent: "center", flexShrink: 0 }}>
                          {visual.icon}
                        </div>
                        <div>
                          <strong>{item.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</strong>
                          {name !== "Mục đơn hàng" ? <span style={{ display: "block", fontSize: "12px", color: "var(--seller-text-primary, #0f172a)", fontWeight: "500" }}>{name}</span> : null}
                          <small>Số lượng: {item.quantity ?? "—"}</small>
                        </div>
                      </div>
                      <div className="seller-order-item-price">
                        <small>Đơn giá: {money(item.unitPrice, item.currency ?? currency)}</small>
                        <strong>{money(item.lineTotal, item.currency ?? currency)}</strong>
                      </div>
                    </li>
                  );
                })}
              </ul>
            ) : (
              <p className="seller-muted">Chưa có thông tin sản phẩm hoặc dịch vụ trong đơn.</p>
            )}
          </div>
          <div>
            <h3>Thanh toán</h3>
            <dl className="seller-order-facts">
              <div>
                <dt>Tiền hàng</dt>
                <dd>{money(order.pricing?.subtotal, currency)}</dd>
              </div>
              <div>
                <dt>Phí giao</dt>
                <dd>{order.pricing?.shippingFeeType === "NEGOTIATED" ? negotiatedShippingCopy() : money(order.pricing?.shippingFee, currency)}</dd>
              </div>
              <div>
                <dt>{order.pricing?.shippingFeeType === "NEGOTIATED" ? "Tạm tính trên CoVie" : "Tổng"}</dt>
                <dd style={{ color: "var(--seller-brand, #047857)", fontSize: "16px" }}>
                  {money(order.pricing?.shippingFeeType === "NEGOTIATED" ? order.pricing?.knownTotal : order.pricing?.totalAmount ?? order.pricing?.knownTotal, currency)}
                </dd>
              </div>
              <div>
                <dt>Phương thức thanh toán</dt>
                <dd>{order.paymentMethod === "COD" ? "Thanh toán khi nhận" : order.paymentMethod === "BANK_TRANSFER_NEGOTIATED" ? "Chuyển khoản thỏa thuận" : "—"}</dd>
              </div>
            </dl>
            {order.pricing?.shippingFeeType === "NEGOTIATED" ? <p className="seller-muted">Tổng cuối cùng sẽ được cập nhật sau khi phí giao được thống nhất.</p> : null}
          </div>
        </div>
      </section>

      <section className="seller-order-detail__fulfillment" aria-labelledby="order-fulfillment-title">
        <h2 id="order-fulfillment-title">Thực hiện đơn</h2>
        <dl className="seller-order-facts">
          <div>
            <dt>Hình thức</dt>
            <dd>{fulfillmentCopy(order)}</dd>
          </div>
          {order.fulfillment?.distanceMeters != null ? (
            <div>
              <dt>Khoảng cách</dt>
              <dd>{(order.fulfillment.distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd>
            </div>
          ) : null}
        </dl>
      </section>

      {order.cancellation || order.issue ? (
        <section className="seller-order-detail__info" aria-label="Thông tin cần chú ý">
          {order.cancellation ? (
            <div aria-labelledby="order-cancellation-title">
              <h2 id="order-cancellation-title">Yêu cầu hủy</h2>
              <p><strong>{statusCopy(order.cancellation.status)}</strong></p>
              {order.cancellation.reasonText || order.cancellation.reasonCode ? <p>{order.cancellation.reasonText ?? order.cancellation.reasonCode}</p> : null}
              {order.cancellation.responseNote ? <p>Phản hồi: {order.cancellation.responseNote}</p> : null}
            </div>
          ) : null}
          {order.issue ? (
            <div className="warning-banner" aria-labelledby="order-issue-title">
              <h2 id="order-issue-title">Sự cố đang được ghi nhận</h2>
              <p>{statusCopy(order.issue.status)}</p>
              {order.issue.reasonText ? <p>{order.issue.reasonText}</p> : null}
            </div>
          ) : null}
        </section>
      ) : null}

      {buyerReputation ? (
        <section className="seller-order-detail__privacy" aria-labelledby="buyer-reputation-title">
          <h2 id="buyer-reputation-title">Độ tin cậy người mua</h2>
          <dl className="seller-order-facts">
            <div>
              <dt>Đơn đủ điều kiện</dt>
              <dd>{buyerReputation.eligibleOrders ?? "—"}</dd>
            </div>
            <div>
              <dt>Đơn hoàn tất</dt>
              <dd>{buyerReputation.completedOrders ?? "—"}</dd>
            </div>
            <div>
              <dt>Tỷ lệ hoàn tất</dt>
              <dd style={{ color: "#047857" }}>
                {buyerReputation.completionRateBasisPoints != null
                  ? `${(buyerReputation.completionRateBasisPoints / 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%`
                  : "—"}
              </dd>
            </div>
          </dl>
          {buyerReputation.warning ? (
            <p className="warning-banner" role="status">
              Người mua đang có cảnh báo độ tin cậy trong ngữ cảnh đơn này.
            </p>
          ) : null}
        </section>
      ) : null}

      <section className="seller-order-detail__actions" aria-labelledby="seller-order-actions-title">
        <h2 id="seller-order-actions-title">Hành động khả dụng</h2>
        <OrderActions order={order} actorView="SELLER" onChanged={refresh} />
        {!hasActions ? <div className="seller-card" aria-label="Trạng thái thao tác"><p className="seller-muted">Hiện không có thao tác khả dụng cho đơn này.</p></div> : null}
        <Link href={`/seller/${encodeURIComponent(merchantId)}/orders`} style={{ display: "inline-flex", alignItems: "center", gap: "6px", textDecoration: "none", color: "var(--seller-brand, #047857)", fontWeight: "600", fontSize: "14px", marginTop: "8px" }}>
          <ArrowLeft size={16} weight="bold" />
          <span>Quay lại danh sách đơn</span>
        </Link>
      </section>
    </article>
  );
}

export function OrderDetailView({ orderId, sellerMerchantId }: { orderId: string; sellerMerchantId?: string }) {
  return <OrderDetailContent key={`${sellerMerchantId ?? "buyer"}:${orderId}`} orderId={orderId} sellerMerchantId={sellerMerchantId} />;
}

function OrderDetailContent({ orderId, sellerMerchantId }: { orderId: string; sellerMerchantId?: string }) {
  const [order, setOrder] = useState<OrderDetail>();
  const [error, setError] = useState<string>();
  const [isAuthError, setIsAuthError] = useState(false);
  const generation = useRef(0);
  const refresh = useCallback(async () => {
    const requestGeneration = ++generation.current;
    try {
      const value = await getOrder(orderId);
      if (requestGeneration !== generation.current) return;
      setOrder(value);
      setError(undefined);
      setIsAuthError(false);
    } catch (cause) {
      if (requestGeneration !== generation.current) return;
      setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
      setError("Không thể tải đơn hàng. Vui lòng thử lại.");
    }
  }, [orderId]);
  useEffect(() => {
    let active = true;
    const requestState = generation;
    queueMicrotask(() => { if (active) void refresh(); });
    return () => { active = false; requestState.current++; };
  }, [refresh]);
  if (isAuthError && !sellerMerchantId) return <ErrorState
    title="Vui lòng đăng nhập lại"
    description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
    action={<Link className="button-primary" href={`/login?returnTo=${encodeURIComponent(`/orders/${encodeURIComponent(orderId)}`)}`}>Đăng nhập lại</Link>}
  />;
  if (error && !order) return <ErrorState title="Không thể tải đơn hàng" description={error} action={<button type="button" onClick={() => void refresh()}>Tải lại</button>} />;
  if (!order) return <LoadingState label="Đang tải đơn hàng…" />;
  return sellerMerchantId ? <SellerOrderDetail order={order} merchantId={sellerMerchantId} error={error} refresh={refresh} /> : <BuyerOrderDetail order={order} error={error} refresh={refresh} />;
}
