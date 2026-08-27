"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { formatMoney, negotiatedShippingCopy } from "@/lib/format/money";
import { statusCopy } from "@/lib/format/status-copy";
import { getOrder, type OrderDetail } from "./order-api";
import { OrderActions } from "./order-actions";
import { OrderDetailMobile } from "./order-detail-mobile";
import mobileStyles from "./order-detail-mobile.module.css";
import { OrderStatusLabel } from "./order-status";
import { OrderTimeline } from "./order-timeline";

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
        <header className="order-detail__page-header"><h1>Đơn {order.orderCode ?? order.id ?? ""}</h1></header>
        {error ? <p className="order-detail__alert" role="status">{error}</p> : null}
        <div className="order-detail__columns">
          <section className="order-detail__summary" aria-labelledby="order-summary-title">
            <div className="order-detail__identity"><h2 id="order-summary-title">{order.orderCode ?? "Đơn hàng"}</h2><OrderStatusLabel status={order.status} /></div>
            <p className="order-detail__merchant">{order.merchant?.merchantId ? `Người bán · ${order.merchant.merchantId}` : "Người bán"}</p>
            {address ? <p>{address}</p> : pickup ? <p>{pickup}</p> : null}
            {contact ? <p>Liên hệ: {contact}</p> : null}
            <OrderTimeline events={order.timeline} status={order.status} createdAt={order.createdAt} confirmedAt={order.confirmedAt} completedAt={order.completedAt} closedAt={order.closedAt} />
            {order.cancellation ? <div className="order-detail__context-card" aria-label="Yêu cầu hủy"><strong>Yêu cầu hủy</strong><p>Trạng thái: {order.cancellation.status ?? "—"}</p>{order.cancellation.reasonText || order.cancellation.reasonCode ? <p>{order.cancellation.reasonText ?? order.cancellation.reasonCode}</p> : null}{order.cancellation.responseNote ? <p>{order.cancellation.responseNote}</p> : null}</div> : null}
            {order.issue ? <div className="order-detail__context-card order-detail__context-card--issue" aria-label="Vấn đề đơn hàng"><strong>Vấn đề đang được ghi nhận</strong><p>{order.issue.reasonText ?? order.issue.type ?? "CoVie đang lưu trạng thái do hệ thống trả về."}</p>{order.issue.status ? <small>{order.issue.status}</small> : null}</div> : null}
          </section>
          <aside className="order-detail__aside" aria-label="Sản phẩm và thao tác">
            <h2>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</h2>
            <div className="order-detail__items">{(order.items ?? []).map((item, index) => <div className="order-detail__item" key={item.id ?? `${item.locationVariantId ?? "item"}-${index}`}><span>{item.quantity ?? 1} × {itemName(item.snapshot)}</span><strong>{money(item.lineTotal ?? (item.unitPrice != null && item.quantity != null ? item.unitPrice * item.quantity : item.unitPrice), item.currency ?? currency)}</strong></div>)}{!order.items?.length ? <p>Chi tiết sản phẩm chưa có trong phản hồi hiện tại.</p> : null}</div>
            <p className="order-detail__fulfillment">{fulfillmentCopy(order)}{order.pricing?.shippingFee != null && currency ? ` · Phí giao ${money(order.pricing.shippingFee, currency)}` : ""}</p>
            <div className="order-detail__total"><span>Tổng theo đơn</span><strong>{money(total, currency)}</strong></div>
            <h3>Hành động khả dụng</h3><OrderActions order={order} actorView="BUYER" onChanged={refresh} />
            {!order.allowedActions?.length ? <p className="order-detail__no-actions">Không có thao tác nào được backend cho phép ở trạng thái hiện tại.</p> : null}
          </aside>
        </div>
      </article>
    </>
  );
}

