"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { ModerationActions, type ModerationActionKind } from "./moderation-actions";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentModerationStatus } from "./admin-ui";

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

export function AdminModerationDetail({ caseId }: { caseId: string }) {
  const [detail, setDetail] = useState<CaseDetail>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [conflict, setConflict] = useState<string>();

  const load = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/moderation-cases/${encodeURIComponent(caseId)}`);
      if (!response.ok) throw await readApiError(response);
      setDetail(await response.json() as CaseDetail);
      setConflict(undefined);
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể tải hồ sơ kiểm duyệt"); }
    finally { setLoading(false); }
  }, [caseId]);

  useEffect(() => { void load(); }, [load]);
  const actions = useMemo(() => availableActions(detail), [detail]);
  const status = presentModerationStatus(detail?.moderationCase.status);

  return <div className="admin-page-stack">
    <AdminPageHeader title={`Hồ sơ báo cáo ${detail ? `#${detail.moderationCase.id.slice(0, 8)}` : ""}`} subtitle="Quản trị vận hành · thao tác theo trạng thái case" action={detail ? <AdminStatusChip label={status.label} tone={status.tone} /> : undefined} />
    <Link className="admin-inline-link" href="/admin/reports">← Quay lại hàng đợi báo cáo</Link>
    {conflict ? <AdminSurface className="admin-warning-alert"><strong>Hồ sơ đã thay đổi</strong><p>{conflict}</p><button className="admin-inline-link admin-link-button" type="button" onClick={() => void load()}>Tải lại hồ sơ</button></AdminSurface> : null}
    {loading ? <AdminSurface><p className="admin-muted">Đang tải hồ sơ…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Thử lại</button></AdminSurface> : null}
    {!loading && !error && detail ? <>
      <div className="admin-moderation-grid">
        <AdminSurface className="admin-entity-summary"><div className="admin-entity-summary__top"><strong>{caseTypeLabel(detail.moderationCase.caseType)}</strong><AdminStatusChip label={status.label} tone={status.tone} /></div><dl><div><dt>Đối tượng</dt><dd>{String(detail.moderationCase.targetType ?? "—")}</dd></div><div><dt>ID đối tượng</dt><dd>{detail.moderationCase.targetId ?? "—"}</dd></div><div><dt>Lý do</dt><dd>{detail.moderationCase.reasonCode ?? "—"}</dd></div><div><dt>Mở lúc</dt><dd>{formatAdminDate(detail.moderationCase.openedAt)}</dd></div></dl><p className="admin-caption">Chỉ hiển thị dữ liệu case do Admin API trả về; không dựng thông tin người báo cáo hoặc độ ưu tiên nội bộ.</p></AdminSurface>
        <AdminSurface><h2>Hành động theo trạng thái hồ sơ</h2><p>Danh sách bên dưới được giới hạn theo loại case, trạng thái hiện tại và endpoint đã có trong contract. Máy chủ vẫn là nguồn xác thực cuối cùng.</p><div className="admin-moderation-actions"><ModerationActions caseId={caseId} actions={actions} onComplete={load} onConflict={message => setConflict(message)} /></div></AdminSurface>
      </div>
      <AdminSurface className="admin-table-surface"><div className="admin-table-scroll"><table className="admin-data-table"><thead><tr><th>Hành động</th><th>Quản trị viên</th><th>Lý do</th><th>Ghi chú</th><th>Thời gian</th></tr></thead><tbody>{detail.actions?.length ? detail.actions.map((action, index) => <tr key={action.id ?? index}><td>{action.action ?? "—"}</td><td>{action.actorAdminUserId ?? "—"}</td><td>{action.reasonCode ?? "—"}</td><td>{action.note ?? "—"}</td><td>{formatAdminDate(action.createdAt)}</td></tr>) : <tr><td colSpan={5}><span className="admin-muted">Chưa có hành động trong hồ sơ.</span></td></tr>}</tbody></table></div></AdminSurface>
    </> : null}
  </div>;
}
