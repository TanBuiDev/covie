"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate } from "./admin-ui";

type AuditRow = {
  id?: string;
  actorUserId?: string;
  actorType?: string;
  eventCategory?: string;
  action?: string;
  entityType?: string;
  entityId?: string;
  metadata?: unknown;
  requestId?: string;
  createdAt?: string;
};

type AuditPage = {
  content?: AuditRow[];
  totalPages?: number;
  totalElements?: number;
  number?: number;
  first?: boolean;
  last?: boolean;
};

type AuditFilters = {
  actorUserId: string;
  action: string;
  entityType: string;
  entityId: string;
  requestId: string;
  createdFrom: string;
  createdTo: string;
  sort: string;
};

const PAGE_SIZE = 50;
const initialFilters: AuditFilters = {
  actorUserId: "",
  action: "",
  entityType: "",
  entityId: "",
  requestId: "",
  createdFrom: "",
  createdTo: "",
  sort: "CREATED_DESC",
};

function toIso(value: string) {
  if (!value) return "";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : date.toISOString();
}

function safeMetadataSummary(metadata: unknown) {
  if (!metadata || typeof metadata !== "object" || Array.isArray(metadata)) return "Không có metadata công khai";
  const hidden = /password|secret|token|authorization|cookie|email|phone|address|contact/i;
  const parts: string[] = [];
  for (const [key, value] of Object.entries(metadata as Record<string, unknown>)) {
    if (hidden.test(key)) continue;
    if (!["string", "number", "boolean"].includes(typeof value)) continue;
    const text = String(value);
    parts.push(`${key}: ${text.length > 70 ? `${text.slice(0, 67)}…` : text}`);
    if (parts.length === 3) break;
  }
  return parts.length ? parts.join(" · ") : "Metadata nhạy cảm/không hiển thị đã được ẩn";
}

