import type { components } from "@covie/api-client";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { OrderStatusLabel } from "@/features/orders/order-status";

type OrderSummary = components["schemas"]["OrderSummaryResponse"];
type MerchantLocation = components["schemas"]["MerchantLocationResponse"];
type NeedMatch = components["schemas"]["MerchantNeedMatchResponse"];
type MerchantReputation = components["schemas"]["MerchantReputationSummary"];

function distanceCopy(distanceMeters?: number) {
  if (distanceMeters == null) return undefined;
  if (distanceMeters < 1000) return `${Math.max(0, Math.round(distanceMeters))} m`;
  return `${(distanceMeters / 1000).toFixed(distanceMeters < 10000 ? 1 : 0)} km`;
}

export function SellerDashboard({
  merchantId,
  orders,
  needMatches,
  locations,
  reputation,
}: {
  merchantId: string;
  orders: OrderSummary[];
  needMatches: NeedMatch[];
  locations: MerchantLocation[];
  reputation?: MerchantReputation;
}) {
  const base = `/seller/${encodeURIComponent(merchantId)}`;

  return (
    <div className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center</p>
        <h1>Tổng quan vận hành</h1>
        <p>Các mục dưới đây là dữ liệu đang tải từ API hiện tại, không phải thống kê tổng hợp hay doanh thu suy diễn ở client.</p>
      </header>

      <section className="seller-metric-grid" aria-label="Tóm tắt dữ liệu hiện tại">
        <div className="seller-metric"><span>Đơn ở trang gần nhất</span><strong>{orders.length}</strong></div>
        <div className="seller-metric"><span>Nhu cầu ở trang gần nhất</span><strong>{needMatches.length}</strong></div>
        <div className="seller-metric"><span>Điểm bán được trả về</span><strong>{locations.length}</strong></div>
      </section>

      <section className="seller-section" aria-labelledby="seller-reputation-heading">
        <div className="seller-section__heading">
          <h2 id="seller-reputation-heading">Độ tin cậy cửa hàng</h2>
        </div>
        {reputation ? (
          <dl className="seller-order-facts">
            <div><dt>Đơn đủ điều kiện</dt><dd>{reputation.eligibleOrders ?? "—"}</dd></div>
            <div><dt>Đơn hoàn tất</dt><dd>{reputation.completedOrders ?? "—"}</dd></div>
            <div><dt>Tỷ lệ hoàn tất</dt><dd>{reputation.completionRateBasisPoints != null ? `${(reputation.completionRateBasisPoints / 100).toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%` : "—"}</dd></div>
          </dl>
        ) : (
          <p className="seller-muted">Backend public merchant view hiện không trả reputation khả dụng cho trạng thái cửa hàng này.</p>
        )}
      </section>

      <section className="seller-section" aria-labelledby="seller-recent-orders">
        <div className="seller-section__heading">
          <h2 id="seller-recent-orders">Đơn gần đây</h2>
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
                <OrderStatusLabel status={order.status} />
              </Link>
            ) : null)}
          </div>
        ) : (
          <EmptyState title="Chưa có đơn gần đây" description="Đơn mới sẽ xuất hiện tại đây khi backend trả về dữ liệu." />
        )}
      </section>

      <section className="seller-section" aria-labelledby="seller-needs-heading">
        <div className="seller-section__heading">
          <h2 id="seller-needs-heading">Nhu cầu phù hợp</h2>
          <Link href={`${base}/needs`}>Mở danh sách</Link>
        </div>
        {needMatches.length ? (
          <div className="seller-row-list">
            {needMatches.slice(0, 5).map((match, index) => {
              const need = match.need;
              const location = match.resolvedLocation;
              const distance = distanceCopy(match.distanceMeters);
              return (
                <Link className="seller-row" key={need?.id ?? `${location?.id ?? "need"}-${index}`} href={`${base}/needs`}>
                  <div>
                    <strong>{need?.title ?? "Nhu cầu gần bạn"}</strong>
                    <small>{[location?.name, match.buyerArea?.label, distance].filter(Boolean).join(" · ") || "Theo phạm vi backend trả về"}</small>
                  </div>
                  {match.alreadyResponded ? <span className="status-chip">Đã phản hồi</span> : null}
                </Link>
              );
            })}
          </div>
        ) : (
          <EmptyState title="Chưa có nhu cầu phù hợp" description="CoVie chỉ hiển thị các kết quả backend cho phép trong phạm vi cửa hàng của bạn." />
        )}
      </section>

      <section className="seller-section" aria-labelledby="seller-location-heading">
        <div className="seller-section__heading">
          <h2 id="seller-location-heading">Điểm bán</h2>
          <Link href={`${base}/locations`}>Quản lý</Link>
        </div>
        {locations.length ? (
          <div className="seller-row-list">
            {locations.slice(0, 3).map((location) => location.id ? (
              <Link className="seller-row" key={location.id} href={`${base}/locations/${encodeURIComponent(location.id)}`}>
                <div>
                  <strong>{location.name ?? "Điểm bán"}</strong>
                  <small>{location.addressText ?? location.timezone ?? "Chưa có mô tả địa điểm"}</small>
                </div>
                <StatusChip status={location.status} />
              </Link>
            ) : null)}
          </div>
        ) : (
          <EmptyState
            title="Chưa có điểm bán"
            description="Tạo điểm bán để cấu hình pickup, giao hàng, dịch vụ và phạm vi phục vụ."
            action={<Link className="seller-button" href={`${base}/locations`}>Thêm điểm bán</Link>}
          />
        )}
      </section>
    </div>
  );
}
