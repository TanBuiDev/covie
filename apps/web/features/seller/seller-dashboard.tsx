import Link from "next/link";
import type { components } from "@covie/api-client";
import { OrderStatusLabel } from "@/features/orders/order-status";
import { sellerBasePath } from "./seller-context";

type OrderSummary = components["schemas"]["OrderSummaryResponse"];
type MerchantLocation = components["schemas"]["MerchantLocationResponse"];

export function SellerDashboard({
  merchantId,
  orders,
  locations,
}: {
  merchantId: string;
  orders: OrderSummary[];
  locations: MerchantLocation[];
}) {
  const base = sellerBasePath(merchantId);
  return (
    <div className="seller-dashboard">
      <header className="surface-header">
        <p className="eyebrow">Seller Center</p>
        <h1>Tổng quan vận hành</h1>
        <p>Hiển thị các mục gần đây để xử lý, không phải báo cáo doanh thu hay tổng số toàn hệ thống.</p>
      </header>

      <section aria-labelledby="seller-orders-title">
        <div className="section-heading">
          <h2 id="seller-orders-title">Đơn gần đây</h2>
          <Link href={`${base}/orders`}>Xem tất cả</Link>
        </div>
        {orders.length ? (
          <ul className="compact-list">
            {orders.slice(0, 5).map((order) => order.id ? (
              <li key={order.id}>
                <Link href={`${base}/orders/${order.id}`}>{order.orderCode ?? "Đơn hàng"}</Link>{" "}
                <OrderStatusLabel status={order.status} />
              </li>
            ) : null)}
          </ul>
        ) : <p>Chưa có đơn hàng gần đây.</p>}
      </section>

      <section aria-labelledby="seller-needs-title">
        <div className="section-heading">
          <h2 id="seller-needs-title">Cần mua phù hợp</h2>
          <Link href={`${base}/needs`}>Mở danh sách Cần mua</Link>
        </div>
        <p>Danh sách matching chi tiết được tải tại màn hình Cần mua theo quyền và điểm bán hiện tại.</p>
      </section>

      <section aria-labelledby="seller-locations-title">
        <div className="section-heading">
          <h2 id="seller-locations-title">Điểm bán</h2>
          <Link href={`${base}/locations`}>Quản lý điểm bán</Link>
        </div>
        {locations.length ? (
          <ul className="compact-list">
            {locations.slice(0, 5).map((location) => location.id ? (
              <li key={location.id}>
                <Link href={`${base}/locations/${location.id}`}>{location.name ?? location.addressText ?? "Điểm bán"}</Link>
              </li>
            ) : null)}
          </ul>
        ) : <p>Chưa có điểm bán nào.</p>}
      </section>
    </div>
  );
}
