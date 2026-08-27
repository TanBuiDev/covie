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
import styles from "./order-list-mobile.module.css";

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
  const buyer = !sellerBase && !merchantId;

  useEffect(() => {
    let active = true;
    void (merchantId ? merchantOrders(merchantId, { locationId: locationId || undefined, limit: 20 }) : myOrders())
      .then((page) => {
        if (!active) return;
        setOrders(page.items ?? []);
        setNextCursor(merchantId ? page.nextCursor : undefined);
        setError(undefined);
      })
      .catch((cause: unknown) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng.");
      });
    return () => { active = false; };
  }, [merchantId, locationId, reloadKey]);

  function changeLocation(nextLocationId: string) {
    setLocationId(nextLocationId);
    setOrders(undefined);
    setNextCursor(undefined);
    setError(undefined);
  }

  function reload() {
    setOrders(undefined);
    setNextCursor(undefined);
    setError(undefined);
    setReloadKey((value) => value + 1);
  }

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

  if (buyer) {
    if (error) return <section className={`${styles.state} buyer-orders-state buyer-orders-error`} role="alert"><h2>Không thể tải đơn hàng</h2><p>{error}</p><button type="button" onClick={reload}>Thử lại</button></section>;
    if (!orders) return <div className={`${styles.state} buyer-orders-state`}><LoadingState label="Đang tải đơn hàng…" /></div>;
    if (!orders.length) return <section className={`${styles.state} buyer-orders-state buyer-orders-empty`}><h2>Chưa có đơn hàng nào.</h2><p>Khi bạn đặt mua sản phẩm hoặc dịch vụ, đơn sẽ xuất hiện tại đây.</p><Link href="/">Khám phá marketplace</Link></section>;
    return (
      <section className={`orders-ledger buyer-orders-ledger ${styles.ledger}`} aria-label="Danh sách đơn hàng">
        {orders.map((order) => order.id ? (
          <Link className={`order-row buyer-order-row ${styles.card}`} key={order.id} href={`/orders/${encodeURIComponent(order.id)}`}>
            <span className={`buyer-order-main ${styles.meta}`}>
              <span className={`buyer-order-heading ${styles.heading}`}><strong>{order.orderCode ?? "Đơn hàng"}</strong><OrderStatusLabel status={order.status} /></span>
              <small className={styles.type}>{order.type === "SERVICE" ? "Đơn dịch vụ" : order.type === "PRODUCT" ? "Đơn sản phẩm" : "Đơn hàng"}</small>
              {order.createdAt ? <small>Tạo {formatDateTime(order.createdAt)}</small> : null}
            </span>
            <span className={`buyer-order-detail-action ${styles.detailAction}`}>Xem chi tiết</span>
          </Link>
        ) : null)}
      </section>
    );
  }

  return (
    <section className="seller-stack" aria-label="Danh sách đơn hàng">
      {merchantId && locations.length ? (
        <label className="seller-order-location-filter">Điểm bán
          <select value={locationId} onChange={(event) => changeLocation(event.target.value)}>
            <option value="">Tất cả điểm bán được phân công</option>
            {locations.map((location) => location.id ? <option value={location.id} key={location.id}>{location.name ?? "Điểm bán"}</option> : null)}
          </select>
          <small>Có thể lọc theo một điểm bán cụ thể</small>
        </label>
      ) : null}
      {error && !orders ? <ErrorState title="Không thể tải đơn hàng" description={error} action={<button type="button" onClick={reload}>Thử lại</button>} /> : null}
      {!error && !orders ? <LoadingState label="Đang tải đơn hàng…" /> : null}
      {orders?.length === 0 ? <EmptyState title="Chưa có đơn hàng" description={locationId ? "Không có đơn trong điểm bán đang chọn." : "Đơn mới sẽ xuất hiện ở đây khi có dữ liệu mới."} /> : null}
      {orders?.length ? (
        <div className="seller-order-list-block"><h2>Mới nhất</h2><div className="seller-order-list-cards">
          {orders.map((order) => order.id ? (
            <Link className="seller-order-list-card" key={order.id} href={sellerBase ? `${sellerBase}/${encodeURIComponent(order.id)}` : `/orders/${encodeURIComponent(order.id)}`}>
              <header><strong>{order.orderCode ?? "Đơn hàng"}</strong><OrderStatusLabel status={order.status} /></header>
              <p>{order.type === "SERVICE" ? "Đơn dịch vụ" : "Đơn sản phẩm"}</p>
              {order.createdAt ? <small>Tạo lúc {formatDateTime(order.createdAt)}</small> : null}
              <span className="seller-order-open">Xem chi tiết</span>
            </Link>
          ) : null)}
        </div></div>
      ) : null}
      {error && orders ? <p role="alert">{error}</p> : null}
      {nextCursor ? <button className="seller-load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? "Đang tải…" : "Tải thêm"}</button> : null}
    </section>
  );
}
