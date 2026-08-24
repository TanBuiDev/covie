"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { LoadingState } from "@/components/ui/loading-state";
import { merchantOrders, myOrders, type OrderSummary } from "./order-api";
import { OrderStatusLabel } from "./order-status";

export function OrderList({ sellerBase, merchantId }: { sellerBase?: string; merchantId?: string }) {
  const [orders, setOrders] = useState<OrderSummary[]>();
  const [error, setError] = useState<string>();

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

  if (error) return <p role="alert">{error}</p>;
  if (!orders) return <LoadingState label="Đang tải đơn hàng…" />;
  if (!orders.length) return <section className="empty-state"><h2>Chưa có đơn hàng nào.</h2></section>;

  return (
    <section className="orders-ledger" aria-label="Danh sách đơn hàng">
      {orders.map((order) => {
        if (!order.id) return null;
        const href = sellerBase ? `${sellerBase}/${order.id}` : `/orders/${order.id}`;
        return (
          <Link className="order-row" key={order.id} href={href}>
            <span>
              <strong>{order.orderCode ?? "Đơn hàng"}</strong>
              <small>{order.type === "SERVICE" ? "Dịch vụ" : order.type === "PRODUCT" ? "Sản phẩm" : "Nội dung"}</small>
            </span>
            <OrderStatusLabel status={order.status} />
          </Link>
        );
      })}
    </section>
  );
}
