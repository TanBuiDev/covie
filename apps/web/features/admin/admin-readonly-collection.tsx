"use client";

import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";

type Column = { key: string; label: string };

type Props = {
  title: string;
  subtitle: string;
  path: string;
  columns: Column[];
  statusKey?: string;
  note: string;
};

function normalizeRows(payload: unknown): Record<string, unknown>[] {
  if (Array.isArray(payload)) return payload as Record<string, unknown>[];
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  for (const key of ["items", "content", "data"]) if (Array.isArray(record[key])) return record[key] as Record<string, unknown>[];
  return [];
}

function statusPresentation(raw: unknown) {
  if (raw === "ACTIVE") return { label: "Đang hoạt động", tone: "success" as const };
  if (raw === "INACTIVE") return { label: "Không hoạt động", tone: "neutral" as const };
  if (raw === "DELETED") return { label: "Đã xoá", tone: "error" as const };
  return { label: "Trạng thái đã được cập nhật", tone: "neutral" as const };
}

export function AdminReadOnlyCollection({ title, subtitle, path, columns, statusKey, note }: Props) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    setLoading(true);
    requestBrowserApi("GET", path).then(async response => {
      if (!response.ok) throw await readApiError(response);
      return response.json();
    }).then(payload => { if (active) setRows(normalizeRows(payload)); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [path]);

  return <div className="admin-page-stack">
    <AdminPageHeader title={title} subtitle={subtitle} action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <AdminSurface className="admin-contract-note"><strong>Contract hiện tại</strong><p>{note}</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải dữ liệu…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <AdminSurface className="admin-table-surface"><div className="admin-table-scroll"><table className="admin-data-table"><thead><tr>{columns.map(column => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>
      {rows.length ? rows.map((row, index) => <tr key={String(row.id ?? index)}>{columns.map(column => {
        const value = row[column.key];
        return <td key={column.key}>{statusKey === column.key ? (() => { const status = statusPresentation(value); return <AdminStatusChip label={status.label} tone={status.tone} />; })() : String(value ?? "—")}</td>;
      })}</tr>) : <tr><td colSpan={columns.length}><span className="admin-muted">Chưa có dữ liệu.</span></td></tr>}
    </tbody></table></div></AdminSurface> : null}
  </div>;
}
