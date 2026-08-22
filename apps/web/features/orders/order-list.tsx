"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { merchantOrders, myOrders, type OrderSummary } from "./order-api";
import { OrderStatusLabel } from "./order-status";

export function OrderList({ sellerBase, merchantId }: { sellerBase?: string; merchantId?: string }) {
  const [orders, setOrders] = useState<OrderSummary[]>([]); const [error, setError] = useState<string>();
  useEffect(() => { void (merchantId ? merchantOrders(merchantId) : myOrders()).then(setOrders).catch((e: Error) => setError(e.message)); }, [merchantId]);
  if (error) return <p role="alert">{error}</p>;
  return <section className="orders-ledger">{orders.length === 0 ? <p>Chưa có đơn hàng nào.</p> : orders.map((order) => <Link className="order-row" key={order.id} href={sellerBase ? `${sellerBase}/${order.id}` : `/orders/${order.id}`}><span><strong>{order.orderCode}</strong><small>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small></span><OrderStatusLabel status={order.status} /></Link>)}</section>;
}
