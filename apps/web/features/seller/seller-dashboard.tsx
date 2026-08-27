import type { components } from "@covie/api-client";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { OrderStatusLabel } from "@/features/orders/order-status";

type OrderSummary = components["schemas"]["OrderSummaryResponse"];
type MerchantLocation = components["schemas"]["MerchantLocationResponse"];
type NeedMatch = components["schemas"]["MerchantNeedMatchResponse"];
type MerchantStatus = components["schemas"]["MerchantManagementResponse"]["status"];

function distanceCopy(distanceMeters?: number) {
  if (distanceMeters == null) return undefined;
  if (distanceMeters < 1000) return `${Math.max(0, Math.round(distanceMeters))} m`;
  return `${(distanceMeters / 1000).toFixed(distanceMeters < 10000 ? 1 : 0)} km`;
}

function merchantStatusCopy(status?: MerchantStatus) {
  switch (status) {
    case "ACTIVE": return { label: "Đang hoạt động", note: "Hồ sơ công khai" };
    case "PAUSED": return { label: "Tạm dừng", note: "Một số tính năng có thể bị giới hạn" };
    case "SUSPENDED": return { label: "Bị hạn chế", note: "Một số tính năng có thể bị giới hạn" };
    case "DELETED": return { label: "Đã đóng", note: "Cửa hàng không còn hoạt động" };
    default: return { label: "Trạng thái đã được cập nhật", note: "Theo dữ liệu hệ thống" };
  }
}

export function SellerDashboard({
  merchantId,
  merchantName,
  merchantStatus,
  orders,
  needMatches,
  locations,
}: {
  merchantId: string;
  merchantName?: string;
  merchantStatus?: MerchantStatus;
  orders: OrderSummary[];
  needMatches: NeedMatch[];
  locations: MerchantLocation[];
}) {
  const base = `/seller/${encodeURIComponent(merchantId)}`;
  const status = merchantStatusCopy(merchantStatus);

  return (
    <div className="seller-stack seller-dashboard">
      <header className="seller-dashboard__header">
        <h1>Tổng quan</h1>
        <p>{merchantName ? `${merchantName} · Trung tâm bán hàng` : "Trung tâm bán hàng"}</p>
      </header>

      <section className="seller-metric-grid" aria-label="Tóm tắt Seller Center">
        <article className="seller-metric">
          <span>Trạng thái cửa hàng</span>
          <strong>{status.label}</strong>
          <small>{status.note}</small>
        </article>
        <article className="seller-metric">
          <span>Điểm bán</span>
          <strong>{locations.length.toLocaleString("vi-VN")}</strong>
          <small>Đang quản lý</small>
        </article>
      </section>

      <section className="seller-section" aria-labelledby="seller-recent-orders">
        <div className="seller-section__heading">
          <div>
            <p className="seller-section__eyebrow">ĐƠN HÀNG</p>
            <h2 id="seller-recent-orders">Đơn gần đây</h2>
          </div>
          <Link href={`${base}/orders`}>Xem tất cả</Link>
        </div>
        {orders.length ? (
          <div className="seller-row-list">
            {orders.slice(0, 5).map((order) => order.id ? (
              <Link className="seller-row" key={order.id} href={`${base}/orders/${encodeURIComponent(order.id)}`}>
                <div>
                  <strong>{order.orderCode ?? "Đơn hàng"}</strong>
                  <small>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small>
                </div>
                <div className="seller-row__meta">
                  <OrderStatusLabel status={order.status} />
                  <span>Xem chi tiết</span>
                </div>
              </Link>
            ) : null)}
          </div>
        ) : (
          <EmptyState title="Chưa có đơn gần đây" description="Đơn mới sẽ xuất hiện tại đây khi có dữ liệu mới." />
        )}
      </section>

      <section className="seller-section" aria-labelledby="seller-needs-heading">
        <div className="seller-section__heading">
          <div>
            <p className="seller-section__eyebrow">NHU CẦU PHÙ HỢP</p>
            <h2 id="seller-needs-heading">Cần mua phù hợp</h2>
          </div>
          <Link href={`${base}/needs`}>Xem tất cả</Link>
        </div>
        {needMatches.length ? (
          <div className="seller-row-list">
            {needMatches.slice(0, 5).map((match, index) => {
              const need = match.need;
              const location = match.resolvedLocation;
              const distance = distanceCopy(match.distanceMeters);
              return (
                <Link className="seller-row seller-need-match-row" key={need?.id ?? `${location?.id ?? "need"}-${index}`} href={`${base}/needs`}>
                  <div>
                    <strong>{need?.title ?? "Nhu cầu gần bạn"}</strong>
                    <small>{[location?.name, match.buyerArea?.label, distance].filter(Boolean).join(" · ") || "Trong phạm vi điểm bán"}</small>
                  </div>
                  <span className="seller-row__meta">{match.alreadyResponded ? "Đã phản hồi" : "Xem nhu cầu"}</span>
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="Chưa có nhu cầu phù hợp" description="Các nhu cầu phù hợp với phạm vi cửa hàng sẽ xuất hiện tại đây." />
        )}
      </section>
    </div>
  );
}
