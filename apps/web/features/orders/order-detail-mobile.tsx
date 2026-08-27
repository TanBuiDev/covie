"use client";

import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { formatMoney } from "@/lib/format/money";
import type { OrderDetail } from "./order-api";
import { OrderActions } from "./order-actions";
import { OrderStatusLabel } from "./order-status";
import { OrderTimeline } from "./order-timeline";
import styles from "./order-detail-mobile.module.css";

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

export function OrderDetailMobile({ order, error, onChanged }: { order: OrderDetail; error?: string; onChanged: () => Promise<void> }) {
  const router = useRouter();
  const address = firstText(order.fulfillment?.address, ["addressText", "formattedAddress", "label"]);
  const pickup = firstText(order.fulfillment?.pickupLocation, ["addressText", "name", "label"]);
  const contact = firstText(order.fulfillment?.merchantContact, ["value", "phone", "zalo", "messenger", "label"]);
  const currency = order.pricing?.currency ?? order.items?.find((item) => item.currency)?.currency;
  const total = order.pricing?.totalAmount ?? order.pricing?.knownTotal;
  const merchantIdentity = order.merchant?.merchantId ? `Người bán · ${order.merchant.merchantId}` : "Người bán";

  return (
    <article className={styles.mobile}>
      <header className={styles.appBar}>
        <button className={styles.back} type="button" aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button>
        <h1>Đơn {order.orderCode ?? order.id ?? ""}</h1>
      </header>

      <div className={styles.body}>
        <div className={styles.statusRow}>
          <h2>{order.orderCode ?? "Đơn hàng"}</h2>
          <OrderStatusLabel status={order.status} />
        </div>

        {error ? <p className={styles.alert} role="status">{error}</p> : null}

        <section className={styles.merchant} aria-label="Thông tin người bán và nhận hàng">
          <strong>{merchantIdentity}</strong>
          {address ? <p>{address}</p> : pickup ? <p>{pickup}</p> : <p>Thông tin địa điểm chưa có trong phản hồi hiện tại.</p>}
          {contact ? <small>Liên hệ: {contact}</small> : null}
        </section>

        <div className={styles.timeline}>
          <OrderTimeline
            status={order.status}
            events={order.timeline}
            createdAt={order.createdAt}
            confirmedAt={order.confirmedAt}
            completedAt={order.completedAt}
            closedAt={order.closedAt}
          />
        </div>

        {order.cancellation ? (
          <section className={styles.contextCard} aria-label="Yêu cầu hủy">
            <strong>Yêu cầu hủy</strong>
            <p>Trạng thái: {order.cancellation.status ?? "—"}</p>
            {order.cancellation.reasonText || order.cancellation.reasonCode ? <p>{order.cancellation.reasonText ?? order.cancellation.reasonCode}</p> : null}
            {order.cancellation.responseNote ? <p>{order.cancellation.responseNote}</p> : null}
          </section>
        ) : null}

        {order.issue ? (
          <section className={`${styles.contextCard} ${styles.issue}`} aria-label="Vấn đề đơn hàng">
            <strong>Vấn đề đang được ghi nhận</strong>
            <p>{order.issue.reasonText ?? order.issue.type ?? "CoVie đang lưu trạng thái do hệ thống trả về."}</p>
            {order.issue.status ? <small>{order.issue.status}</small> : null}
          </section>
        ) : null}

        <section className={styles.summary} aria-label="Tóm tắt đơn hàng">
          <p className={styles.summaryLabel}>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</p>
          {(order.items ?? []).map((item, index) => (
            <p className={styles.item} key={item.id ?? `${item.locationVariantId ?? "item"}-${index}`}>
              <span>{item.quantity ?? 1} × {itemName(item.snapshot)}</span>
              <strong>{formatAmount(item.lineTotal ?? (item.unitPrice != null && item.quantity != null ? item.unitPrice * item.quantity : item.unitPrice), item.currency ?? currency)}</strong>
            </p>
          ))}
          {!order.items?.length ? <p>Chi tiết sản phẩm chưa có trong phản hồi hiện tại.</p> : null}
          <p>{fulfillmentCopy(order)}{order.pricing?.shippingFee != null && currency ? ` · Phí giao ${formatAmount(order.pricing.shippingFee, currency)}` : ""}{order.paymentMethod ? ` · ${paymentCopy(order)}` : ""}</p>
          <div className={styles.total}><span>Tổng theo đơn</span><strong>{formatAmount(total, currency)}</strong></div>
        </section>

        <section className={styles.actions} aria-label="Hành động khả dụng">
          <h3>Hành động khả dụng</h3>
          <OrderActions order={order} onChanged={onChanged} />
          {!order.allowedActions?.length ? <p className={styles.noActions}>Không có thao tác nào được backend cho phép ở trạng thái hiện tại.</p> : null}
        </section>
      </div>
    </article>
  );
}
