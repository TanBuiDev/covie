"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useEffect, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { formatDateTime } from "@/lib/format/time";
import { merchantOrders, myOrders, type OrderSummary } from "./order-api";
import { OrderStatusLabel } from "./order-status";

type Location = components["schemas"]["MerchantLocationResponse"];

export function OrderList({
  sellerBase,
  merchantId,
  locations = [],
}: {
  sellerBase?: string;
  merchantId?: string;
  locations?: Location[];
}) {
  const [orders, setOrders] = useState<OrderSummary[]>();
  const [nextCursor, setNextCursor] = useState<string>();
  const [locationId, setLocationId] = useState("");
  const [error, setError] = useState<string>();
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    let active = true;
    setOrders(undefined);
    setNextCursor(undefined);
    setError(undefined);

    void (merchantId ? merchantOrders(merchantId, { locationId: locationId || undefined, limit: 20 }) : myOrders())
      .then((page) => {
        if (!active) return;
        setOrders(page.items ?? []);
        setNextCursor(merchantId ? page.nextCursor : undefined);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng.");
      });

    return () => {
      active = false;
    };
  }, [merchantId, locationId, reloadKey]);

  async function loadMore() {
    if (!merchantId || !nextCursor || loadingMore) return;
    setLoadingMore(true);
    setError(undefined);
    try {
      const page = await merchantOrders(merchantId, { cursor: nextCursor, locationId: locationId || undefined, limit: 20 });
      setOrders((current) => [...(current ?? []), ...(page.items ?? [])]);
      setNextCursor(page.nextCursor);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tải thêm đơn hàng.");
    } finally {
      setLoadingMore(false);
    }
  }

  return (
    <section className="seller-stack" aria-label="Danh sách đơn hàng">
      {merchantId && locations.length ? (
        <label className="seller-order-location-filter">
          Điểm bán
          <select value={locationId} onChange={(event) => setLocationId(event.target.value)}>
            <option value="">Tất cả điểm bán được phép</option>
            {locations.map((location) => location.id ? (
              <option value={location.id} key={location.id}>{location.name ?? "Điểm bán"}</option>
            ) : null)}
          </select>
        </label>
      ) : null}

      {error && !orders ? (
        <ErrorState
          title="Không thể tải đơn hàng"
          description={error}
          action={<button type="button" onClick={() => setReloadKey((value) => value + 1)}>Thử lại</button>}
        />
      ) : null}

      {!error && !orders ? <LoadingState label="Đang tải đơn hàng…" /> : null}

      {orders?.length === 0 ? (
        <EmptyState
          title="Chưa có đơn hàng"
          description={locationId ? "Không có đơn trong điểm bán đang chọn." : "Đơn mới sẽ xuất hiện ở đây khi backend trả dữ liệu."}
        />
      ) : null}

      {orders?.length ? (
        <div className="orders-ledger">
          {orders.map((order) => {
            if (!order.id) return null;
            const href = sellerBase ? `${sellerBase}/${encodeURIComponent(order.id)}` : `/orders/${encodeURIComponent(order.id)}`;
            return (
              <Link className="order-row" key={order.id} href={href}>
                <span>
                  <strong>{order.orderCode ?? "Đơn hàng"}</strong>
                  <small>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small>
                  {order.createdAt ? <small>{formatDateTime(order.createdAt)}</small> : null}
                </span>
                <OrderStatusLabel status={order.status} />
              </Link>
            );
          })}
        </div>
      ) : null}

      {error && orders ? <p role="alert">{error}</p> : null}
      {nextCursor ? (
        <button type="button" onClick={() => void loadMore()} disabled={loadingMore}>
          {loadingMore ? "Đang tải…" : "Tải thêm đơn"}
        </button>
      ) : null}
    </section>
  );
}