export function AdminAuditLog() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [draft, setDraft] = useState<AuditFilters>(initialFilters);
  const [filters, setFilters] = useState<AuditFilters>(initialFilters);
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState({ totalPages: 1, totalElements: 0, first: true, last: true });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const loadRequest = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++loadRequest.current;
    setLoading(true);
    setError(undefined);
    try {
      const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE), sort: filters.sort });
      if (filters.actorUserId.trim()) params.set("actorUserId", filters.actorUserId.trim());
      if (filters.action.trim()) params.set("action", filters.action.trim());
      if (filters.entityType.trim()) params.set("entityType", filters.entityType.trim());
      if (filters.entityId.trim()) params.set("entityId", filters.entityId.trim());
      if (filters.requestId.trim()) params.set("requestId", filters.requestId.trim());
      const createdFrom = toIso(filters.createdFrom);
      const createdTo = toIso(filters.createdTo);
      if (createdFrom) params.set("createdFrom", createdFrom);
      if (createdTo) params.set("createdTo", createdTo);

      const response = await requestBrowserApi("GET", `/api/v1/admin/audit-logs?${params.toString()}`);
      if (!response.ok) throw await readApiError(response);
      const payload = await response.json() as AuditPage;
      if (requestId !== loadRequest.current) return;
      const content = payload.content ?? [];
      setRows(content);
      setPageInfo({
        totalPages: Math.max(1, payload.totalPages ?? 1),
        totalElements: payload.totalElements ?? content.length,
        first: payload.first ?? page === 0,
        last: payload.last ?? true,
      });
    } catch (cause) {
      if (requestId === loadRequest.current) setError(cause instanceof Error ? cause.message : "Không thể tải nhật ký quản trị");
    } finally {
      if (requestId === loadRequest.current) setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => { if (active) void load(); });
    return () => { active = false; loadRequest.current += 1; };
  }, [load]);

  function applyFilters() {
    setPage(0);
    setFilters({ ...draft });
  }

  function resetFilters() {
    setPage(0);
    setDraft(initialFilters);
    setFilters(initialFilters);
  }

  return <div className="admin-page-stack">
    <AdminPageHeader title="Nhật ký quản trị" subtitle="Chỉ đọc · Dữ liệu riêng tư không lưu ngoại tuyến" action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <AdminSurface>
      <h2>Bộ lọc</h2>
      <form onSubmit={event => { event.preventDefault(); applyFilters(); }}>
        <div className="admin-filter-grid admin-filter-grid--audit">
          <label className="admin-field"><span>Người thực hiện</span><input value={draft.actorUserId} onChange={event => setDraft(current => ({ ...current, actorUserId: event.target.value }))} placeholder="UUID quản trị viên" /></label>
          <label className="admin-field"><span>Hành động</span><input value={draft.action} onChange={event => setDraft(current => ({ ...current, action: event.target.value }))} placeholder="Tất cả hành động" /></label>
          <label className="admin-field"><span>Loại đối tượng</span><input value={draft.entityType} onChange={event => setDraft(current => ({ ...current, entityType: event.target.value }))} placeholder="Tất cả đối tượng" /></label>
          <label className="admin-field"><span>ID đối tượng</span><input value={draft.entityId} onChange={event => setDraft(current => ({ ...current, entityId: event.target.value }))} placeholder="UUID đối tượng" /></label>
          <label className="admin-field"><span>Mã yêu cầu</span><input value={draft.requestId} onChange={event => setDraft(current => ({ ...current, requestId: event.target.value }))} placeholder="requestId" /></label>
          <label className="admin-field"><span>Từ thời điểm</span><input type="datetime-local" value={draft.createdFrom} onChange={event => setDraft(current => ({ ...current, createdFrom: event.target.value }))} /></label>
          <label className="admin-field"><span>Đến thời điểm</span><input type="datetime-local" value={draft.createdTo} onChange={event => setDraft(current => ({ ...current, createdTo: event.target.value }))} /></label>
          <label className="admin-field"><span>Sắp xếp</span><select value={draft.sort} onChange={event => setDraft(current => ({ ...current, sort: event.target.value }))}><option value="CREATED_DESC">Mới nhất trước</option><option value="CREATED_ASC">Cũ nhất trước</option></select></label>
        </div>
        <div className="admin-filter-actions"><button className="admin-button admin-button--secondary" type="submit" disabled={loading}>Áp dụng</button><button className="admin-button admin-button--secondary" type="button" onClick={resetFilters} disabled={loading}>Đặt lại</button></div>
      </form>
    </AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải nhật ký…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Thử lại</button></AdminSurface> : null}
    {!loading && !error ? <>
      <div className="admin-audit-list" role="list">
        {rows.length ? rows.map((row, index) => <article className="admin-audit-row" role="listitem" key={row.id ?? index}>
          <strong title={row.actorUserId ?? row.actorType}>{row.actorType === "SYSTEM" ? "Hệ thống" : row.actorUserId ?? row.actorType ?? "—"}</strong>
          <span title={row.action}>{row.action ?? "—"}</span>
          <span title={`${row.entityType ?? ""} ${row.entityId ?? ""}`}>{row.entityType ?? "—"}{row.entityId ? ` · ${row.entityId}` : ""}</span>
          <small>{formatAdminDate(row.createdAt)}</small>
          <small title={safeMetadataSummary(row.metadata)}>{safeMetadataSummary(row.metadata)}{row.requestId ? ` · requestId ${row.requestId}` : ""}</small>
        </article>) : <AdminSurface><p className="admin-muted">Không có mục phù hợp bộ lọc.</p></AdminSurface>}
      </div>
      <div className="admin-pagination"><span>Trang {page + 1} / {pageInfo.totalPages} · tối đa {PAGE_SIZE} mục mỗi trang · {pageInfo.totalElements} mục</span><div><button className="admin-button admin-button--secondary" disabled={pageInfo.first} onClick={() => setPage(value => Math.max(0, value - 1))}>Trước</button><button className="admin-button admin-button--secondary" disabled={pageInfo.last} onClick={() => setPage(value => value + 1)}>Sau</button></div></div>
    </> : null}
  </div>;
}
