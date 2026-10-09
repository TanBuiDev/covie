"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  ArrowLeft,
  ArrowsClockwise,
  Clock,
  Flag,
  Scroll,
  ShieldCheck,
  ShieldWarning,
  Storefront,
  Tag,
  User,
} from "@phosphor-icons/react";
import { ModerationActions, type ModerationActionKind } from "./moderation-actions";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  presentModerationStatus,
} from "./admin-ui";

type CaseSummary = { id: string; caseType?: string; status?: string; targetType?: string; targetId?: string; reasonCode?: string; openedAt?: string; resolvedAt?: string };
type CaseAction = { id?: string; action?: string; actorAdminUserId?: string; reasonCode?: string; note?: string; createdAt?: string };
type CaseDetail = { moderationCase: CaseSummary; actions?: CaseAction[] };

const contentTargets = new Set(["CATALOG_ITEM", "NEED_REQUEST", "REVIEW"]);

function availableActions(detail: CaseDetail | undefined): ModerationActionKind[] {
  if (!detail) return [];
  const current = detail.moderationCase;
  if (current.status === "OPEN") {
    if (current.caseType === "PRE_APPROVAL") return ["approve", "reject", "close"];
    if (contentTargets.has(String(current.targetType))) return ["hide-content", "close"];
    return ["close"];
  }
  const lastContentAction = [...(detail.actions ?? [])].reverse().find(action => action.action === "HIDE" || action.action === "RESTORE");
  if (current.status === "RESOLVED" && contentTargets.has(String(current.targetType)) && lastContentAction?.action === "HIDE") return ["restore-content"];
  return [];
}

function caseTypeLabel(value: unknown) {
  switch (value) {
    case "USER_REPORT": return "Báo cáo người dùng";
    case "PRE_APPROVAL": return "Kiểm duyệt trước xuất bản";
    case "SYSTEM_FLAG": return "Cảnh báo hệ thống";
    default: return "Hồ sơ kiểm duyệt";
  }
}

function targetLabel(type: unknown) {
  switch (type) {
    case "CATALOG_ITEM": return "Nội dung / Sản phẩm";
    case "MERCHANT": return "Cửa hàng / Người bán";
    case "USER": return "Người dùng";
    case "NEED_REQUEST": return "Cần mua";
    case "REVIEW": return "Đánh giá";
    default: return "Đối tượng";
  }
}

function TargetIcon({ type }: { type: unknown }) {
  switch (type) {
    case "CATALOG_ITEM": return <Tag size={20} weight="duotone" />;
    case "MERCHANT": return <Storefront size={20} weight="duotone" />;
    case "USER": return <User size={20} weight="duotone" />;
    case "NEED_REQUEST": return <Flag size={20} weight="duotone" />;
    default: return <ShieldWarning size={20} weight="duotone" />;
  }
}

