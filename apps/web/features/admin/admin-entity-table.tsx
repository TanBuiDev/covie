"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  MagnifyingGlass,
  ShieldCheck,
  ShieldWarning,
  Storefront,
  User,
  Copy,
  Check,
  CalendarBlank,
  Clock,
  ArrowSquareOut,
  ArrowRight,
  Scroll,
  Package,
  Fingerprint,
} from "@phosphor-icons/react/dist/ssr";
import { AdminSuspensionAction } from "./admin-suspension-action";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  presentAccountStatus,
  presentModerationStatus,
} from "./admin-ui";
import styles from "./admin-entity-table.module.css";

type Row = Record<string, unknown> & { id: string; status: string; globalRoles?: string[] };
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
  columns: string[];
}) {
  const [items, setItems] = useState<Row[]>([]);
  const [selectedId, setSelectedId] = useState<string>();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [total, setTotal] = useState(0);
  const [filterText, setFilterText] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [copiedId, setCopiedId] = useState(false);

  const handleCopyId = (id: string) => {
    void navigator.clipboard.writeText(id);
    setCopiedId(true);
    setTimeout(() => setCopiedId(false), 2000);
  };

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
                                  const moderation = presentModerationStatus(item.moderationStatus as string);
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
                <div className={styles.inspectorCard}>
                  {/* PROFILE HEADER */}
                  <div className={styles.inspectorHeader}>
                    <div className={styles.profileRow}>
                      <div
                        className={`${styles.avatar} ${
                          type === "users" ? styles.avatarUser : styles.avatarMerchant
                        }`}
                      >
                        {type === "users" ? (
                          <User size={26} weight="bold" />
                        ) : (
                          <Storefront size={26} weight="bold" />
                        )}
                      </div>
                      <div className={styles.profileInfo}>
                        <span
                          className={styles.displayName}
                          title={String(selected.displayName ?? selected.name ?? selected.id)}
                        >
                          {String(selected.displayName ?? selected.name ?? selected.id)}
                        </span>
                        <div className={styles.roleSubtitle}>
                          {type === "users" ? (
                            Array.isArray(selected.globalRoles) &&
                            (selected.globalRoles as string[]).includes("ADMIN") ? (
                              <span className={`${styles.roleBadge} ${styles.roleBadgeAdmin}`}>
                                Quản trị viên sàn
                              </span>
                            ) : (
                              <span className={styles.roleBadge}>Cư dân CoVie</span>
                            )
                          ) : (
                            <span className={styles.roleBadge}>Đối tác kinh doanh</span>
                          )}
                          {type === "merchants" && selected.slug ? (
                            <span className="admin-caption" style={{ fontFamily: "monospace" }}>
                              @{String(selected.slug)}
                            </span>
                          ) : null}
                        </div>
                      </div>
                    </div>

                    <div className={styles.statusBanner}>
                      <span className={styles.statusBannerLabel}>Trạng thái tài khoản</span>
                      <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                        <AdminStatusChip
                          label={presentAccountStatus(selected.status).label}
                          tone={presentAccountStatus(selected.status).tone}
                        />
                        {type === "merchants" && selected.moderationStatus ? (
                          <AdminStatusChip
                            label={presentModerationStatus(selected.moderationStatus as string).label}
                            tone={presentModerationStatus(selected.moderationStatus as string).tone}
                          />
                        ) : null}
                      </div>
                    </div>
                  </div>

                  {/* IDENTIFIERS & ATTRIBUTES */}
                  <div className={styles.sectionBlock}>
                    <div className={styles.sectionTitle}>Thông tin chi tiết</div>
                    <div className={styles.detailsList}>
                      <div className={styles.detailRow}>
                        <span className={styles.detailLabel}>
                          <Fingerprint size={14} weight="bold" />
                          <span>Mã định danh</span>
                        </span>
                        <button
                          type="button"
                          className={`${styles.idCopyBadge} ${copiedId ? styles.idCopiedSuccess : ""}`}
                          onClick={() => handleCopyId(selected.id)}
                          title="Bấm để sao chép toàn bộ UUID"
                        >
                          {copiedId ? <Check size={12} weight="bold" /> : <Copy size={12} />}
                          <span>{copiedId ? "Đã sao chép" : `${selected.id.slice(0, 8)}…${selected.id.slice(-4)}`}</span>
                        </button>
                      </div>

                      {type === "users" && Array.isArray(selected.globalRoles) ? (
                        <div className={styles.detailRow}>
                          <span className={styles.detailLabel}>
                            <ShieldCheck size={14} weight="bold" />
                            <span>Quyền hệ thống</span>
                          </span>
                          <span className={styles.detailValue}>
                            {(selected.globalRoles as string[]).join(", ") || "USER"}
                          </span>
                        </div>
                      ) : null}

                      {selected.createdAt ? (
                        <div className={styles.detailRow}>
                          <span className={styles.detailLabel}>
                            <CalendarBlank size={14} weight="bold" />
                            <span>Ngày tham gia</span>
                          </span>
                          <span className={styles.detailValue}>
                            {formatAdminDate(selected.createdAt as string)}
                          </span>
                        </div>
                      ) : null}

                      {selected.updatedAt ? (
                        <div className={styles.detailRow}>
                          <span className={styles.detailLabel}>
                            <Clock size={14} weight="bold" />
                            <span>Cập nhật gần nhất</span>
                          </span>
                          <span className={styles.detailValue}>
                            {formatAdminDate(selected.updatedAt as string)}
                          </span>
                        </div>
                      ) : null}
                    </div>
                  </div>

                  {/* ACTIONS CARD (SUSPEND / RESTORE) */}
                  <div className={styles.actionBox}>
                    <div className={styles.actionBoxHeader}>
                      <ShieldWarning size={18} weight="duotone" style={{ color: "#ea580c" }} />
                      <span>Tác vụ tài khoản</span>
                    </div>
                    <p className={styles.actionNote}>
                      {type === "users"
                        ? "Đình chỉ sẽ khóa quyền đặt hàng và phiên đăng nhập của người dùng ngay lập tức."
                        : "Đình chỉ sẽ tạm ẩn gian hàng và ngừng nhận đơn mới trên toàn sàn."}
                    </p>
                    <AdminSuspensionAction
                      status={selected.status}
                      onSubmit={(operation, reason) => mutate(selected.id, operation, reason)}
                    />
                  </div>

                  {/* QUICK SHORTCUTS */}
                  <div className={styles.quickLinks}>
                    <div className={styles.sectionTitle}>Lối tắt liên quan</div>
                    <Link
                      href={`/admin/audit?q=${encodeURIComponent(selected.id)}`}
                      className={styles.quickLinkItem}
                      title="Xem nhật ký kiểm toán liên quan đến đối tượng này"
                    >
                      <div className={styles.quickLinkLeft}>
                        <Scroll size={14} weight="bold" />
                        <span>Nhật ký kiểm toán</span>
                      </div>
                      <ArrowRight size={12} weight="bold" />
                    </Link>

                    <Link
                      href="/admin/orders"
                      className={styles.quickLinkItem}
                      title="Xem danh sách đơn hàng toàn sàn"
                    >
                      <div className={styles.quickLinkLeft}>
                        <Package size={14} weight="bold" />
                        <span>Đơn hàng toàn sàn</span>
                      </div>
                      <ArrowRight size={12} weight="bold" />
                    </Link>

                    {type === "merchants" ? (
                      <Link
                        href={`/merchants/${selected.id}`}
                        target="_blank"
                        rel="noreferrer"
                        className={styles.quickLinkItem}
                        title="Xem trang gian hàng công khai trên chợ cư dân"
                      >
                        <div className={styles.quickLinkLeft}>
                          <Storefront size={14} weight="bold" />
                          <span>Xem gian hàng công khai</span>
                        </div>
                        <ArrowSquareOut size={12} weight="bold" />
                      </Link>
                    ) : null}
                  </div>
                </div>
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
        </>
      ) : null}
    </div>
  );
}
