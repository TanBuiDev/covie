"use client";

import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate } from "./admin-ui";

type AuditRow = Record<string, unknown> & { id?: string; actorUserId?: string; actorType?: string; action?: string; entityType?: string; entityId?: string; requestId?: string; createdAt?: string };

function normalize(payload: unknown): AuditRow[] {
  if (Array.isArray(payload)) return payload as AuditRow[];
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  for (const key of ["items", "content", "data"]) if (Array.isArray(record[key])) return record[key] as AuditRow[];
  return [];
}

export function AdminAuditLog() {
  const [rows, setRows] = useState<AuditRow[]>([]);
  const [actor, setActor] = useState("");
  const [action, setAction] = useState("");
  const [entity, setEntity] = useState("");
  const [requestId, setRequestId] = useState("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  useEffect(() => {
    let active = true;
    requestBrowserApi("GET", "/api/v1/admin/audit-logs")
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json(); })
      .then(payload => { if (active) setRows(normalize(payload)); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải nhật ký quản trị"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  const filtered = useMemo(() => rows.filter(row => {
    const includes = (value: unknown, query: string) => !query.trim() || String(value ?? "").toLowerCase().includes(query.trim().toLowerCase());
    return includes(`${row.actorType ?? ""} ${row.actorUserId ?? ""}`, actor) && includes(row.action, action) && includes(`${row.entityType ?? ""} ${row.entityId ?? ""}`, entity) && includes(row.requestId, requestId);
  }), [rows, actor, action, entity, requestId]);
  return <div className="admin-page-stack">
    <AdminPageHeader title="Nhật ký quản trị" subtitle="Chỉ đọc · Dữ liệu riêng tư không lưu ngoại tuyến" action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <AdminSurface><h2>Bộ lọc</h2><div className="admin-filter-grid"><label className="admin-field"><span>Người thực hiện</span><input value={actor} onChange={event => setActor(event.target.value)} placeholder="ID quản trị viên" /></label><label className="admin-field"><span>Hành động</span><input value={action} onChange={event => setAction(event.target.value)} placeholder="Tất cả hành động" /></label><label className="admin-field"><span>Đối tượng</span><input value={entity} onChange={event => setEntity(event.target.value)} placeholder="Tất cả đối tượng" /></label><label className="admin-field"><span>Mã yêu cầu</span><input value={requestId} onChange={event => setRequestId(event.target.value)} placeholder="requestId" /></label></div></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải nhật ký…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <div className="admin-audit-list" role="list">{filtered.length ? filtered.map((row, index) => <article className="admin-audit-row" role="listitem" key={String(row.id ?? index)}><strong>{String(row.actorType === "SYSTEM" ? "Hệ thống" : row.actorUserId ?? row.actorType ?? "—")}</strong><span>{String(row.action ?? "—")}</span><span>{String(row.entityType ?? "—")} {row.entityId ? `· ${row.entityId}` : ""}</span><small>{formatAdminDate(row.createdAt)}</small><small>{row.requestId ? `requestId ${row.requestId}` : "Không có metadata công khai"}</small></article>) : <AdminSurface><p className="admin-muted">Không có mục phù hợp bộ lọc.</p></AdminSurface>}</div> : null}
  </div>;
}
