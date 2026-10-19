"use client";

import { useCallback, useEffect, useState } from "react";
import {
  ShieldCheck,
  LockKey,
  SlidersHorizontal,
  MagnifyingGlass,
  ArrowsClockwise,
  Cpu,
  User,
  Clock,
  CaretLeft,
  CaretRight,
} from "@phosphor-icons/react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { SelectDropdown } from "@/components/ui/select-dropdown";
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

  const load = useCallback(async (isActive: () => boolean = () => true) => {
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
      const content = payload.content ?? [];
      if (!isActive()) return;
      setRows(content);
      setPageInfo({
        totalPages: Math.max(1, payload.totalPages ?? 1),
        totalElements: payload.totalElements ?? content.length,
        first: payload.first ?? page === 0,
        last: payload.last ?? true,
      });
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải nhật ký quản trị");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, [filters, page]);

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

  function applyFilters() {
    setLoading(true);
    setError(undefined);
    setPage(0);
    setFilters({ ...draft });
  }

  function resetFilters() {
    setLoading(true);
    setError(undefined);
    setPage(0);
    setDraft(initialFilters);
    setFilters(initialFilters);
  }

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Nhật ký quản trị"
        subtitle="Chỉ đọc · Dữ liệu riêng tư không lưu ngoại tuyến"
        action={
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <AdminStatusChip label="Chỉ đọc" tone="neutral" />
            <span
              style={{
                display: "inline-flex",
                alignItems: "center",
                gap: "5px",
                fontSize: "12px",
                color: "#047857",
                background: "#ecfdf5",
                border: "1px solid #a7f3d0",
                borderRadius: "999px",
                padding: "3px 10px",
                fontWeight: 500,
              }}
            >
              <ShieldCheck size={14} weight="bold" aria-hidden="true" />
              <span>Bảo mật kiểm toán</span>
            </span>
          </div>
        }
      />

      <div
        style={{
          display: "flex",
          alignItems: "center",
          justifyContent: "space-between",
          flexWrap: "wrap",
          gap: "12px",
          background: "#f8fafc",
          border: "1px solid #e2e8f0",
          borderRadius: "10px",
          padding: "12px 16px",
          fontSize: "13px",
          color: "#334155",
        }}
      >
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <ShieldCheck size={20} weight="fill" color="#047857" aria-hidden="true" />
          <span>
            <strong>Hệ thống lưu vết bất biến (Append-Only Audit Log)</strong> — Mọi hành vi quản trị viên và tự động hoá đều được ghi lại với Trace ID.
          </span>
        </div>
        <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "12px", color: "#64748b" }}>
          <LockKey size={15} weight="bold" aria-hidden="true" />
          <span>Dữ liệu nhạy cảm được che chắn tự động (Masked PII)</span>
        </div>
      </div>

      <AdminSurface>
        <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", marginBottom: "12px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <SlidersHorizontal size={18} weight="bold" color="#047857" aria-hidden="true" />
            <h2 style={{ margin: 0, fontSize: "15px", fontWeight: 600 }}>Bộ lọc</h2>
          </div>
          <span style={{ fontSize: "12px", color: "var(--admin-text-secondary)" }}>
            {Object.values(draft).filter(Boolean).length > 1 ? "Đang áp dụng tiêu chí tùy chỉnh" : "Toàn bộ lịch sử ghi vết"}
          </span>
        </div>
        <form onSubmit={event => { event.preventDefault(); applyFilters(); }}>
          <div className="admin-filter-grid admin-filter-grid--audit">
            <label className="admin-field">
              <span>Người thực hiện</span>
              <input
                value={draft.actorUserId}
                onChange={event => setDraft(current => ({ ...current, actorUserId: event.target.value }))}
                placeholder="UUID quản trị viên"
              />
            </label>
            <label className="admin-field">
              <span>Hành động</span>
              <input
                value={draft.action}
                onChange={event => setDraft(current => ({ ...current, action: event.target.value }))}
                placeholder="Tất cả hành động"
              />
            </label>
            <label className="admin-field">
              <span>Loại đối tượng</span>
              <input
                value={draft.entityType}
                onChange={event => setDraft(current => ({ ...current, entityType: event.target.value }))}
                placeholder="Tất cả đối tượng"
              />
            </label>
            <label className="admin-field">
              <span>ID đối tượng</span>
              <input
                value={draft.entityId}
                onChange={event => setDraft(current => ({ ...current, entityId: event.target.value }))}
                placeholder="UUID đối tượng"
              />
            </label>
            <label className="admin-field">
              <span>Mã yêu cầu</span>
              <input
                value={draft.requestId}
                onChange={event => setDraft(current => ({ ...current, requestId: event.target.value }))}
                placeholder="requestId"
              />
            </label>
            <label className="admin-field">
              <span>Từ thời điểm</span>
              <input
                type="datetime-local"
                value={draft.createdFrom}
                onChange={event => setDraft(current => ({ ...current, createdFrom: event.target.value }))}
              />
            </label>
            <label className="admin-field">
              <span>Đến thời điểm</span>
              <input
                type="datetime-local"
                value={draft.createdTo}
                onChange={event => setDraft(current => ({ ...current, createdTo: event.target.value }))}
              />
            </label>
            <div className="admin-field">
              <SelectDropdown<string>
                label="Sắp xếp"
                aria-label="Sắp xếp"
                value={draft.sort}
                radius="md"
                size="md"
                options={[
                  { value: "CREATED_DESC", label: "Mới nhất trước" },
                  { value: "CREATED_ASC", label: "Cũ nhất trước" },
                ]}
                onChange={val => setDraft(current => ({ ...current, sort: val }))}
              />
            </div>
          </div>
          <div className="admin-filter-actions" style={{ display: "flex", gap: "8px", marginTop: "14px" }}>
            <button
              className="admin-button admin-button--primary"
              type="submit"
              disabled={loading}
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <MagnifyingGlass size={16} weight="bold" aria-hidden="true" />
              <span>Áp dụng</span>
            </button>
            <button
              className="admin-button admin-button--secondary"
              type="button"
              onClick={resetFilters}
              disabled={loading}
              style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
            >
              <ArrowsClockwise size={16} aria-hidden="true" />
              <span>Đặt lại</span>
            </button>
          </div>
        </form>
      </AdminSurface>

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải nhật ký…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
          <button
            className="admin-button admin-button--secondary"
            onClick={reload}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px", marginTop: "8px" }}
          >
            <ArrowsClockwise size={16} aria-hidden="true" />
            <span>Thử lại</span>
          </button>
        </AdminSurface>
      ) : null}

      {!loading && !error ? (
        <>
          {rows.length ? (
            <div className="admin-audit-scroll" role="region" aria-label="Nhật ký quản trị có thể cuộn ngang" tabIndex={0} style={{ display: "grid", gap: "8px", minWidth: 0 }}>
              <div
                className="admin-audit-columns"
                style={{
                  display: "grid",
                  gridTemplateColumns: "minmax(130px, 180px) minmax(130px, 180px) minmax(150px, 190px) minmax(130px, 170px) minmax(220px, 1fr)",
                  gap: "12px",
                  padding: "0 14px",
                  fontSize: "12px",
                  fontWeight: 600,
                  color: "var(--admin-text-secondary)",
                  textTransform: "uppercase",
                  letterSpacing: "0.03em",
                }}
                aria-hidden="true"
              >
                <span>Người thực hiện</span>
                <span>Hành động</span>
                <span>Đối tượng tác động</span>
                <span>Thời điểm</span>
                <span>Chi tiết & Trace ID</span>
              </div>
              <div className="admin-audit-list" role="list">
                {rows.map((row, index) => {
                  const isSystem = row.actorType === "SYSTEM";
                  return (
                    <article className="admin-audit-row" role="listitem" key={row.id ?? index}>
                      <strong
                        title={row.actorUserId ?? row.actorType}
                        style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                      >
                        {isSystem ? (
                          <>
                            <span
                              style={{
                                display: "inline-flex",
                                alignItems: "center",
                                justifyContent: "center",
                                width: "20px",
                                height: "20px",
                                borderRadius: "4px",
                                background: "#ecfdf5",
                                color: "#047857",
                                flexShrink: 0,
                              }}
                            >
                              <Cpu size={13} weight="bold" aria-hidden="true" />
                            </span>
                            <span>Hệ thống</span>
                          </>
                        ) : (
                          <>
                            <span
                              style={{
                                display: "inline-flex",
                                alignItems: "center",
                                justifyContent: "center",
                                width: "20px",
                                height: "20px",
                                borderRadius: "4px",
                                background: "#f1f5f9",
                                color: "#475569",
                                flexShrink: 0,
                              }}
                            >
                              <User size={13} weight="bold" aria-hidden="true" />
                            </span>
                            <span style={{ fontFamily: "monospace", fontSize: "13px" }}>
                              {row.actorUserId ?? row.actorType ?? "—"}
                            </span>
                          </>
                        )}
                      </strong>
                      <span title={row.action}>
                        <span
                          style={{
                            background: "#f1f5f9",
                            border: "1px solid #e2e8f0",
                            borderRadius: "4px",
                            padding: "2px 6px",
                            fontSize: "12px",
                            fontFamily: "monospace",
                            fontWeight: 600,
                            color: "#0f172a",
                          }}
                        >
                          {row.action ?? "—"}
                        </span>
                      </span>
                      <span title={`${row.entityType ?? ""} ${row.entityId ?? ""}`}>
                        <strong style={{ fontWeight: 600, color: "#0f172a" }}>{row.entityType ?? "—"}</strong>
                        {row.entityId ? (
                          <span style={{ fontFamily: "monospace", color: "#64748b", fontSize: "12px" }}>
                            {" · " + row.entityId}
                          </span>
                        ) : (
                          ""
                        )}
                      </span>
                      <small style={{ display: "inline-flex", alignItems: "center", gap: "5px" }}>
                        <Clock size={13} color="#94a3b8" aria-hidden="true" />
                        <span>{formatAdminDate(row.createdAt)}</span>
                      </small>
                      <small
                        title={safeMetadataSummary(row.metadata)}
                        style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                      >
                        <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                          {safeMetadataSummary(row.metadata)}
                        </span>
                        {row.requestId ? (
                          <span
                            style={{
                              background: "#f8fafc",
                              border: "1px solid #e2e8f0",
                              borderRadius: "4px",
                              padding: "1px 5px",
                              fontSize: "11px",
                              fontFamily: "monospace",
                              color: "#475569",
                              flexShrink: 0,
                            }}
                          >
                            req:
                            {row.requestId.length > 8 ? row.requestId.slice(0, 8) + "…" : row.requestId}
                          </span>
                        ) : null}
                      </small>
                    </article>
                  );
                })}
              </div>
            </div>
          ) : (
            <div className="admin-audit-list" role="list">
              <AdminSurface>
                <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", padding: "20px 0", gap: "8px" }}>
                  <MagnifyingGlass size={30} color="#94a3b8" aria-hidden="true" />
                  <p className="admin-muted" style={{ margin: 0 }}>Không có mục phù hợp bộ lọc.</p>
                </div>
              </AdminSurface>
            </div>
          )}
          <div className="admin-pagination">
            <span>
              Trang {page + 1} / {pageInfo.totalPages} · tối đa {PAGE_SIZE} mục mỗi trang · {pageInfo.totalElements} mục
            </span>
            <div>
              <button
                className="admin-button admin-button--secondary"
                disabled={pageInfo.first}
                onClick={() => {
                  setLoading(true);
                  setError(undefined);
                  setPage(value => Math.max(0, value - 1));
                }}
                style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
              >
                <CaretLeft size={14} aria-hidden="true" />
                <span>Trước</span>
              </button>
              <button
                className="admin-button admin-button--secondary"
                disabled={pageInfo.last}
                onClick={() => {
                  setLoading(true);
                  setError(undefined);
                  setPage(value => value + 1);
                }}
                style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
              >
                <span>Sau</span>
                <CaretRight size={14} aria-hidden="true" />
              </button>
            </div>
          </div>
        </>
      ) : null}
    </div>
  );
}
