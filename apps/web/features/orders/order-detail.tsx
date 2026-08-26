"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { formatMoney } from "@/lib/format/money";
import { getOrder, type OrderDetail } from "./order-api";
import { OrderActions } from "./order-actions";
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

function formatAmount(amount: number | undefined, currency: string | undefined): string {
  if (amount == null || !currency) return "—";
  return formatMoney({ amount, currency });
}

function fulfillmentCopy(order: OrderDetail): string {
  if (order.type === "SERVICE") return "Dịch vụ";
  if (order.fulfillment?.productMethod === "SELLER_DELIVERY") return "Giao tận nơi";
  if (order.fulfillment?.productMethod === "PICKUP") return "Nhận tại cửa hàng";
  return "Phương thức theo đơn hàng";
}

function paymentCopy(order: OrderDetail): string {
  if (order.paymentMethod === "COD") return "COD";
  if (order.paymentMethod === "BANK_TRANSFER_NEGOTIATED") return "Chuyển khoản theo thỏa thuận";
  return "—";
}

export function OrderDetailView({ orderId, sellerMerchantId }: { orderId: string; sellerMerchantId?: string }) {
  const [order, setOrder] = useState<OrderDetail>();
  const [error, setError] = useState<string>();

  const refresh = useCallback(async () => {
    try {
      setOrder(await getOrder(orderId));
      setError(undefined);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng.");
    }
  }, [orderId]);

  useEffect(() => {
    queueMicrotask(() => {
      void refresh();
    });
  }, [refresh]);

  if (error && !order) {
    return (
      <ErrorState
        title="Không thể tải đơn hàng"
        description={error}
        action={<button type="button" onClick={() => void refresh()}>Tải lại</button>}
      />
    );
  }
  if (!order) return <LoadingState label="Đang tải đơn hàng…" />;

  const address = firstText(order.fulfillment?.address, ["addressText", "formattedAddress", "label"]);
  const pickup = firstText(order.fulfillment?.pickupLocation, ["addressText", "name", "label"]);
  const contact = firstText(order.fulfillment?.merchantContact, ["value", "phone", "zalo", "messenger", "label"]);
  const currency = order.pricing?.currency ?? order.items?.find((item) => item.currency)?.currency;
  const total = order.pricing?.totalAmount ?? order.pricing?.knownTotal;
  const merchantIdentity = order.merchant?.merchantId ? `Người bán · ${order.merchant.merchantId}` : "Người bán";

  return (
    <article className="order-detail">
      <header className="order-detail__page-header">
        <h1>Đơn {order.orderCode ?? order.id ?? ""}</h1>
      </header>

      {error ? <p className="order-detail__alert" role="status">{error}</p> : null}

      <div className="order-detail__columns">
        <section className="order-detail__summary" aria-labelledby="order-summary-title">
          <div className="order-detail__identity">
            <h2 id="order-summary-title">{order.orderCode ?? "Đơn hàng"}</h2>
            <OrderStatusLabel status={order.status} />
          </div>
          <p className="order-detail__merchant">{merchantIdentity}</p>
          {address ? <p>{address}</p> : pickup ? <p>{pickup}</p> : null}
          {contact ? <p>Liên hệ: {contact}</p> : null}

          <OrderTimeline
            status={order.status}
            events={order.timeline}
            createdAt={order.createdAt}
            confirmedAt={order.confirmedAt}
            completedAt={order.completedAt}
            closedAt={order.closedAt}
          />

          {order.cancellation ? (
            <div className="order-detail__context-card" aria-label="Yêu cầu hủy">
              <strong>Yêu cầu hủy</strong>
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

          {sellerMerchantId ? (
            <p className="order-detail__seller-back">
              <Link href={`/seller/${encodeURIComponent(sellerMerchantId)}/orders`}>Quay lại danh sách đơn</Link>
            </p>
          ) : null}
        </section>

        <aside className="order-detail__aside" aria-label="Sản phẩm và thao tác">
          <h2>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</h2>
          <div className="order-detail__items">
            {(order.items ?? []).map((item, index) => (
              <div className="order-detail__item" key={item.id ?? `${item.locationVariantId ?? "item"}-${index}`}>
                <span>{item.quantity ?? 1} × {itemName(item.snapshot)}</span>
                <strong>{formatAmount(item.lineTotal ?? (item.unitPrice != null && item.quantity != null ? item.unitPrice * item.quantity : item.unitPrice), item.currency ?? currency)}</strong>
              </div>
            ))}
            {!order.items?.length ? <p>Chi tiết sản phẩm chưa có trong phản hồi hiện tại.</p> : null}
          </div>

          <p className="order-detail__fulfillment">
            {fulfillmentCopy(order)}
            {order.pricing?.shippingFee != null && currency ? ` · Phí giao ${formatAmount(order.pricing.shippingFee, currency)}` : ""}
            {order.paymentMethod ? ` · ${paymentCopy(order)}` : ""}
          </p>
          <div className="order-detail__total">
            <span>Tổng theo đơn</span>
            <strong>{formatAmount(total, currency)}</strong>
          </div>

          <h3>Hành động khả dụng</h3>
          <OrderActions order={order} onChanged={refresh} />
          {!order.allowedActions?.length ? <p className="order-detail__no-actions">Không có thao tác nào được backend cho phép ở trạng thái hiện tại.</p> : null}
        </aside>
      </div>
    </article>
  );
}
