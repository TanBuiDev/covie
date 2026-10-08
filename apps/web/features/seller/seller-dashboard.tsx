import type { components } from "@covie/api-client";
import Link from "next/link";
import { OrderStatusLabel } from "@/features/orders/order-status";
import {
  ArrowRight,
  CheckCircle,
  Gear,
  MagnifyingGlass,
  MapPin,
  MapPinLine,
  Megaphone,
  Package,
  PlusCircle,
  Sparkle,
  Storefront,
  Wrench,
} from "@phosphor-icons/react/dist/ssr";

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
    case "ACTIVE": return { label: "Đang hoạt động", note: "Sẵn sàng đón khách" };
    case "PAUSED": return { label: "Tạm dừng", note: "Một số tính năng bị giới hạn" };
    case "SUSPENDED": return { label: "Bị hạn chế", note: "Tài khoản cần kiểm tra" };
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
  const pendingOrders = orders.filter((o) => o.status === "PENDING_CONFIRMATION").length;

  return (
    <div className="seller-stack seller-dashboard">
      {/* HEADER SECTION */}
      <header className="seller-dashboard__header">
        <div className="seller-dashboard__header-content">
          <div>
            <h1>Tổng quan</h1>
            <p>{merchantName ? `${merchantName} · Trung tâm bán hàng` : "Trung tâm bán hàng"}</p>
          </div>
          <div className="seller-dashboard__header-actions">
            <Link className="seller-btn-header" href={`${base}/settings`}>
              <Gear size={16} weight="bold" />
              <span>Cài đặt gian hàng</span>
            </Link>
          </div>
        </div>
      </header>

      {/* SHOP PROFILE BANNER */}
      <section className="seller-shop-banner" aria-label="Hồ sơ gian hàng">
        <div className="seller-shop-banner__left">
          <div className="seller-shop-banner__avatar" aria-hidden="true">
            <Storefront size={34} weight="duotone" />
          </div>
          <div className="seller-shop-banner__details">
            <div className="seller-shop-banner__title-row">
              <h2>{merchantName ?? "Gian hàng CoVie"}</h2>
              <span className="seller-shop-banner__badge">Gian hàng nội khu</span>
            </div>
            <p className="seller-shop-banner__sub">
              Trạng thái: <strong>{status.label}</strong> · {status.note}
            </p>
          </div>
        </div>
        <div className="seller-shop-banner__right">
          <div className="seller-shop-banner__stat">
            <span>Điểm giao nhận</span>
            <strong>{locations.length > 0 ? `${locations.length} điểm` : "Chưa có"}</strong>
          </div>
          <div className="seller-shop-banner__stat">
            <span>Đơn chờ xử lý</span>
            <strong>{pendingOrders > 0 ? `${pendingOrders} đơn` : "0 đơn"}</strong>
          </div>
        </div>
      </section>

      {/* STORE SETUP PROGRESS CHECKLIST (FOR NEW / UNCONFIGURED SHOPS) */}
      {locations.length === 0 ? (
        <section className="seller-setup-checklist" aria-label="Hoàn thiện thiết lập gian hàng">
          <div className="seller-setup-checklist__header">
            <div className="seller-setup-checklist__title">
              <Sparkle size={20} weight="fill" className="seller-setup-checklist__sparkle" />
              <div>
                <strong>Hoàn tất thiết lập gian hàng để sẵn sàng đón khách</strong>
                <p>Thực hiện các bước cơ bản để cư dân toà nhà có thể tìm thấy và đặt mua từ bạn.</p>
              </div>
            </div>
            <span className="seller-setup-checklist__progress-badge">Bước 2/3</span>
          </div>
          <div className="seller-setup-checklist__steps">
            <div className="seller-setup-step seller-setup-step--done">
              <div className="seller-setup-step__icon">
                <CheckCircle size={22} weight="fill" />
              </div>
              <div className="seller-setup-step__info">
                <strong>Khởi tạo gian hàng</strong>
                <small>Đã hoàn thành</small>
              </div>
            </div>
            <div className="seller-setup-step seller-setup-step--current">
              <div className="seller-setup-step__icon">
                <MapPinLine size={22} weight="duotone" />
              </div>
              <div className="seller-setup-step__info">
                <strong>Ghim điểm bán sảnh/tầng</strong>
                <small>Chưa thiết lập vị trí nhận</small>
              </div>
              <Link href={`${base}/locations`} className="seller-setup-step__btn">
                Thiết lập ngay
              </Link>
            </div>
            <div className="seller-setup-step">
              <div className="seller-setup-step__icon">
                <PlusCircle size={22} weight="duotone" />
              </div>
              <div className="seller-setup-step__info">
                <strong>Đăng món hoặc sản phẩm</strong>
                <small>Lên kệ thực đơn đầu tiên</small>
              </div>
              <Link href={`${base}/catalog`} className="seller-setup-step__btn seller-setup-step__btn--ghost">
                Lên kệ món
              </Link>
            </div>
          </div>
        </section>
      ) : null}

      {/* METRIC GRID */}
      <section className="seller-metric-grid" aria-label="Tóm tắt Seller Center">
        <article className="seller-metric seller-metric--status">
          <div className="seller-metric__top">
            <div className="seller-metric__icon seller-metric__icon--emerald" aria-hidden="true">
              <Storefront size={22} weight="duotone" />
            </div>
            <span>Trạng thái cửa hàng</span>
          </div>
          <strong>{status.label}</strong>
          <small>{status.note}</small>
        </article>

        <article className="seller-metric seller-metric--locations">
          <div className="seller-metric__top">
            <div className="seller-metric__icon seller-metric__icon--blue" aria-hidden="true">
              <MapPin size={22} weight="duotone" />
            </div>
            <span>Điểm bán</span>
          </div>
          <strong>{locations.length.toLocaleString("vi-VN")}</strong>
          <small>Đang quản lý</small>
        </article>

        <article className="seller-metric seller-metric--pending">
          <div className="seller-metric__top">
            <div className="seller-metric__icon seller-metric__icon--amber" aria-hidden="true">
              <Package size={22} weight="duotone" />
            </div>
            <span>Đơn chờ chuẩn bị</span>
          </div>
          <strong>{pendingOrders} đơn</strong>
          <small>Cần xác nhận giao</small>
        </article>

        <article className="seller-metric seller-metric--needs">
          <div className="seller-metric__top">
            <div className="seller-metric__icon seller-metric__icon--purple" aria-hidden="true">
              <Sparkle size={22} weight="duotone" />
            </div>
            <span>Cần mua phù hợp</span>
          </div>
          <strong>{needMatches.length} nhu cầu</strong>
          <small>Trong phạm vi điểm bán</small>
        </article>
      </section>

      {/* QUICK ACTIONS */}
      <section className="seller-quick-actions" aria-label="Thao tác nhanh">
        <h2 className="seller-quick-actions__title">Thao tác nhanh</h2>
        <div className="seller-quick-actions__grid">
          <Link className="seller-quick-action-btn" href={`${base}/catalog`}>
            <div className="seller-quick-action-icon seller-quick-action-icon--emerald" aria-hidden="true">
              <PlusCircle size={24} weight="duotone" />
            </div>
            <div className="seller-quick-action-text">
              <strong>Quản lý sản phẩm</strong>
              <small>Thêm món & menu</small>
            </div>
          </Link>

          <Link className="seller-quick-action-btn" href={`${base}/needs`}>
            <div className="seller-quick-action-icon seller-quick-action-icon--purple" aria-hidden="true">
              <MagnifyingGlass size={24} weight="duotone" />
            </div>
            <div className="seller-quick-action-text">
              <strong>Tìm nhu cầu cư dân</strong>
              <small>Phản hồi cần mua</small>
            </div>
          </Link>

          <Link className="seller-quick-action-btn" href={`${base}/locations`}>
            <div className="seller-quick-action-icon seller-quick-action-icon--blue" aria-hidden="true">
              <MapPinLine size={24} weight="duotone" />
            </div>
            <div className="seller-quick-action-text">
              <strong>Cấu hình điểm bán</strong>
              <small>Địa chỉ & bán kính</small>
            </div>
          </Link>

          <Link className="seller-quick-action-btn" href={`${base}/ads`}>
            <div className="seller-quick-action-icon seller-quick-action-icon--amber" aria-hidden="true">
              <Megaphone size={24} weight="duotone" />
            </div>
            <div className="seller-quick-action-text">
              <strong>Quảng cáo nội khu</strong>
              <small>Tăng hiển thị shop</small>
            </div>
          </Link>
        </div>
      </section>

      {/* DUAL SECTIONS */}
      <div className="seller-dashboard-columns">
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
                <Link className="seller-row seller-order-card" key={order.id} href={`${base}/orders/${encodeURIComponent(order.id)}`}>
                  <div className="seller-order-card__info">
                    <div className="seller-order-card__icon" aria-hidden="true">
                      {order.type === "SERVICE" ? (
                        <Wrench size={18} weight="duotone" />
                      ) : (
                        <Package size={18} weight="duotone" />
                      )}
                    </div>
                    <div>
                      <strong>{order.orderCode ?? "Đơn hàng"}</strong>
                      <small>{order.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm"}</small>
                    </div>
                  </div>
                  <div className="seller-row__meta">
                    <OrderStatusLabel status={order.status} />
                    <span className="seller-row__view-link">
                      <span>Xem chi tiết</span>
                      <ArrowRight size={13} weight="bold" />
                    </span>
                  </div>
                </Link>
              ) : null)}
            </div>
          ) : (
            <div className="seller-actionable-empty">
              <div className="seller-actionable-empty__icon seller-actionable-empty__icon--emerald" aria-hidden="true">
                <Package size={44} weight="duotone" />
              </div>
              <div className="seller-actionable-empty__body">
                <h3>Chưa có đơn gần đây</h3>
                <p>Đơn mới sẽ xuất hiện tại đây khi có dữ liệu mới.</p>
                <Link href={`${base}/catalog`} className="seller-empty-cta">
                  <span>Đăng sản phẩm đón đơn ngay</span>
                  <ArrowRight size={14} weight="bold" />
                </Link>
              </div>
            </div>
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
                    <div className="seller-need-card__info">
                      <div className="seller-need-card__icon" aria-hidden="true">
                        <Sparkle size={18} weight="duotone" />
                      </div>
                      <div>
                        <strong>{need?.title ?? "Nhu cầu gần bạn"}</strong>
                        <small>{[location?.name, match.buyerArea?.label, distance].filter(Boolean).join(" · ") || "Trong phạm vi điểm bán"}</small>
                      </div>
                    </div>
                    <span className="seller-row__meta">
                      {match.alreadyResponded ? (
                        <span className="seller-tag-responded">Đã phản hồi</span>
                      ) : (
                        <span className="seller-tag-action">
                          <span>Xem nhu cầu</span>
                          <ArrowRight size={13} weight="bold" />
                        </span>
                      )}
                    </span>
                  </Link>
                );
              })}
            </div>
          ) : (
            <div className="seller-actionable-empty">
              <div className="seller-actionable-empty__icon seller-actionable-empty__icon--purple" aria-hidden="true">
                <Sparkle size={44} weight="duotone" />
              </div>
              <div className="seller-actionable-empty__body">
                <h3>Chưa có nhu cầu phù hợp</h3>
                <p>Các nhu cầu phù hợp với phạm vi cửa hàng sẽ xuất hiện tại đây.</p>
                <Link href={`${base}/needs`} className="seller-empty-cta">
                  <span>Khám phá nhu cầu cư dân</span>
                  <ArrowRight size={14} weight="bold" />
                </Link>
              </div>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
