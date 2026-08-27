"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, formatAdminMoney, presentOrderStatus } from "./admin-ui";

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
    for (const key of ["name", "itemName", "title", "variantName"]) if (typeof record[key] === "string" && record[key]) return record[key] as string;
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

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(undefined);
    const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
    if (appliedQuery.trim()) params.set("orderCode", appliedQuery.trim());
    requestBrowserApi("GET", `/api/v1/admin/orders?${params.toString()}`)
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json() as Promise<OrderPage>; })
      .then(payload => {
        if (!active) return;
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
    if (!selectedId) { setDetail(undefined); return; }
    let active = true;
    setDetailLoading(true);
    setDetailError(undefined);
    requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(selectedId)}`)
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json() as Promise<OrderDetail>; })
      .then(payload => { if (active) setDetail(payload); })
      .catch(cause => { if (active) { setDetail(undefined); setDetailError(cause instanceof Error ? cause.message : "Không thể tải chi tiết đơn hàng"); } })
      .finally(() => { if (active) setDetailLoading(false); });
    return () => { active = false; };
  }, [selectedId]);

  const selected = items.find(item => item.id === selectedId) ?? items[0];
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));
  const displayDetail = detail?.id === selected?.id ? detail : undefined;
  const detailItems = useMemo(() => displayDetail?.items ?? [], [displayDetail]);

  return <div className="admin-page-stack">
    <AdminPageHeader title="Tra cứu đơn hàng" subtitle="CoVie Admin · Tra cứu đơn hàng" />
    <form className="admin-search-row" onSubmit={event => { event.preventDefault(); setPage(0); setAppliedQuery(query); }}>
      <label className="admin-field admin-field--search"><span>Mã đơn</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="#CV2048" /></label>
      <button className="admin-button admin-button--secondary" type="submit">Tìm kiếm</button>
      {appliedQuery ? <button className="admin-button admin-button--secondary" type="button" onClick={() => { setQuery(""); setAppliedQuery(""); setPage(0); }}>Xoá lọc</button> : null}
    </form>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải đơn hàng…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <AdminSurface className="admin-table-surface admin-orders-table">
      <div className="admin-table-scroll"><table className="admin-data-table"><thead><tr><th>Mã đơn</th><th>Người bán</th><th>Trạng thái</th><th>Cập nhật</th><th>Tác vụ</th></tr></thead>
      <tbody>{items.length ? items.map(item => {
        const status = presentOrderStatus(item.status);
        return <tr key={item.id} data-selected={item.id === selected?.id || undefined}>
          <td>{String(item.orderCode ?? item.id)}</td><td>{String(item.merchantId ?? "—")}</td><td><AdminStatusChip label={status.label} tone={status.tone} /></td><td>{formatAdminDate(item.updatedAt ?? item.createdAt)}</td>
          <td><button className="admin-row-action" type="button" onClick={() => setSelectedId(item.id)}>Xem</button></td>
        </tr>;
      }) : <tr><td colSpan={5}><span className="admin-muted">Không tìm thấy đơn phù hợp.</span></td></tr>}</tbody></table></div>
    </AdminSurface> : null}
    {!loading && !error && totalPages > 1 ? <div className="admin-pagination"><span>Trang {page + 1} / {totalPages} · {total} đơn</span><div><button className="admin-button admin-button--secondary" disabled={page === 0} onClick={() => setPage(value => Math.max(0, value - 1))}>Trước</button><button className="admin-button admin-button--secondary" disabled={page + 1 >= totalPages} onClick={() => setPage(value => value + 1)}>Sau</button></div></div> : null}
    {selected ? <div className="admin-order-detail-grid">
      <AdminSurface className="admin-entity-summary">
        <div className="admin-entity-summary__top"><strong>Đơn {String(displayDetail?.orderCode ?? selected.orderCode ?? selected.id)}</strong><AdminStatusChip label="Chỉ đọc" tone="neutral" /></div>
        {detailLoading ? <p className="admin-muted">Đang tải chi tiết…</p> : null}
        {detailError ? <p className="admin-error" role="alert">{detailError}</p> : null}
        <dl><div><dt>Người mua</dt><dd>{String(displayDetail?.buyer?.userId ?? selected.buyerUserId ?? "—")}</dd></div><div><dt>Người bán</dt><dd>{String(displayDetail?.merchant?.merchantId ?? selected.merchantId ?? "—")}</dd></div><div><dt>Loại</dt><dd>{String(displayDetail?.type ?? selected.type ?? "—")}</dd></div><div><dt>ID</dt><dd>{selected.id}</dd></div></dl>
        <p className="admin-caption">Không có điều khiển ép trạng thái đơn hàng.</p>
      </AdminSurface>
      <AdminSurface className="admin-order-readonly-card"><h2>Nội dung đơn</h2>{displayDetail ? <>
        <div className="admin-order-lines">{detailItems.length ? detailItems.map((item, index) => <p key={item.id ?? index}>{item.quantity ?? 0} × {itemLabel(item, index)} · {formatAdminMoney(item.lineTotal ?? item.unitPrice, item.currency ?? displayDetail.pricing?.currency)}</p>) : <p className="admin-muted">API không trả danh sách item.</p>}</div>
        <p>Phí giao: {formatAdminMoney(displayDetail.pricing?.shippingFee, displayDetail.pricing?.currency)} · Thanh toán: {String(displayDetail.paymentMethod ?? "—")}</p>
        <p><strong>Tổng:</strong> {formatAdminMoney(displayDetail.pricing?.totalAmount ?? displayDetail.pricing?.knownTotal, displayDetail.pricing?.currency)}</p>
      </> : <p className="admin-muted">Chi tiết chưa khả dụng.</p>}<Link className="admin-inline-link" href={`/admin/orders/${selected.id}/history`}>Xem lịch sử đơn hàng</Link><strong>Màn hình này không có thao tác thay đổi trạng thái đơn.</strong></AdminSurface>
    </div> : null}
  </div>;
}
