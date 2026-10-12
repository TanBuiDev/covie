"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  ArrowRight,
  Clock,
  MagnifyingGlass,
  Package,
  Receipt,
  ShieldCheck,
  Copy,
  Check,
  CalendarBlank,
  User,
  Storefront,
  Scroll,
  Fingerprint,
} from "@phosphor-icons/react/dist/ssr";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  formatAdminMoney,
  presentOrderStatus,
} from "./admin-ui";
import styles from "./admin-orders-explorer.module.css";

type OrderRow = {
  id: string;
  orderCode?: string;
  type?: string;
  status?: string;
  buyerUserId?: string;
  merchantId?: string;
  createdAt?: string;
  updatedAt?: string;
};
type OrderParty = { userId?: string; merchantId?: string; locationId?: string };
type OrderItem = { id?: string; quantity?: number; unitPrice?: number; lineTotal?: number; currency?: string; snapshot?: unknown };
type OrderPricing = { subtotal?: number; shippingFee?: number; knownTotal?: number; totalAmount?: number; currency?: string };
type OrderDetail = {
  id: string;
  orderCode?: string;
  type?: string;
  status?: string;
  source?: string;
  buyer?: OrderParty;
  merchant?: OrderParty;
  items?: OrderItem[];
  pricing?: OrderPricing;
  paymentMethod?: string;
  createdAt?: string;
};
type OrderPage = { items?: OrderRow[]; total?: number; page?: number; size?: number };

const PAGE_SIZE = 50;

function itemLabel(item: OrderItem, index: number) {
  if (item.snapshot && typeof item.snapshot === "object") {
    const record = item.snapshot as Record<string, unknown>;
    for (const key of ["name", "itemName", "title", "variantName"]) {
      if (typeof record[key] === "string" && record[key]) return record[key] as string;
    }
  }
  return `Mục ${index + 1}`;
}

