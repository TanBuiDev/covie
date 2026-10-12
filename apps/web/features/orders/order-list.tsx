"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useEffect, useMemo, useRef, useState } from "react";
import {
  CaretRight,
  CheckCircle,
  Clock,
  ForkKnife,
  LockKey,
  MagnifyingGlass,
  Package,
  Receipt,
  Sparkle,
  Storefront,
  Truck,
  Wrench,
  X,
} from "@phosphor-icons/react";
import { EmptyState } from "@/components/ui/empty-state";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { formatDateTime } from "@/lib/format/time";
import { formatMoney } from "@/lib/format/money";
import { CovieApiError } from "@/lib/api/api-error";
import { merchantOrders, myOrders, type OrderSummary } from "./order-api";
import { OrderStatusLabel } from "./order-status";
import { SelectDropdown } from "@/components/ui/select-dropdown";
import styles from "./order-list-mobile.module.css";

function getItemVisual(name?: string | null, orderType?: string | null) {
  const lower = (name ?? "").toLowerCase();
  if (orderType === "SERVICE" || lower.includes("sửa") || lower.includes("dịch vụ") || lower.includes("bảo dưỡng")) {
    return {
      icon: <Wrench size={22} weight="duotone" />,
      bg: "#ecfdf5",
      color: "#059669",
    };
  }
  if (
    lower.includes("bánh") ||
    lower.includes("chè") ||
    lower.includes("trà") ||
    lower.includes("cà phê") ||
    lower.includes("cơm") ||
    lower.includes("phở") ||
    lower.includes("ăn") ||
    lower.includes("kem") ||
    lower.includes("nước") ||
    lower.includes("món") ||
    lower.includes("thực phẩm")
  ) {
    return {
      icon: <ForkKnife size={22} weight="duotone" />,
      bg: "#fffbeb",
      color: "#d97706",
    };
  }
  return {
    icon: <Package size={22} weight="duotone" />,
    bg: "#f1f5f9",
    color: "#475569",
  };
}

type Location = components["schemas"]["MerchantLocationResponse"];

