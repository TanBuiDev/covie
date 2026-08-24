"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { getOrder, type OrderDetail } from "./order-api";
import { OrderActions } from "./order-actions";
import { OrderStatusLabel } from "./order-status";
import { OrderTimeline } from "./order-timeline";

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

  if (error) return <ErrorState title="Không thể tải đơn hàng" description={error} onRetry={() => void refresh()} />;
  if (!order) return <LoadingState label="Đang tải đơn hàng…" />;

  return (
    <article className="order-detail">
      <header className="surface-header">
        <p className="eyebrow">Đơn {order.orderCode ?? order.id ?? ""}</p>
        <h1>{order.type === "SERVICE" ? "Đơn dịch vụ" : order.type === "PRODUCT" ? "Đơn sản phẩm" : "Đơn hàng"}</h1>
        <OrderStatusLabel status={order.status} />
      </header>

      <OrderTimeline
        status={order.status}
        createdAt={order.createdAt}
        confirmedAt={order.confirmedAt}
        completedAt={order.completedAt}
        closedAt={order.closedAt}
      />

      <section aria-labelledby="order-context-title">
        <h2 id="order-context-title">Thông tin giao dịch</h2>
        <dl className="detail-list">
          {order.merchantLocationId ? <><dt>Điểm bán</dt><dd>{order.merchantLocationId}</dd></> : null}
          {sellerMerchantId && order.buyerUserId ? <><dt>Người mua</dt><dd>Mã người dùng {order.buyerUserId}</dd></> : null}
        </dl>
        {sellerMerchantId ? <Link href={`/seller/${encodeURIComponent(sellerMerchantId)}/orders`}>Quay lại danh sách đơn</Link> : null}
      </section>

      <OrderActions order={order} onChanged={refresh} />
    </article>
  );
}
