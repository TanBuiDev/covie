"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { formatDateTime } from "@/lib/format/time";
import { merchantOrders, myOrders, type OrderSummary } from "./order-api";
import { OrderStatusLabel } from "./order-status";
import styles from "./order-list-mobile.module.css";

export function OrderList({ sellerBase, merchantId }: { sellerBase?: string; merchantId?: string }) {
  const [orders, setOrders] = useState<OrderSummary[]>();
  const [error, setError] = useState<string>();
  const buyer = !sellerBase && !merchantId;

  useEffect(() => {
    let active = true;
    void (merchantId ? merchantOrders(merchantId) : myOrders())
      .then((page) => {
        if (active) setOrders(page.items ?? []);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng.");
      });
    return () => {
      active = false;
    };
  }, [merchantId]);

  if (error) {
    return <section className={buyer ? `${styles.state} buyer-orders-state buyer-orders-error` : "empty-state"} role="alert"><h2>Không thể tải đơn hàng</h2><p>{error}</p></section>;
  }
  if (!orders) return <div className={buyer ? `${styles.state} buyer-orders-state` : undefined}><LoadingState label="Đang tải đơn hàng…" /></div>;
  if (!orders.length) {
    return <section className={buyer ? `${styles.state} buyer-orders-state buyer-orders-empty` : "empty-state"}><h2>Chưa có đơn hàng nào.</h2>{buyer ? <><p>Khi bạn đặt mua sản phẩm hoặc dịch vụ, đơn sẽ xuất hiện tại đây.</p><Link href="/">Khám phá marketplace</Link></> : null}</section>;
  }

  return (
    <section className={`orders-ledger ${buyer ? `buyer-orders-ledger ${styles.ledger}` : ""}`} aria-label="Danh sách đơn hàng">
      {orders.map((order) => {
        if (!order.id) return null;
        const href = sellerBase ? `${sellerBase}/${order.id}` : `/orders/${order.id}`;
        return (
          <Link className={`order-row ${buyer ? `buyer-order-row ${styles.card}` : ""}`} key={order.id} href={href}>
            <span className={`buyer-order-main ${buyer ? styles.meta : ""}`}>
              <span className={`buyer-order-heading ${buyer ? styles.heading : ""}`}><strong>{order.orderCode ?? "Đơn hàng"}</strong><OrderStatusLabel status={order.status} /></span>
              <small className={buyer ? styles.type : undefined}>{order.type === "SERVICE" ? "Đơn dịch vụ" : order.type === "PRODUCT" ? "Đơn sản phẩm" : "Đơn hàng"}</small>
              {order.createdAt ? <small>Tạo {formatDateTime(order.createdAt)}</small> : null}
            </span>
            {buyer ? <span className={`buyer-order-detail-action ${styles.detailAction}`}>Xem chi tiết</span> : <OrderStatusLabel status={order.status} />}
          </Link>
        );
      })}
    </section>
  );
}