function SellerOrderDetail({ order, merchantId, error, refresh }: { order: OrderDetail; merchantId: string; error?: string; refresh: () => Promise<void> }) {
  const currency = order.pricing?.currency ?? order.items?.find((item) => item.currency)?.currency;
  const buyerReputation = order.reputation?.buyer;
  const hasActions = (order.allowedActions?.length ?? 0) > 0;
  return (
    <article className="order-detail">
      <header className="surface-header"><p className="eyebrow">Đơn {order.orderCode ?? order.id ?? ""}</p><h1>{order.type === "SERVICE" ? "Đơn dịch vụ" : "Đơn sản phẩm"}</h1><OrderStatusLabel status={order.status} />{error ? <p role="status">Dữ liệu gần nhất vẫn đang hiển thị. Lần làm mới vừa rồi thất bại: {error}</p> : null}</header>
      <OrderTimeline events={order.timeline ?? []} status={order.status} createdAt={order.createdAt} confirmedAt={order.confirmedAt} completedAt={order.completedAt} closedAt={order.closedAt} />
      <section aria-labelledby="order-items-title"><h2 id="order-items-title">Nội dung đơn</h2>{order.items?.length ? <ul className="seller-order-item-list">{order.items.map((item, index) => <li key={item.id ?? `${item.catalogItemId ?? "item"}-${index}`}><div><strong>{item.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</strong><small>Số lượng: {item.quantity ?? "—"}</small></div><div className="seller-order-item-price"><small>Đơn giá: {money(item.unitPrice, item.currency ?? currency)}</small><strong>{money(item.lineTotal, item.currency ?? currency)}</strong></div></li>)}</ul> : <p className="seller-muted">Chưa có thông tin sản phẩm hoặc dịch vụ trong đơn.</p>}</section>
      <section aria-labelledby="order-pricing-title"><h2 id="order-pricing-title">Thanh toán</h2><dl className="seller-order-facts"><div><dt>Tiền hàng</dt><dd>{money(order.pricing?.subtotal, currency)}</dd></div><div><dt>Phí giao</dt><dd>{order.pricing?.shippingFeeType === "NEGOTIATED" ? negotiatedShippingCopy() : money(order.pricing?.shippingFee, currency)}</dd></div><div><dt>{order.pricing?.shippingFeeType === "NEGOTIATED" ? "Tạm tính trên CoVie" : "Tổng"}</dt><dd>{money(order.pricing?.shippingFeeType === "NEGOTIATED" ? order.pricing?.knownTotal : order.pricing?.totalAmount ?? order.pricing?.knownTotal, currency)}</dd></div><div><dt>Phương thức thanh toán</dt><dd>{order.paymentMethod === "COD" ? "Thanh toán khi nhận" : order.paymentMethod === "BANK_TRANSFER_NEGOTIATED" ? "Chuyển khoản thỏa thuận" : "—"}</dd></div></dl>{order.pricing?.shippingFeeType === "NEGOTIATED" ? <p className="seller-muted">Tổng cuối cùng sẽ được cập nhật sau khi phí giao được thống nhất.</p> : null}</section>
      <section aria-labelledby="order-fulfillment-title"><h2 id="order-fulfillment-title">Thực hiện đơn</h2><dl className="seller-order-facts"><div><dt>Hình thức</dt><dd>{fulfillmentCopy(order)}</dd></div>{order.fulfillment?.distanceMeters != null ? <div><dt>Khoảng cách</dt><dd>{(order.fulfillment.distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</dd></div> : null}</dl></section>
      {order.cancellation ? <section aria-labelledby="order-cancellation-title"><h2 id="order-cancellation-title">Yêu cầu hủy</h2><p><strong>{statusCopy(order.cancellation.status)}</strong></p>{order.cancellation.reasonText || order.cancellation.reasonCode ? <p>{order.cancellation.reasonText ?? order.cancellation.reasonCode}</p> : null}{order.cancellation.responseNote ? <p>Phản hồi: {order.cancellation.responseNote}</p> : null}</section> : null}
      {order.issue ? <section className="warning-banner" aria-labelledby="order-issue-title"><h2 id="order-issue-title">Sự cố đang được ghi nhận</h2><p>{statusCopy(order.issue.status)}</p>{order.issue.reasonText ? <p>{order.issue.reasonText}</p> : null}</section> : null}
      {buyerReputation ? <section aria-labelledby="buyer-reputation-title"><h2 id="buyer-reputation-title">Độ tin cậy người mua</h2><dl className="seller-order-facts"><div><dt>Đơn đủ điều kiện</dt><dd>{buyerReputation.eligibleOrders ?? "—"}</dd></div><div><dt>Đơn hoàn tất</dt><dd>{buyerReputation.completedOrders ?? "—"}</dd></div><div><dt>Tỷ lệ hoàn tất</dt><dd>{buyerReputation.completionRateBasisPoints != null ? `${(buyerReputation.completionRateBasisPoints / 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%` : "—"}</dd></div></dl>{buyerReputation.warning ? <p className="warning-banner" role="status">Người mua đang có cảnh báo độ tin cậy trong ngữ cảnh đơn này.</p> : null}</section> : null}
      <section aria-labelledby="order-context-title"><h2 id="order-context-title">Điều hướng</h2><Link href={`/seller/${encodeURIComponent(merchantId)}/orders`}>Quay lại danh sách đơn</Link></section>
      {hasActions ? <OrderActions order={order} actorView="SELLER" onChanged={refresh} /> : <section className="seller-card" aria-label="Trạng thái thao tác"><p className="seller-muted">Hiện không có thao tác khả dụng cho đơn này.</p></section>}
    </article>
  );
}

export function OrderDetailView({ orderId, sellerMerchantId }: { orderId: string; sellerMerchantId?: string }) {
  const [order, setOrder] = useState<OrderDetail>();
  const [error, setError] = useState<string>();
  const refresh = useCallback(async () => { try { setOrder(await getOrder(orderId)); setError(undefined); } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng."); } }, [orderId]);
  useEffect(() => { queueMicrotask(() => { void refresh(); }); }, [refresh]);
  if (error && !order) return <ErrorState title="Không thể tải đơn hàng" description={error} action={<button type="button" onClick={() => void refresh()}>Tải lại</button>} />;
  if (!order) return <LoadingState label="Đang tải đơn hàng…" />;
  return sellerMerchantId ? <SellerOrderDetail order={order} merchantId={sellerMerchantId} error={error} refresh={refresh} /> : <BuyerOrderDetail order={order} error={error} refresh={refresh} />;
}
