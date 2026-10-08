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
  expandHierarchy?: boolean;
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

function hierarchyValue(row: Record<string, unknown>, key: string, rows: Record<string, unknown>[]) {
  const value = row[key];
  if (key === "parentId" && value) {
    return String(rows.find(candidate => String(candidate.id ?? "") === String(value))?.name ?? value);
  }
  if (key === "areaType") {
    const labels: Record<string, string> = {
      COMMUNITY: "Cộng đồng",
      ZONE: "Khu vực",
      CLUSTER: "Cụm",
      BUILDING: "Tòa nhà",
      WARD: "Phường",
    };
    return labels[String(value ?? "")] ?? String(value ?? "—");
  }
  return String(value ?? "—");
}

export function AdminReadOnlyCollection({ title, subtitle, path, columns, statusKey, note, expandHierarchy = false }: Props) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();

  useEffect(() => {
    let active = true;
    Promise.resolve().then(async () => {
      if (!active) return undefined;
      setLoading(true);
      setError(undefined);
      const response = await requestBrowserApi("GET", path);
      if (!response.ok) throw await readApiError(response);
      const rootRows = normalizeRows(await response.json());
      if (!expandHierarchy) return rootRows;

      const allRows = [...rootRows];
      const pending = rootRows.map(row => String(row.id ?? "")).filter(Boolean);
      const visited = new Set<string>();
      while (pending.length && active) {
        const parentId = pending.shift()!;
        if (visited.has(parentId)) continue;
        visited.add(parentId);
        const separator = path.includes("?") ? "&" : "?";
        const childResponse = await requestBrowserApi("GET", `${path}${separator}parentId=${encodeURIComponent(parentId)}`);
        if (!childResponse.ok) throw await readApiError(childResponse);
        const children = normalizeRows(await childResponse.json());
        allRows.push(...children);
        for (const child of children) {
          const childId = String(child.id ?? "");
          if (childId && !visited.has(childId)) pending.push(childId);
        }
      }
      return allRows;
    }).then(payload => { if (active && payload) setRows(payload); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [expandHierarchy, path]);

  return <div className="admin-page-stack">
    <AdminPageHeader title={title} subtitle={subtitle} action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <AdminSurface className="admin-contract-note"><strong>Contract hiện tại</strong><p>{note}</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải dữ liệu…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <AdminSurface className="admin-table-surface"><div className="admin-table-scroll"><table className="admin-data-table"><thead><tr>{columns.map(column => <th key={column.key}>{column.label}</th>)}</tr></thead><tbody>
      {rows.length ? rows.map((row, index) => <tr key={String(row.id ?? index)}>{columns.map(column => {
        const value = row[column.key];
        return <td key={column.key}>{statusKey === column.key ? (() => { const status = statusPresentation(value); return <AdminStatusChip label={status.label} tone={status.tone} />; })() : expandHierarchy ? hierarchyValue(row, column.key, rows) : String(value ?? "—")}</td>;
      })}</tr>) : <tr><td colSpan={columns.length}><span className="admin-muted">Chưa có dữ liệu.</span></td></tr>}
    </tbody></table></div></AdminSurface> : null}
  </div>;
}
