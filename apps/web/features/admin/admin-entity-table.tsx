"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  MagnifyingGlass,
  ShieldCheck,
  ShieldWarning,
  Storefront,
  User,
} from "@phosphor-icons/react";
import { AdminSuspensionAction } from "./admin-suspension-action";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  presentAccountStatus,
  presentModerationStatus,
} from "./admin-ui";

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

export function AdminEntityTable({
  type,
  title,
  columns,
}: {
  type: "users" | "merchants";
  title: string;
  columns: string[]
}) {
  const [items, setItems] = useState<Row[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [filterText, setFilterText] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

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

  const filteredItems = useMemo(() => {
    let result = items;
    if (statusFilter !== "ALL") {
      if (statusFilter === "PENDING_MOD") {
        result = result.filter(item => item.moderationStatus === "PENDING");
      } else if (statusFilter === "APPROVED_MOD") {
        result = result.filter(item => item.moderationStatus === "APPROVED");
      } else {
        result = result.filter(item => item.status === statusFilter);
      }
    }
    if (filterText.trim()) {
      const q = filterText.trim().toLowerCase();
      result = result.filter(item => {
        const name = String(item.displayName ?? item.name ?? "").toLowerCase();
        const id = String(item.id ?? "").toLowerCase();
        return name.includes(q) || id.includes(q);
      });
    }
    return result;
  }, [items, statusFilter, filterText]);

  const selected = filteredItems.find(item => item.id === selectedId) ?? filteredItems[0] ?? items.find(item => item.id === selectedId) ?? items[0];
  const subtitle = type === "users" ? "Quản trị vận hành & tài khoản cư dân CoVie" : "Quản lý tài khoản người bán và đối tác cung cấp dịch vụ";
  const totalPages = Math.max(1, Math.ceil(total / PAGE_SIZE));

  const statusTabs = type === "users" ? [
    { key: "ALL", label: "Tất cả" },
    { key: "ACTIVE", label: "Hoạt động" },
    { key: "SUSPENDED", label: "Tạm ngưng" },
    { key: "PAUSED", label: "Tạm dừng" },
  ] : [
    { key: "ALL", label: "Tất cả" },
    { key: "ACTIVE", label: "Hoạt động" },
    { key: "SUSPENDED", label: "Tạm ngưng" },
    { key: "PENDING_MOD", label: "Chờ duyệt" },
    { key: "APPROVED_MOD", label: "Đã duyệt" },
  ];

  return (
    <div className="admin-page-stack">
      <AdminPageHeader title={title} subtitle={subtitle} />
      {loading ? <AdminSurface><p className="admin-muted" aria-live="polite">Đang tải dữ liệu…</p></AdminSurface> : null}
      {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={reload}>Thử lại</button></AdminSurface> : null}
      {!loading && !error && items.length === 0 ? <AdminSurface><p className="admin-muted">Chưa có dữ liệu.</p></AdminSurface> : null}
      {!loading && !error && items.length > 0 ? (
        <>
          <div className="admin-toolbar-row">
            <div className="admin-search-box">
              <MagnifyingGlass size={16} weight="bold" style={{ color: "#94a3b8" }} />
              <input
                type="text"
                value={filterText}
                onChange={(e) => setFilterText(e.target.value)}
                placeholder={type === "users" ? "Tìm theo tên cư dân hoặc ID…" : "Tìm theo tên gian hàng hoặc ID…"}
              />
            </div>

            <div className="admin-status-tabs">
              {statusTabs.map((tab) => (
                <button
                  key={tab.key}
                  type="button"
                  className="admin-status-tab"
                  data-active={statusFilter === tab.key || undefined}
                  onClick={() => setStatusFilter(tab.key)}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          {filteredItems.length === 0 ? (
            <AdminSurface>
              <p className="admin-muted" style={{ textAlign: "center", padding: "20px 0" }}>
                Không tìm thấy kết quả phù hợp với bộ lọc hiện tại.
              </p>
            </AdminSurface>
          ) : (
            <div className="admin-split-grid">
              <AdminSurface className="admin-table-surface">
                <div className="admin-table-scroll">
                  <table className="admin-data-table">
                    <thead>
                      <tr>
                        {columns.map(column => <th key={column}>{columnLabels[column] ?? column}</th>)}
                        <th>Tác vụ</th>
                      </tr>
                    </thead>
                    <tbody>
                      {filteredItems.map(item => {
                        const status = presentAccountStatus(item.status);
                        return (
                          <tr key={item.id} data-selected={item.id === selected?.id || undefined}>
                            {columns.map(column => (
                              <td key={column}>
                                {column === "status" ? (
                                  <AdminStatusChip label={status.label} tone={status.tone} />
                                ) : column === "moderationStatus" ? (() => {
                                  const moderation = presentModerationStatus(item.moderationStatus);
                                  return <AdminStatusChip label={moderation.label} tone={moderation.tone} />;
                                })() : (
                                  displayValue(item, column)
                                )}
                              </td>
                            ))}
                            <td>
                              <button className="admin-row-action" type="button" onClick={() => setSelectedId(item.id)}>
                                Xem
                              </button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </AdminSurface>

              {selected ? (
                <AdminSurface className="admin-entity-summary">
                  <div className="admin-entity-summary__top" style={{ alignItems: "center", display: "flex", gap: "12px" }}>
                    <div
                      className="admin-entity-avatar"
                      style={{
                        background: type === "users" ? "rgba(4, 120, 87, 0.08)" : "rgba(37, 99, 235, 0.08)",
                        color: type === "users" ? "#047857" : "#2563eb",
                      }}
                    >
                      {type === "users" ? <User size={22} weight="duotone" /> : <Storefront size={22} weight="duotone" />}
                    </div>
                    <div style={{ minWidth: 0, flex: 1 }}>
                      <strong style={{ display: "block", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                        {String(selected.displayName ?? selected.name ?? selected.id)}
                      </strong>
                      <small style={{ color: "var(--admin-text-tertiary, #64748b)" }}>{type === "users" ? "Tài khoản cư dân" : "Đối tác cung cấp"}</small>
                    </div>
                    <AdminStatusChip label={presentAccountStatus(selected.status).label} tone={presentAccountStatus(selected.status).tone} />
                  </div>

                  <dl style={{ marginTop: "12px" }}>
                    <div>
                      <dt>Trạng thái</dt>
                      <dd>{presentAccountStatus(selected.status).label}</dd>
                    </div>
                    {type === "merchants" ? (
                      <div>
                        <dt>Kiểm duyệt</dt>
                        <dd>{presentModerationStatus(selected.moderationStatus).label}</dd>
                      </div>
                    ) : null}
                    <div>
                      <dt>ID định danh</dt>
                      <dd style={{ fontFamily: "monospace", fontSize: "12px" }}>{selected.id}</dd>
                    </div>
                    <div>
                      <dt>Cập nhật</dt>
                      <dd>{formatAdminDate(selected.updatedAt ?? selected.createdAt)}</dd>
                    </div>
                  </dl>

                  {type === "merchants" ? (
                    <div style={{ marginTop: "10px", padding: "10px 12px", background: "#f8fafc", borderRadius: "8px", border: "1px solid #e2e8f0" }}>
                      <strong style={{ fontSize: "12px", color: "var(--admin-text, #0f172a)", display: "block" }}>Nguyên tắc đối tác CoVie</strong>
                      <p style={{ margin: "4px 0 0", fontSize: "12px", color: "var(--admin-text-secondary, #64748b)", lineHeight: "16px" }}>
                        Chỉ gian hàng được duyệt mới hiển thị trên chợ cư dân. Đình chỉ sẽ vô hiệu hóa nhận đơn ngay lập tức.
                      </p>
                    </div>
                  ) : null}

                  <div style={{ display: "flex", alignItems: "center", gap: "6px", borderTop: "1px solid #f1f5f9", paddingTop: "10px", marginTop: "4px" }}>
                    <ShieldCheck size={16} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
                    <p className="admin-caption" style={{ margin: 0 }}>
                      Dữ liệu xác thực qua Spring Security Admin Scope.
                    </p>
                  </div>
                </AdminSurface>
              ) : null}
            </div>
          )}

          {totalPages > 1 ? (
            <div className="admin-pagination">
              <span>Trang {page + 1} / {totalPages} · {total} mục</span>
              <div>
                <button
                  className="admin-button admin-button--secondary"
                  disabled={page === 0 || loading}
                  onClick={() => { setLoading(true); setError(undefined); setPage(value => Math.max(0, value - 1)); }}
                >
                  Trước
                </button>
                <button
                  className="admin-button admin-button--secondary"
                  disabled={page + 1 >= totalPages || loading}
                  onClick={() => { setLoading(true); setError(undefined); setPage(value => value + 1); }}
                >
                  Sau
                </button>
              </div>
            </div>
          ) : null}

          {selected ? (
            <AdminSurface className="admin-safety-card">
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <ShieldWarning size={20} weight="duotone" style={{ color: "#ea580c" }} />
                <h2 style={{ margin: 0 }}>Tạm ngưng / khôi phục tài khoản</h2>
              </div>
              <p>
                Mọi thao tác có tác động đến tài khoản đều yêu cầu xác nhận. Trạng thái được quyết định bởi hệ thống; màn hình này không cung cấp trường sửa trạng thái trực tiếp.
              </p>
              <AdminSuspensionAction status={selected.status} onSubmit={(operation, reason) => mutate(selected.id, operation, reason)} />
            </AdminSurface>
          ) : null}
        </>
      ) : null}
    </div>
  );
}