export function AdminModerationDetail({ caseId }: { caseId: string }) {
  const [detail, setDetail] = useState<CaseDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [conflict, setConflict] = useState<string>();

  const load = useCallback(async (isActive: () => boolean = () => true) => {
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/moderation-cases/${encodeURIComponent(caseId)}`);
      if (!response.ok) throw await readApiError(response);
      const payload = await response.json() as CaseDetail;
      if (!isActive()) return;
      setDetail(payload);
      setConflict(undefined);
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải hồ sơ kiểm duyệt");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, [caseId]);

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

  const actions = useMemo(() => availableActions(detail), [detail]);
  const status = presentModerationStatus(detail?.moderationCase.status);

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title={`Hồ sơ báo cáo ${detail ? `#${detail.moderationCase.id.slice(0, 8)}` : ""}`}
        subtitle="Quản trị vận hành · thao tác theo trạng thái case"
        action={detail ? <AdminStatusChip label={status.label} tone={status.tone} /> : undefined}
      />

      <Link
        className="admin-inline-link"
        href="/admin/reports"
        style={{ display: "inline-flex", alignItems: "center", gap: "6px", width: "fit-content", fontWeight: "600", color: "#047857" }}
      >
        <ArrowLeft size={16} weight="bold" />
        <span>Quay lại hàng đợi báo cáo</span>
      </Link>

      {conflict ? (
        <AdminSurface className="admin-warning-alert">
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <ShieldWarning size={18} weight="fill" style={{ color: "#b45309" }} />
            <strong>Hồ sơ đã thay đổi</strong>
          </div>
          <p>{conflict}</p>
          <button className="admin-inline-link admin-link-button" type="button" onClick={reload}>
            Tải lại hồ sơ
          </button>
        </AdminSurface>
      ) : null}

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải hồ sơ…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
          <button
            className="admin-button admin-button--secondary"
            onClick={reload}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
          >
            <ArrowsClockwise size={16} weight="bold" />
            <span>Thử lại</span>
          </button>
        </AdminSurface>
      ) : null}

      {!loading && !error && detail ? (
        <>
          <div className="admin-moderation-grid">
            <AdminSurface className="admin-entity-summary">
              <div className="admin-entity-summary__top" style={{ alignItems: "center", display: "flex", gap: "10px" }}>
                <div
                  style={{
                    width: "36px",
                    height: "36px",
                    borderRadius: "8px",
                    background: "rgba(225, 29, 72, 0.08)",
                    color: "#e11d48",
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "center",
                    flexShrink: 0,
                  }}
                >
                  <TargetIcon type={detail.moderationCase.targetType} />
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <strong style={{ display: "block" }}>{caseTypeLabel(detail.moderationCase.caseType)}</strong>
                  <small style={{ color: "var(--admin-text-tertiary, #64748b)" }}>{targetLabel(detail.moderationCase.targetType)}</small>
                </div>
                <AdminStatusChip label={status.label} tone={status.tone} />
              </div>

              <dl style={{ marginTop: "12px" }}>
                <div>
                  <dt>Đối tượng</dt>
                  <dd>{String(detail.moderationCase.targetType ?? "—")}</dd>
                </div>
                <div>
                  <dt>ID đối tượng</dt>
                  <dd style={{ fontFamily: "monospace", fontSize: "12px" }}>{detail.moderationCase.targetId ?? "—"}</dd>
                </div>
                <div>
                  <dt>Lý do</dt>
                  <dd>{detail.moderationCase.reasonCode ?? "—"}</dd>
                </div>
                <div>
                  <dt>Mở lúc</dt>
                  <dd>{formatAdminDate(detail.moderationCase.openedAt)}</dd>
                </div>
              </dl>

              <div style={{ display: "flex", alignItems: "center", gap: "6px", borderTop: "1px solid #f1f5f9", paddingTop: "8px", marginTop: "4px" }}>
                <ShieldCheck size={16} weight="fill" style={{ color: "#047857", flexShrink: 0 }} />
                <p className="admin-caption" style={{ margin: 0 }}>
                  Chỉ hiển thị dữ liệu case do Admin API trả về; không dựng thông tin người báo cáo hoặc độ ưu tiên nội bộ.
                </p>
              </div>
            </AdminSurface>

            <AdminSurface>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
                <h2 style={{ margin: 0 }}>Hành động theo trạng thái hồ sơ</h2>
              </div>
              <p>
                Danh sách bên dưới được giới hạn theo loại case, trạng thái hiện tại và endpoint đã có trong contract. Máy chủ vẫn là nguồn xác thực cuối cùng.
              </p>
              <div className="admin-moderation-actions">
                <ModerationActions
                  caseId={caseId}
                  actions={actions}
                  onComplete={load}
                  onConflict={message => setConflict(message)}
                />
              </div>
            </AdminSurface>
          </div>

          <AdminSurface className="admin-table-surface">
            <div style={{ padding: "14px 16px", borderBottom: "1px solid var(--admin-border-subtle, #f1f5f9)", display: "flex", alignItems: "center", gap: "8px" }}>
              <Scroll size={18} weight="duotone" style={{ color: "#047857" }} />
              <strong style={{ fontSize: "14px", color: "var(--admin-text, #0f172a)" }}>
                Lịch sử hành động kiểm duyệt
              </strong>
            </div>
            <div className="admin-table-scroll">
              <table className="admin-data-table">
                <thead>
                  <tr>
                    <th>Hành động</th>
                    <th>Quản trị viên</th>
                    <th>Lý do</th>
                    <th>Ghi chú</th>
                    <th>Thời gian</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.actions?.length ? (
                    detail.actions.map((action, index) => (
                      <tr key={action.id ?? index}>
                        <td>
                          <strong>{action.action ?? "—"}</strong>
                        </td>
                        <td style={{ fontFamily: "monospace", fontSize: "12px" }}>{action.actorAdminUserId ?? "—"}</td>
                        <td>{action.reasonCode ?? "—"}</td>
                        <td>{action.note ?? "—"}</td>
                        <td>{formatAdminDate(action.createdAt)}</td>
                      </tr>
                    ))
                  ) : (
                    <tr>
                      <td colSpan={5}>
                        <span className="admin-muted">Chưa có hành động trong hồ sơ.</span>
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
