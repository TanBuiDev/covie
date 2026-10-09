"use client";

import { useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  LockKey,
  MagnifyingGlass,
  ShieldCheck,
} from "@phosphor-icons/react";
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
  for (const key of ["items", "content", "data"]) {
    if (Array.isArray(record[key])) return record[key] as Record<string, unknown>[];
  }
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

export function AdminReadOnlyCollection({
  title,
  subtitle,
  path,
  columns,
  statusKey,
  note,
  expandHierarchy = false,
}: Props) {
  const [rows, setRows] = useState<Record<string, unknown>[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [filterText, setFilterText] = useState("");

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

  const filteredRows = useMemo(() => {
    if (!filterText.trim()) return rows;
    const q = filterText.trim().toLowerCase();
    return rows.filter(row => {
      return Object.values(row).some(v => String(v ?? "").toLowerCase().includes(q));
    });
  }, [rows, filterText]);

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title={title}
        subtitle={subtitle}
        action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />}
      />

      <AdminSurface className="admin-contract-note">
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "8px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <LockKey size={18} weight="duotone" style={{ color: "#2563eb" }} />
            <strong>Contract hiện tại</strong>
          </div>
          <span style={{ fontSize: "12px", color: "var(--admin-text-secondary, #64748b)" }}>
            Tổng số: <strong>{rows.length}</strong> mục
          </span>
        </div>
        <p>{note}</p>
        <div className="admin-governance-pills" style={{ marginTop: "4px" }}>
          <span className="admin-governance-pill">
            <ShieldCheck size={14} weight="fill" style={{ color: "#047857" }} />
            <span>OpenAPI v1.0 Validated Endpoint</span>
          </span>
          <span className="admin-governance-pill">
            <ShieldCheck size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Không mở mutation khi chưa có contract</span>
          </span>
        </div>
      </AdminSurface>

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải dữ liệu…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
        </AdminSurface>
      ) : null}

      {!loading && !error ? (
        <>
          {rows.length > 0 ? (
            <div className="admin-toolbar-row">
              <div className="admin-search-box">
                <MagnifyingGlass size={16} weight="bold" style={{ color: "#94a3b8" }} />
                <input
                  type="text"
                  value={filterText}
                  onChange={(e) => setFilterText(e.target.value)}
                  placeholder={`Lọc trong ${rows.length} ${title.toLowerCase()}…`}
                />
              </div>
            </div>
          ) : null}

          <AdminSurface className="admin-table-surface">
            <div className="admin-table-scroll">
              <table className="admin-data-table">
                <thead>
                  <tr>
                    {columns.map(column => (
                      <th key={column.key}>{column.label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {rows.length ? (
                    filteredRows.length ? (
                      filteredRows.map((row, index) => (
                        <tr key={String(row.id ?? index)}>
                          {columns.map(column => {
                            const value = row[column.key];
                            return (
                              <td key={column.key}>
                                {statusKey === column.key ? (() => {
                                  const status = statusPresentation(value);
                                  return <AdminStatusChip label={status.label} tone={status.tone} />;
                                })() : expandHierarchy ? (
                                  hierarchyValue(row, column.key, rows)
                                ) : (
                                  String(value ?? "—")
                                )}
                              </td>
                            );
                          })}
                        </tr>
                      ))
                    ) : (
                      <tr>
                        <td colSpan={columns.length}>
                          <span className="admin-muted">Không tìm thấy kết quả phù hợp.</span>
                        </td>
                      </tr>
                    )
                  ) : (
                    <tr>
                      <td colSpan={columns.length}>
                        <span className="admin-muted">Chưa có dữ liệu.</span>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </AdminSurface>
        </>
      ) : null}
    </div>
  );
}
