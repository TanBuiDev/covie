"use client";

import { useCallback, useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminSuspensionAction } from "./admin-suspension-action";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentAccountStatus, presentModerationStatus } from "./admin-ui";

type Row = Record<string, unknown> & { id: string; status: string };
type PagePayload = { items?: Row[]; total?: number; page?: number; size?: number };

const PAGE_SIZE = 50;
const columnLabels: Record<string, string> = {
  id: "Mã",
  displayName: "Người dùng",
  name: "Người bán / Nhà cung cấp",
  status: "Trạng thái",
  moderationStatus: "Kiểm duyệt",
  createdAt: "Tạo lúc",
  updatedAt: "Cập nhật",
};

function displayValue(item: Row, column: string) {
  if (column === "createdAt" || column === "updatedAt") return formatAdminDate(item[column]);
  return String(item[column] ?? "—");
}

export function AdminEntityTable({ type, title, columns }: { type: "users" | "merchants"; title: string; columns: string[] }) {
  const [items, setItems] = useState<Row[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);

  const load = useCallback(async (isActive: () => boolean = () => true) => {
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/${type}?page=${page}&size=${PAGE_SIZE}`);
      if (!response.ok) throw await readApiError(response);
      const payload = await response.json() as PagePayload;
      const nextItems = payload.items ?? [];
      if (!isActive()) return;
      setItems(nextItems);
      setTotal(payload.total ?? nextItems.length);
      setSelectedId(current => current && nextItems.some(item => item.id === current) ? current : nextItems[0]?.id);
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu quản trị");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, [page, type]);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(() => active); });
    return () => { active = false; };
  }, [load]);

  function reload() {
    setLoading(true);
    setError(undefined);
    void load();
  }

  async function mutate(id: string, operation: "suspend" | "restore", reason?: string) {
    const response = await requestBrowserApi(
      "POST",
      `/api/v1/admin/${type}/${id}/${operation}`,
      reason ? { reasonCode: reason } : undefined,
      { "Idempotency-Key": crypto.randomUUID(), "Content-Type": "application/json" },
    );
    if (!response.ok) throw await readApiError(response);
    setLoading(true);
    setError(undefined);
    await load();
  }

  const selected = items.find(item => item.id === selectedId) ?? items[0];
  const subtitle = type === "users" ? "Quản trị vận hành" : "Quản lý tài khoản người bán và nhà cung cấp";
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  return (
    <div className="admin-page-stack">
      <AdminPageHeader title={title} subtitle={subtitle} />
      {loading ? <AdminSurface><p className="admin-muted" aria-live="polite">Đang tải dữ liệu…</p></AdminSurface> : null}
      {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={reload}>Thử lại</button></AdminSurface> : null}
      {!loading && !error && items.length === 0 ? <AdminSurface><p className="admin-muted">Chưa có dữ liệu.</p></AdminSurface> : null}
      {!loading && !error && items.length > 0 ? (
        <>
          <div className="admin-split-grid">
            <AdminSurface className="admin-table-surface">
              <div className="admin-table-scroll">
                <table className="admin-data-table">
                  <thead><tr>{columns.map(column => <th key={column}>{columnLabels[column] ?? column}</th>)}<th>Tác vụ</th></tr></thead>
                  <tbody>{items.map(item => {
                    const status = presentAccountStatus(item.status);
                    return <tr key={item.id} data-selected={item.id === selected?.id || undefined}>
                      {columns.map(column => <td key={column}>{column === "status" ? <AdminStatusChip label={status.label} tone={status.tone} /> : column === "moderationStatus" ? (() => { const moderation = presentModerationStatus(item.moderationStatus); return <AdminStatusChip label={moderation.label} tone={moderation.tone} />; })() : displayValue(item, column)}</td>)}
                      <td><button className="admin-row-action" type="button" onClick={() => setSelectedId(item.id)}>Xem</button></td>
                    </tr>;
                  })}</tbody>
                </table>
              </div>
            </AdminSurface>
            {selected ? <AdminSurface className="admin-entity-summary">
              <div className="admin-entity-summary__top"><strong>{String(selected.displayName ?? selected.name ?? selected.id)}</strong><AdminStatusChip label="Thông tin" tone="neutral" /></div>
              <dl>
                <div><dt>Trạng thái</dt><dd>{presentAccountStatus(selected.status).label}</dd></div>
                {type === "merchants" ? <div><dt>Kiểm duyệt</dt><dd>{presentModerationStatus(selected.moderationStatus).label}</dd></div> : null}
                <div><dt>ID</dt><dd>{selected.id}</dd></div>
                <div><dt>Cập nhật</dt><dd>{formatAdminDate(selected.updatedAt ?? selected.createdAt)}</dd></div>
              </dl>
              <p className="admin-caption">Thông tin chi tiết theo dữ liệu hệ thống.</p>
            </AdminSurface> : null}
          </div>
          {totalPages > 1 ? <div className="admin-pagination"><span>Trang {page + 1} / {totalPages} · {total} mục</span><div><button className="admin-button admin-button--secondary" disabled={page === 0 || loading} onClick={() => { setLoading(true); setError(undefined); setPage(value => Math.max(0, value - 1)); }}>Trước</button><button className="admin-button admin-button--secondary" disabled={page + 1 >= totalPages || loading} onClick={() => { setLoading(true); setError(undefined); setPage(value => value + 1); }}>Sau</button></div></div> : null}
          {selected ? <AdminSurface className="admin-safety-card">
            <h2>Tạm ngưng / khôi phục tài khoản</h2>
            <p>Mọi thao tác có tác động đến tài khoản đều yêu cầu xác nhận. Trạng thái được quyết định bởi hệ thống; màn hình này không cung cấp trường sửa trạng thái trực tiếp.</p>
            <AdminSuspensionAction status={selected.status} onSubmit={(operation, reason) => mutate(selected.id, operation, reason)} />
          </AdminSurface> : null}
        </>
      ) : null}
    </div>
  );
}