export function AdminOrdersExplorer() {
  const [items, setItems] = useState<OrderRow[]>([]);
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string>();
  const [detail, setDetail] = useState<OrderDetail>();
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [error, setError] = useState<string>();
  const [detailError, setDetailError] = useState<string>();
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [copiedId, setCopiedId] = useState(false);

  const handleCopy = (text: string) => {
    void navigator.clipboard.writeText(text);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

  useEffect(() => {
    let active = true;
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (appliedQuery.trim()) params.set("orderCode", appliedQuery.trim());
    Promise.resolve().then(() => {
      if (!active) return undefined;
      setLoading(true);
      setError(undefined);
      return requestBrowserApi("GET", `/api/v1/admin/orders?${params.toString()}`);
    })
      .then(async response => { if (!response) return undefined; if (!response.ok) throw await readApiError(response); return response.json() as Promise<OrderPage>; })
      .then(payload => {
        if (!active || !payload) return;
        const nextItems = payload.items ?? [];
        setItems(nextItems);
        setTotal(payload.total ?? nextItems.length);
        setSelectedId(current => current && nextItems.some(item => item.id === current) ? current : nextItems[0]?.id);
      })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [appliedQuery, page]);

  useEffect(() => {
    if (!selectedId) return;
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return undefined;
      setDetailLoading(true);
      setDetailError(undefined);
      return requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(selectedId)}`);
    })
      .then(async response => { if (!response) return undefined; if (!response.ok) throw await readApiError(response); return response.json() as Promise<OrderDetail>; })
      .then(payload => { if (active && payload) setDetail(payload); })
      .catch(cause => { if (active) { setDetail(undefined); setDetailError(cause instanceof Error ? cause.message : "Không thể tải chi tiết đơn hàng"); } })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId]);

  const selected = items.find(item => item.id === selectedId) ?? items[0];
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const displayDetail = detail?.id === selected?.id ? detail : undefined;
  const detailItems = useMemo(() => displayDetail?.items ?? [], [displayDetail]);
  const orderStatus = selected ? presentOrderStatus(displayDetail?.status ?? selected.status) : undefined;

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Tra cứu đơn hàng"
        subtitle="CoVie Admin · Tra cứu và giám sát tiến trình giao dịch toàn sàn"
      />

      <form className="admin-search-row" onSubmit={event => { event.preventDefault(); setPage(0); setAppliedQuery(query); }}>
        <label className="admin-field admin-field--search">
          <span>Mã đơn</span>
          <input
            value={query}
            onChange={event => setQuery(event.target.value)}
            placeholder="Nhập mã đơn, ví dụ #CV2048…"
          />
        </label>
        <button className="admin-button admin-button--secondary" type="submit">
          <MagnifyingGlass size={16} weight="bold" />
          <span>Tìm kiếm</span>
        </button>
        {appliedQuery ? (
          <button
            className="admin-button admin-button--secondary"
            type="button"
            onClick={() => { setQuery(""); setAppliedQuery(""); setPage(0); }}
          >
            Xoá lọc
          </button>
        ) : null}
      </form>

      {loading ? (
        <AdminSurface>
          <p className="admin-muted" aria-live="polite">Đang tải đơn hàng…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
        </AdminSurface>
      ) : null}

      {!loading && !error ? (
        <div className="admin-split-grid">
          {/* CỘT TRÁI: MASTER TABLE + PAGINATION */}
          <div className={styles.tableColumn}>
            <AdminSurface className={`admin-table-surface admin-orders-table ${styles.tableSurface}`}>
              <div className={`admin-table-scroll ${styles.tableScroll}`}>
                <table className="admin-data-table">
                  <thead>
                    <tr>
                      <th>Mã đơn</th>
                      <th>Người bán</th>
                      <th>Trạng thái</th>
                      <th>Cập nhật</th>
                      <th>Tác vụ</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.length ? (
                      items.map(item => {
                        const status = presentOrderStatus(item.status);
                        const isRowSelected = item.id === selected?.id;
                        return (
                          <tr key={item.id} data-selected={isRowSelected || undefined}>
                            <td>
                              <strong style={{ color: isRowSelected ? "#047857" : "inherit" }}>
                                {String(item.orderCode ?? item.id)}
                              </strong>
                            </td>
                            <td style={{ fontFamily: "monospace", fontSize: "12px" }}>
                              {String(item.merchantId ? `${item.merchantId.slice(0, 8)}…` : "—")}
                            </td>
                            <td>
                              <AdminStatusChip label={status.label} tone={status.tone} />
                            </td>
                            <td>{formatAdminDate(item.updatedAt ?? item.createdAt)}</td>
                            <td>
                              <button
                                className="admin-row-action"
                                type="button"
                                onClick={() => setSelectedId(item.id)}
                                style={{
                                  fontWeight: isRowSelected ? 700 : 500,
                                  color: isRowSelected ? "#047857" : undefined,
                                }}
                              >
                                {isRowSelected ? "Đang xem" : "Xem"}
                              </button>
                            </td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan={5} style={{ textAlign: "center", padding: "32px 0" }}>
                          <span className="admin-muted">Không tìm thấy đơn phù hợp.</span>
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>

              {/* DOCKED PAGINATION */}
              {totalPages > 1 ? (
                <div className={styles.tablePagination}>
                  <span>Trang {page + 1} / {totalPages} · {total} đơn hàng</span>
                  <div className={styles.paginationButtons}>
                    <button
                      className="admin-button admin-button--secondary"
                      disabled={page === 0}
                      onClick={() => setPage(value => Math.max(0, value - 1))}
                    >
                      Trước
                    </button>
                    <button
                      className="admin-button admin-button--secondary"
                      disabled={page + 1 >= totalPages}
                      onClick={() => setPage(value => value + 1)}
                    >
                      Sau
                    </button>
                  </div>
                </div>
              ) : null}
            </AdminSurface>
          </div>

          {/* CỘT PHẢI: STICKY DETAIL INSPECTOR PANEL */}
          {selected ? (
            <div className={styles.inspectorCard}>
              {/* HEADER */}
              <div className={styles.inspectorHeader}>
                <div className={styles.orderTitleRow}>
                  <div className={styles.orderAvatar}>
                    <Package size={26} weight="bold" />
                  </div>
                  <div className={styles.orderTitleMeta}>
                    <span className={styles.orderCode} title={String(displayDetail?.orderCode ?? selected.orderCode ?? selected.id)}>
                      Đơn {String(displayDetail?.orderCode ?? selected.orderCode ?? selected.id)}
                    </span>
                    <span className={styles.orderTypeBadge}>
                      Loại: {String(displayDetail?.type ?? selected.type ?? "PRODUCT")}
                    </span>
                  </div>
                </div>

                <div className={styles.statusBanner}>
                  <span className={styles.statusBannerLabel}>Trạng thái tiến trình</span>
                  {orderStatus ? (
                    <AdminStatusChip label={orderStatus.label} tone={orderStatus.tone} />
                  ) : (
                    <AdminStatusChip label="Chỉ đọc" tone="neutral" />
                  )}
                </div>
              </div>

              {detailLoading ? <p className="admin-muted">Đang tải dữ liệu chi tiết…</p> : null}
              {detailError ? <p className="admin-error" role="alert">{detailError}</p> : null}

              {/* IDENTIFIERS */}
              <div className={styles.sectionBlock}>
                <div className={styles.sectionTitle}>Thông tin giao dịch</div>
                <div className={styles.detailsList}>
                  <div className={styles.detailRow}>
                    <span className={styles.detailLabel}>
                      <Fingerprint size={14} weight="bold" />
                      <span>Mã hệ thống</span>
                    </span>
                    <button
                      type="button"
                      className={`${styles.idCopyBadge} ${copiedId ? styles.idCopiedSuccess : ""}`}
                      onClick={() => handleCopy(selected.id)}
                      title="Bấm để sao chép toàn bộ UUID"
                    >
                      {copiedId ? <Check size={12} weight="bold" /> : <Copy size={12} />}
                      <span>{copiedId ? "Đã sao chép" : `${selected.id.slice(0, 8)}…${selected.id.slice(-4)}`}</span>
                    </button>
                  </div>

                  <div className={styles.detailRow}>
                    <span className={styles.detailLabel}>
                      <User size={14} weight="bold" />
                      <span>Người mua</span>
                    </span>
                    <span className={styles.detailValue} style={{ fontFamily: "monospace", fontSize: "12px" }}>
                      {String(displayDetail?.buyer?.userId ?? selected.buyerUserId ?? "—")}
                    </span>
                  </div>

                  <div className={styles.detailRow}>
                    <span className={styles.detailLabel}>
                      <Storefront size={14} weight="bold" />
                      <span>Gian hàng</span>
                    </span>
                    <span className={styles.detailValue} style={{ fontFamily: "monospace", fontSize: "12px" }}>
                      {String(displayDetail?.merchant?.merchantId ?? selected.merchantId ?? "—")}
                    </span>
                  </div>

                  <div className={styles.detailRow}>
                    <span className={styles.detailLabel}>
                      <CalendarBlank size={14} weight="bold" />
                      <span>Cập nhật lúc</span>
                    </span>
                    <span className={styles.detailValue}>
                      {formatAdminDate(selected.updatedAt ?? selected.createdAt)}
                    </span>
                  </div>
                </div>
              </div>

              {/* ORDER ITEMS LIST */}
              <div className={styles.sectionBlock}>
                <div className={styles.sectionTitle}>
                  Mặt hàng ({detailItems.length})
                </div>
                <div className={styles.itemsBox}>
                  {displayDetail ? (
                    detailItems.length ? (
                      <div className={styles.itemsScrollList}>
                        {detailItems.map((item, index) => (
                          <div key={item.id ?? index} className={styles.itemLine}>
                            <div className={styles.itemQtyTitle}>
                              <span className={styles.itemQty}>{item.quantity ?? 1}×</span>
                              <span className={styles.itemName}>{itemLabel(item, index)}</span>
                            </div>
                            <span className={styles.itemPrice}>
                              {formatAdminMoney(item.lineTotal ?? item.unitPrice, item.currency ?? displayDetail.pricing?.currency)}
                            </span>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <p className="admin-muted" style={{ margin: 0 }}>API không trả danh sách item.</p>
                    )
                  ) : (
                    <p className="admin-muted" style={{ margin: 0 }}>Đang chuẩn bị dữ liệu…</p>
                  )}
                </div>
              </div>

              {/* FINANCIAL TOTALS */}
              {displayDetail ? (
                <div className={styles.pricingBox}>
                  <div className={styles.pricingRow}>
                    <span>Tạm tính hàng:</span>
                    <span>{formatAdminMoney(displayDetail.pricing?.subtotal, displayDetail.pricing?.currency)}</span>
                  </div>
                  <div className={styles.pricingRow}>
                    <span>Phí vận chuyển:</span>
                    <span>{formatAdminMoney(displayDetail.pricing?.shippingFee, displayDetail.pricing?.currency)}</span>
                  </div>
                  <div className={styles.pricingRow}>
                    <span>Phương thức:</span>
                    <span style={{ fontWeight: 600 }}>{String(displayDetail.paymentMethod ?? "COD")}</span>
                  </div>
                  <div className={styles.pricingTotalRow}>
                    <span className={styles.totalLabel}>Tổng thanh toán:</span>
                    <span className={styles.totalValue}>
                      {formatAdminMoney(displayDetail.pricing?.totalAmount ?? displayDetail.pricing?.knownTotal, displayDetail.pricing?.currency)}
                    </span>
                  </div>
                </div>
              ) : null}

              {/* SHORTCUTS & ACTIONS */}
              <div className={styles.quickLinks}>
                <div className={styles.sectionTitle}>Hành động & Tra cứu</div>
                <Link
                  className={styles.quickLinkItem}
                  href={`/admin/orders/${selected.id}/history`}
                  title="Xem lịch sử tiến trình và sự kiện đơn hàng"
                >
                  <div className={styles.quickLinkLeft}>
                    <Clock size={14} weight="bold" />
                    <span>Lịch sử trạng thái đơn</span>
                  </div>
                  <ArrowRight size={12} weight="bold" />
                </Link>

                <Link
                  className={styles.quickLinkItem}
                  href={`/admin/audit?q=${encodeURIComponent(selected.id)}`}
                  title="Tra cứu log kiểm toán của đơn này"
                >
                  <div className={styles.quickLinkLeft}>
                    <Scroll size={14} weight="bold" />
                    <span>Tra cứu log kiểm toán</span>
                  </div>
                  <ArrowRight size={12} weight="bold" />
                </Link>

                <div className={styles.contractBadge}>
                  <ShieldCheck size={14} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
                  <span>Dữ liệu đơn hàng chỉ đọc, không thể ép trạng thái trái luồng.</span>
                </div>
              </div>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  );
}