function StoredOrderPrice({ order }: { order: OrderSummary }) {
  const pricing = order.pricing;
  if (!pricing) return null;
  const totalKnown = pricing.totalAmount != null;
  const amount = totalKnown ? pricing.totalAmount : pricing.knownTotal;
  if (amount == null || !pricing.currency) return null;
  return <div className={styles.pricing}>
    <small>{totalKnown ? "Tổng tiền" : "Số tiền đã biết"}</small>
    <strong>{formatMoney({ amount, currency: pricing.currency })}</strong>
    {!totalKnown && pricing.shippingFeeType === "NEGOTIATED" ? <small>Phí giao hàng cần thỏa thuận.</small> : null}
  </div>;
}

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
  const [isAuthError, setIsAuthError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [loadingMore, setLoadingMore] = useState(false);
  const [statusFilter, setStatusFilter] = useState<"ALL" | "PENDING" | "DELIVERING" | "COMPLETED" | "CANCELLED">("ALL");
  const [searchQuery, setSearchQuery] = useState("");
  const morePending = useRef(false);
  const generation = useRef(0);
  const [moreError, setMoreError] = useState(false);
  const buyer = !sellerBase && !merchantId;

  const allOrders = useMemo(() => orders ?? [], [orders]);
  const tabs = useMemo(() => [
    { key: "ALL" as const, label: "Tất cả", count: allOrders.length },
    {
      key: "PENDING" as const,
      label: "Chờ xác nhận",
      count: allOrders.filter((o) => o.status === "PENDING_CONFIRMATION").length,
    },
    {
      key: "DELIVERING" as const,
      label: "Đang giao",
      count: allOrders.filter(
        (o) =>
          o.status === "CONFIRMED" ||
          o.status === "OUT_FOR_DELIVERY" ||
          o.status === "READY_FOR_PICKUP" ||
          o.status === "AWAITING_BUYER_CONFIRMATION",
      ).length,
    },
    {
      key: "COMPLETED" as const,
      label: "Hoàn tất",
      count: allOrders.filter((o) => o.status === "COMPLETED").length,
    },
    {
      key: "CANCELLED" as const,
      label: "Đã hủy",
      count: allOrders.filter(
        (o) =>
          o.status?.startsWith("CANCEL") ||
          o.status === "REJECTED_BY_SELLER" ||
          o.status === "EXPIRED" ||
          o.status === "CLOSED_WITH_ISSUE",
      ).length,
    },
  ], [allOrders]);

  const filteredOrders = useMemo(() => {
    return allOrders.filter((order) => {
      if (statusFilter === "PENDING" && order.status !== "PENDING_CONFIRMATION") return false;
      if (
        statusFilter === "DELIVERING" &&
        order.status !== "CONFIRMED" &&
        order.status !== "OUT_FOR_DELIVERY" &&
        order.status !== "READY_FOR_PICKUP" &&
        order.status !== "AWAITING_BUYER_CONFIRMATION"
      )
        return false;
      if (statusFilter === "COMPLETED" && order.status !== "COMPLETED") return false;
      if (
        statusFilter === "CANCELLED" &&
        !order.status?.startsWith("CANCEL") &&
        order.status !== "REJECTED_BY_SELLER" &&
        order.status !== "EXPIRED" &&
        order.status !== "CLOSED_WITH_ISSUE"
      )
        return false;

      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase().trim();
        const codeMatch = order.orderCode?.toLowerCase().includes(q);
        const itemMatch = order.items?.some((i) => i.name?.toLowerCase().includes(q));
        if (!codeMatch && !itemMatch) return false;
      }
      return true;
    });
  }, [allOrders, statusFilter, searchQuery]);

  useEffect(() => {
    let active = true;
    const effectGeneration = ++generation.current;
    morePending.current = false;
    void (merchantId ? merchantOrders(merchantId, { locationId: locationId || undefined, limit: 20 }) : myOrders())
      .then((page) => {
        if (!active) return;
        setLoadingMore(false);
        setMoreError(false);
        setOrders(page.items ?? []);
        setNextCursor(page.nextCursor);
        setError(undefined);
        setIsAuthError(false);
      })
      .catch((cause: unknown) => {
        if (active) {
          setLoadingMore(false);
          setMoreError(false);
          setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
          setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng.");
        }
      });
    return () => { active = false; generation.current = effectGeneration + 1; };
  }, [merchantId, locationId, reloadKey]);

  function changeLocation(nextLocationId: string) {
    generation.current++;
    setLoadingMore(false);
    setMoreError(false);
    setLocationId(nextLocationId);
    setOrders(undefined);
    setNextCursor(undefined);
    setError(undefined);
  }

  function reload() {
    generation.current++;
    setLoadingMore(false);
    setMoreError(false);
    setOrders(undefined);
    setNextCursor(undefined);
    setError(undefined);
    setReloadKey((value) => value + 1);
  }

  async function loadMore() {
    if (!nextCursor || morePending.current) return;
    morePending.current = true;
    const requestGeneration = generation.current;
    setLoadingMore(true);
    setMoreError(false);
    setError(undefined);
    try {
      const page = await (merchantId ? merchantOrders(merchantId, { cursor: nextCursor, locationId: locationId || undefined, limit: 20 }) : myOrders({ cursor: nextCursor, limit: 20 }));
      if (requestGeneration !== generation.current) return;
      setOrders((current) => {
        const merged = [...(current ?? [])];
        const seen = new Set(merged.map(order => order.id));
        for (const order of page.items ?? []) if (order.id && !seen.has(order.id)) { merged.push(order); seen.add(order.id); }
        return merged;
      });
      setNextCursor(page.nextCursor);
    } catch (cause) {
      if (requestGeneration !== generation.current) return;
      if (buyer) {
        if (cause instanceof CovieApiError && cause.status === 401) { setIsAuthError(true); setError("Vui lòng đăng nhập lại."); }
        else setMoreError(true);
      } else setError(cause instanceof Error ? cause.message : "Không thể tải thêm đơn hàng.");
    } finally {
      if (requestGeneration === generation.current) { morePending.current = false; setLoadingMore(false); }
    }
  }

  if (buyer) {
    if (error) {
      if (isAuthError) {
        return (
          <section className={`buyer-orders-empty buyer-orders-auth-card ${styles.state}`} aria-label="Đăng nhập để xem đơn hàng">
            <div className="buyer-orders-auth-badge">
              <LockKey size={32} weight="duotone" />
            </div>
            <h2>Đăng nhập để xem đơn hàng</h2>
            <p>Vui lòng đăng nhập tài khoản CoVie để quản lý và theo dõi trạng thái các đơn hàng của bạn.</p>
            <div className="buyer-orders-auth-benefits">
              <div className="buyer-orders-auth-benefit-item">
                <CheckCircle size={16} weight="fill" className="text-emerald" />
                <span>Theo dõi tiến trình đơn hàng theo thời gian thực</span>
              </div>
              <div className="buyer-orders-auth-benefit-item">
                <CheckCircle size={16} weight="fill" className="text-emerald" />
                <span>Liên hệ trao đổi trực tiếp với người bán nội khu</span>
              </div>
              <div className="buyer-orders-auth-benefit-item">
                <CheckCircle size={16} weight="fill" className="text-emerald" />
                <span>Đánh giá món ăn & dịch vụ để tích lũy uy tín cư dân</span>
              </div>
            </div>
            <Link className="button-primary buyer-orders-auth-btn" href="/login?returnTo=%2Forders">Đăng nhập ngay</Link>
          </section>
        );
      }

      return (
        <section className={`buyer-orders-error ${styles.state}`} role="alert">
          <div className="buyer-orders-error__icon-surface">
            <CoVieIcon name="feedback-error" />
          </div>
          <h2>Không thể tải đơn hàng</h2>
          <p>Không thể tải danh sách đơn hàng. Hãy thử lại sau ít phút.</p>
          <div className="buyer-orders-error__actions">
            <button className="button-primary" type="button" onClick={reload}>Thử lại</button>
          </div>
        </section>
      );
    }

    if (!orders) {
      return (
        <div className={`buyer-orders-loading ${styles.state}`}>
          <LoadingState label="Đang tải đơn hàng…" />
        </div>
      );
    }

    if (!orders.length) {
      return (
        <section className={`buyer-orders-empty ${styles.state}`} aria-label="Chưa có đơn hàng">
          <div className="buyer-orders-empty-icon-surface">
            <Package size={44} weight="duotone" />
          </div>
          <h2>Chưa có đơn hàng</h2>
          <p>Các đơn mua và dịch vụ của bạn sẽ xuất hiện tại đây.</p>
          <Link className="button-primary" href="/">Khám phá</Link>
        </section>
      );
    }

    return (
      <div className="buyer-orders-content">
        {/* Toolbar: Search & Status Filter Tabs */}
        <div className="buyer-orders-toolbar">
          <div className="buyer-orders-search">
            <MagnifyingGlass size={18} className="buyer-orders-search-icon" aria-hidden="true" />
            <input
              type="search"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              placeholder="Tìm theo mã đơn hoặc tên món..."
              aria-label="Tìm kiếm đơn hàng"
              className="buyer-orders-search-input"
            />
            {searchQuery ? (
              <button
                type="button"
                onClick={() => setSearchQuery("")}
                className="buyer-orders-search-clear"
                aria-label="Xóa tìm kiếm"
              >
                <X size={14} />
              </button>
            ) : null}
          </div>

          <div className="buyer-orders-tabs" role="tablist" aria-label="Lọc trạng thái đơn hàng">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={statusFilter === tab.key}
                className={`buyer-orders-tab ${statusFilter === tab.key ? "buyer-orders-tab--active" : ""}`}
                onClick={() => setStatusFilter(tab.key)}
              >
                <span>{tab.label}</span>
                {tab.count > 0 ? <span className="buyer-orders-tab-count">{tab.count}</span> : null}
              </button>
            ))}
          </div>
        </div>

        {filteredOrders.length === 0 ? (
          <div className="buyer-orders-filtered-empty" role="status">
            <Package size={36} weight="duotone" className="buyer-orders-empty-icon" />
            <p>Không tìm thấy đơn hàng nào phù hợp với bộ lọc hiện tại.</p>
            <button
              type="button"
              className="button-secondary"
              onClick={() => { setStatusFilter("ALL"); setSearchQuery(""); }}
            >
              Đặt lại bộ lọc
            </button>
          </div>
        ) : (
          <section className={`orders-ledger buyer-orders-ledger ${styles.ledger}`} aria-label="Danh sách đơn hàng">
            {filteredOrders.map((order) => order.id ? (
              <Link className={`order-row buyer-order-row ${styles.card}`} key={order.id} href={`/orders/${encodeURIComponent(order.id)}`}>
                {/* 1. Header Row */}
                <div className={`buyer-order-card__header ${styles.heading}`}>
                  <div className="buyer-order-card__meta-left">
                    <span className="buyer-order-code-badge buyer-order-code-pill">
                      <strong>{order.orderCode ?? "Đơn hàng"}</strong>
                    </span>
                    <span className={`buyer-order-type ${styles.type}`}>
                      {order.type === "SERVICE" ? "Đơn dịch vụ" : order.type === "PRODUCT" ? "Đơn sản phẩm" : "Đơn hàng"}
                    </span>
                    <span className="buyer-order-dot-separator" aria-hidden="true">·</span>
                    {order.updatedAt ? (
                      <small className={styles.date}>Cập nhật {formatDateTime(order.updatedAt)}</small>
                    ) : order.createdAt ? (
                      <small className={styles.date}>Tạo {formatDateTime(order.createdAt)}</small>
                    ) : null}
                  </div>
                  <OrderStatusLabel status={order.status} />
                </div>

                {/* 2. Items & Pricing Body */}
                <div className="buyer-order-card__body">
                  <div className="buyer-order-card__items-section">
                    {order.items?.length ? (
                      <ul className={`buyer-order-card__items-list ${styles.items}`} aria-label="Món trong đơn">
                        {order.items.map((item, index) => {
                          const visual = getItemVisual(item.name, order.type);
                          return (
                            <li key={index} className="buyer-order-item-line">
                              <div
                                className="buyer-order-item-visual"
                                style={{ backgroundColor: visual.bg, color: visual.color }}
                                aria-hidden="true"
                              >
                                {visual.icon}
                              </div>
                              <div className="buyer-order-item-info">
                                <span className="buyer-order-item-name">{item.name?.trim() || "Mục đơn hàng"}</span>
                                <span className="buyer-order-item-qty">
                                  {item.quantity?.toLocaleString("vi-VN") ?? "—"} × {item.name?.trim() || "Mục đơn hàng"}
                                </span>
                              </div>
                            </li>
                          );
                        })}
                      </ul>
                    ) : (
                      <p className="buyer-order-no-items">Không có thông tin món trong đơn</p>
                    )}
                  </div>

                  <div className="buyer-order-card__pricing-section">
                    <StoredOrderPrice order={order} />
                  </div>
                </div>

                {/* 3. Footer Row: Actions & Hints */}
                <div className="buyer-order-card__footer">
                  <div className="buyer-order-card__hints">
                    {order.status === "COMPLETED" ? (
                      <span className="buyer-order-badge-hint">
                        <Sparkle size={14} weight="fill" /> Có thể đánh giá
                      </span>
                    ) : null}
                  </div>
                  <div className="buyer-order-card-actions">
                    <span className={`button-secondary buyer-order-detail-action ${styles.detailAction}`}>
                      <span>Xem chi tiết</span>
                      <CaretRight size={14} weight="bold" />
                    </span>
                  </div>
                </div>
              </Link>
            ) : null)}
          </section>
        )}
        {moreError ? <p role="alert">Không thể tải thêm đơn hàng. Vui lòng thử lại.</p> : null}
        {nextCursor ? (
          <button className="button-secondary buyer-orders-more-btn" type="button" onClick={() => void loadMore()} disabled={loadingMore}>
            {loadingMore ? "Đang tải…" : "Tải thêm đơn hàng"}
          </button>
        ) : null}
      </div>
    );
  }

  const pendingCount = allOrders.filter((o) => o.status === "PENDING_CONFIRMATION").length;
  const deliveringCount = allOrders.filter(
    (o) =>
      o.status === "CONFIRMED" ||
      o.status === "OUT_FOR_DELIVERY" ||
      o.status === "READY_FOR_PICKUP" ||
      o.status === "AWAITING_BUYER_CONFIRMATION",
  ).length;
  const completedCount = allOrders.filter((o) => o.status === "COMPLETED").length;

  return (
    <section className="seller-stack" aria-label="Danh sách đơn hàng">
      {merchantId && locations.length ? (
        <div className="seller-order-location-filter">
          <SelectDropdown<string>
            label="Điểm bán"
            aria-label="Điểm bán"
            value={locationId}
            radius="md"
            size="md"
            options={[
              { value: "", label: "Tất cả điểm bán được phân công" },
              ...locations
                .filter((location) => Boolean(location.id))
                .map((location) => ({
                  value: location.id!,
                  label: location.name ?? "Điểm bán",
                })),
            ]}
            onChange={(val) => changeLocation(val)}
          />
          <small>Có thể lọc theo một điểm bán cụ thể</small>
        </div>
      ) : null}

      {allOrders.length > 0 ? (
        <div className="seller-order-kpi-bar" role="region" aria-label="Tổng quan tình trạng đơn">
          <div
            className={`seller-order-kpi-card${statusFilter === "ALL" ? " seller-order-kpi-card--active" : ""}`}
            onClick={() => setStatusFilter("ALL")}
            role="button"
            tabIndex={0}
          >
            <div className="seller-order-kpi-card__icon" style={{ background: "rgba(71, 85, 105, 0.08)", color: "#475569" }}>
              <Receipt size={20} weight="duotone" />
            </div>
            <div className="seller-order-kpi-card__info">
              <span className="seller-order-kpi-card__value">{allOrders.length}</span>
              <span className="seller-order-kpi-card__label">Tổng đơn bán</span>
            </div>
          </div>
          <div
            className={`seller-order-kpi-card${statusFilter === "PENDING" ? " seller-order-kpi-card--active" : ""}`}
            onClick={() => setStatusFilter("PENDING")}
            role="button"
            tabIndex={0}
          >
            <div className="seller-order-kpi-card__icon" style={{ background: "rgba(234, 88, 12, 0.1)", color: "#ea580c" }}>
              <Clock size={20} weight="duotone" />
            </div>
            <div className="seller-order-kpi-card__info">
              <span className="seller-order-kpi-card__value">{pendingCount}</span>
              <span className="seller-order-kpi-card__label">Chờ xác nhận</span>
            </div>
          </div>
          <div
            className={`seller-order-kpi-card${statusFilter === "DELIVERING" ? " seller-order-kpi-card--active" : ""}`}
            onClick={() => setStatusFilter("DELIVERING")}
            role="button"
            tabIndex={0}
          >
            <div className="seller-order-kpi-card__icon" style={{ background: "rgba(37, 99, 235, 0.08)", color: "#2563eb" }}>
              <Truck size={20} weight="duotone" />
            </div>
            <div className="seller-order-kpi-card__info">
              <span className="seller-order-kpi-card__value">{deliveringCount}</span>
              <span className="seller-order-kpi-card__label">Đang giao / xử lý</span>
            </div>
          </div>
          <div
            className={`seller-order-kpi-card${statusFilter === "COMPLETED" ? " seller-order-kpi-card--active" : ""}`}
            onClick={() => setStatusFilter("COMPLETED")}
            role="button"
            tabIndex={0}
          >
            <div className="seller-order-kpi-card__icon" style={{ background: "rgba(4, 120, 87, 0.08)", color: "#047857" }}>
              <CheckCircle size={20} weight="duotone" />
            </div>
            <div className="seller-order-kpi-card__info">
              <span className="seller-order-kpi-card__value">{completedCount}</span>
              <span className="seller-order-kpi-card__label">Hoàn tất</span>
            </div>
          </div>
        </div>
      ) : null}

      {allOrders.length > 0 ? (
        <div className="seller-order-filter-toolbar">
          <div className="seller-order-search-wrap">
            <MagnifyingGlass size={16} weight="bold" className="seller-order-search-icon" />
            <input
              type="search"
              className="seller-order-search-input"
              placeholder="Tìm theo mã đơn (CV-...) hoặc tên món..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              aria-label="Tìm kiếm đơn hàng"
            />
            {searchQuery ? (
              <button
                type="button"
                className="seller-order-search-clear"
                onClick={() => setSearchQuery("")}
                aria-label="Xóa tìm kiếm"
              >
                <X size={14} weight="bold" />
              </button>
            ) : null}
          </div>

          <div className="seller-order-tabs" role="tablist" aria-label="Lọc đơn theo trạng thái">
            {tabs.map((tab) => (
              <button
                key={tab.key}
                type="button"
                role="tab"
                aria-selected={statusFilter === tab.key}
                className={`seller-order-tab${statusFilter === tab.key ? " seller-order-tab--active" : ""}`}
                onClick={() => setStatusFilter(tab.key)}
              >
                <span>{tab.label}</span>
                <span className="seller-order-tab__badge">{tab.count}</span>
              </button>
            ))}
          </div>
        </div>
      ) : null}

      {error && !orders ? <ErrorState title="Không thể tải đơn hàng" description={error} action={<button type="button" onClick={reload}>Thử lại</button>} /> : null}
      {!error && !orders ? <LoadingState label="Đang tải đơn hàng…" /> : null}
      {orders?.length === 0 ? <EmptyState title="Chưa có đơn hàng" description={locationId ? "Không có đơn trong điểm bán đang chọn." : "Đơn mới sẽ xuất hiện ở đây khi có dữ liệu mới."} /> : null}
      {allOrders.length > 0 && filteredOrders.length === 0 ? (
        <div className="seller-order-filter-empty">
          <p>Không tìm thấy đơn hàng phù hợp với điều kiện tìm kiếm hoặc bộ lọc.</p>
          <button
            type="button"
            className="seller-load-more"
            onClick={() => {
              setStatusFilter("ALL");
              setSearchQuery("");
            }}
          >
            Đặt lại bộ lọc
          </button>
        </div>
      ) : null}

      {filteredOrders.length ? (
        <div className="seller-order-list-block">
          <h2>Mới nhất</h2>
          <div className="seller-order-list-cards">
            {filteredOrders.map((order) => {
              if (!order.id) return null;
              const firstItem = order.items?.[0];
              const visual = getItemVisual(firstItem?.name, order.type);
              const pricing = order.pricing;
              const totalAmount = pricing?.totalAmount ?? pricing?.knownTotal;

              return (
                <Link
                  className="seller-order-list-card"
                  key={order.id}
                  href={sellerBase ? `${sellerBase}/${encodeURIComponent(order.id)}` : `/orders/${encodeURIComponent(order.id)}`}
                >
                  <header>
                    <div className="seller-order-card__visual" style={{ background: visual.bg, color: visual.color }}>
                      {visual.icon}
                    </div>
                    <div className="seller-order-card__meta">
                      <strong>{order.orderCode ?? "Đơn hàng"}</strong>
                      {firstItem?.name ? <span className="seller-order-card__brief">{firstItem.name}</span> : null}
                    </div>
                    <OrderStatusLabel status={order.status} />
                  </header>
                  <p>
                    {order.type === "SERVICE" ? "Đơn dịch vụ" : "Đơn sản phẩm"}
                    {totalAmount != null && pricing?.currency ? ` · ${formatMoney({ amount: totalAmount, currency: pricing.currency })}` : ""}
                  </p>
                  {order.createdAt ? <small>Tạo lúc {formatDateTime(order.createdAt)}</small> : <small />}
                  <span className="seller-order-open">
                    Xem chi tiết
                    <CaretRight size={13} weight="bold" style={{ marginLeft: 4 }} />
                  </span>
                </Link>
              );
            })}
          </div>
        </div>
      ) : null}
      {error && orders ? <p role="alert">{error}</p> : null}
      {nextCursor ? <button className="seller-load-more" type="button" onClick={() => void loadMore()} disabled={loadingMore}>{loadingMore ? "Đang tải…" : "Tải thêm"}</button> : null}
    </section>
  );
}
