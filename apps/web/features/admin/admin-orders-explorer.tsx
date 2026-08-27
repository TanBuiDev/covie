"use client";

import Link from "next/link";
import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentOrderStatus } from "./admin-ui";

type OrderRow = Record<string, unknown> & {
  id: string;
  orderCode?: string;
  type?: string;
  status?: string;
  buyerUserId?: string;
  merchantId?: string;
  createdAt?: string;
  updatedAt?: string;
};

export function AdminOrdersExplorer() {
  const [items, setItems] = useState<OrderRow[]>([]);
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [selectedId, setSelectedId] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    setLoading(true);
    requestBrowserApi("GET", "/api/v1/admin/orders")
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json(); })
      .then(payload => {
        if (!active) return;
        const nextItems = (Array.isArray(payload) ? payload : payload.items ?? []) as OrderRow[];
        setItems(nextItems);
        setSelectedId(nextItems[0]?.id);
      })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải đơn hàng"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  const visibleItems = useMemo(() => {
    const normalized = appliedQuery.trim().toLowerCase();
    if (!normalized) return items;
    return items.filter(item => String(item.orderCode ?? item.id).toLowerCase().includes(normalized));
  }, [appliedQuery, items]);
  const selected = items.find(item => item.id === selectedId) ?? visibleItems[0];

  return <div className="admin-page-stack">
    <AdminPageHeader title="Tra cứu đơn hàng" subtitle="CoVie Admin · Tra cứu đơn hàng" />
    <form className="admin-search-row" onSubmit={event => { event.preventDefault(); setAppliedQuery(query); }}>
      <label className="admin-field admin-field--search"><span>Mã đơn</span><input value={query} onChange={event => setQuery(event.target.value)} placeholder="#CV2048" /></label>
      <button className="admin-button admin-button--secondary" type="submit">Tìm kiếm</button>
    </form>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải đơn hàng…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <AdminSurface className="admin-table-surface admin-orders-table">
      <div className="admin-table-scroll"><table className="admin-data-table"><thead><tr><th>Mã đơn</th><th>Người bán</th><th>Trạng thái</th><th>Cập nhật</th><th>Tác vụ</th></tr></thead>
      <tbody>{visibleItems.length ? visibleItems.map(item => {
        const status = presentOrderStatus(item.status);
        return <tr key={item.id} data-selected={item.id === selected?.id || undefined}>
          <td>{String(item.orderCode ?? item.id)}</td><td>{String(item.merchantId ?? "—")}</td><td><AdminStatusChip label={status.label} tone={status.tone} /></td><td>{formatAdminDate(item.updatedAt ?? item.createdAt)}</td>
          <td><button className="admin-row-action" type="button" onClick={() => setSelectedId(item.id)}>Xem</button></td>
        </tr>;
      }) : <tr><td colSpan={5}><span className="admin-muted">Không tìm thấy đơn phù hợp.</span></td></tr>}</tbody></table></div>
    </AdminSurface> : null}
    {selected ? <div className="admin-order-detail-grid">
      <AdminSurface className="admin-entity-summary">
        <div className="admin-entity-summary__top"><strong>Đơn {String(selected.orderCode ?? selected.id)}</strong><AdminStatusChip label="Chỉ đọc" tone="neutral" /></div>
        <dl><div><dt>Người mua</dt><dd>{String(selected.buyerUserId ?? "—")}</dd></div><div><dt>Loại</dt><dd>{String(selected.type ?? "—")}</dd></div><div><dt>ID</dt><dd>{selected.id}</dd></div></dl>
        <p className="admin-caption">Không có điều khiển ép trạng thái đơn hàng.</p>
      </AdminSurface>
      <AdminSurface className="admin-order-readonly-card"><h2>Ngữ cảnh đơn hàng</h2><p>Dữ liệu hiển thị theo snapshot mà Admin API cung cấp. Trường không có trong contract sẽ không được tái dựng từ dữ liệu mẫu.</p><Link className="admin-inline-link" href={`/admin/orders/${selected.id}/history`}>Xem lịch sử đơn hàng</Link><strong>Màn hình này không có thao tác thay đổi trạng thái đơn.</strong></AdminSurface>
    </div> : null}
  </div>;
}
