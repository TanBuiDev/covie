"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentModerationStatus } from "./admin-ui";

type ModerationCase = { id: string; caseType?: string; status?: string; targetType?: string; targetId?: string; reasonCode?: string; openedAt?: string; resolvedAt?: string };
type PagePayload = { content?: ModerationCase[]; totalPages?: number; totalElements?: number; number?: number; first?: boolean; last?: boolean };
const PAGE_SIZE = 50;

function targetLabel(type: unknown) {
  switch (type) {
    case "CATALOG_ITEM": return "Nội dung";
    case "MERCHANT": return "Cửa hàng / Người bán";
    case "USER": return "Người dùng";
    case "NEED_REQUEST": return "Cần mua";
    case "REVIEW": return "Đánh giá";
    default: return "Đối tượng";
  }
}

function caseTypeLabel(type: unknown) {
  switch (type) {
    case "USER_REPORT": return "Báo cáo người dùng";
    case "PRE_APPROVAL": return "Kiểm duyệt trước";
    case "SYSTEM_FLAG": return "Cảnh báo hệ thống";
    default: return "Hồ sơ kiểm duyệt";
  }
}

export function AdminReportsQueue() {
  const [items, setItems] = useState<ModerationCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState({ totalPages: 1, totalElements: 0, first: true, last: true });
  const loadRequest = useRef(0);

  const load = useCallback(async () => {
    const requestId = ++loadRequest.current;
    setLoading(true); setError(undefined);
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/moderation-cases?status=OPEN&page=${page}&size=${PAGE_SIZE}&sort=OPENED_DESC`);
      if (!response.ok) throw await readApiError(response);
      const payload = await response.json() as PagePayload;
      if (requestId !== loadRequest.current) return;
      const content = payload.content ?? [];
      setItems(content);
      setPageInfo({ totalPages: Math.max(1, payload.totalPages ?? 1), totalElements: payload.totalElements ?? content.length, first: payload.first ?? page === 0, last: payload.last ?? true });
    } catch (cause) { if (requestId === loadRequest.current) setError(cause instanceof Error ? cause.message : "Không thể tải hàng đợi báo cáo"); }
    finally { if (requestId === loadRequest.current) setLoading(false); }
  }, [page]);

  useEffect(() => {
    let active = true;
    queueMicrotask(() => { if (active) void load(); });
    return () => { active = false; loadRequest.current += 1; };
  }, [load]);

  return <div className="admin-page-stack">
    <AdminPageHeader title="Hàng đợi báo cáo" subtitle="Quản trị vận hành" />
    {loading ? <AdminSurface><p className="admin-muted">Đang tải hàng đợi…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Thử lại</button></AdminSurface> : null}
    {!loading && !error ? <>
      <div className="admin-report-grid">{items.length ? items.map(item => { const status = presentModerationStatus(item.status); return <article className="admin-report-card" key={item.id}><div><strong>Báo cáo #{item.id.slice(0, 8)}</strong><small>{targetLabel(item.targetType)} · {item.targetId ?? "—"}</small></div><span>{item.reasonCode ?? caseTypeLabel(item.caseType)}</span><AdminStatusChip label={status.label} tone={status.tone} /><small>{formatAdminDate(item.openedAt)}</small><p>Chỉ hiển thị dữ liệu case cần thiết; mở hồ sơ để xem lịch sử hành động và ngữ cảnh contract.</p><Link className="admin-row-action" href={`/admin/moderation/${item.id}`} aria-label={`Tác vụ cho báo cáo ${item.id}`}>Xem hồ sơ</Link></article>; }) : <AdminSurface><p className="admin-muted">Không có hồ sơ đang mở.</p></AdminSurface>}</div>
      {pageInfo.totalPages > 1 ? <div className="admin-pagination"><span>Trang {page + 1} / {pageInfo.totalPages} · {pageInfo.totalElements} hồ sơ</span><div><button className="admin-button admin-button--secondary" disabled={pageInfo.first} onClick={() => setPage(value => Math.max(0, value - 1))}>Trước</button><button className="admin-button admin-button--secondary" disabled={pageInfo.last} onClick={() => setPage(value => value + 1)}>Sau</button></div></div> : null}
      <div className="admin-report-safety"><AdminSurface><h2>Tóm tắt đối tượng an toàn</h2><p>Chỉ hiển thị thông tin cần thiết để xem xét báo cáo. Không có độ ưu tiên nội bộ, người phụ trách, cam kết xử lý hay trường kết quả do người báo cáo điều khiển.</p></AdminSurface><AdminSurface><h2>Hành động theo trạng thái hồ sơ</h2><p>Hành động kiểm duyệt chỉ xuất hiện trong hồ sơ chi tiết khi trạng thái và loại case phù hợp với API. Không có chức năng phân xử tranh chấp giao dịch.</p></AdminSurface></div>
      <AdminSurface className="admin-warning-alert"><strong>Hồ sơ có thể đã thay đổi</strong><p>Trước khi thực hiện hành động, màn hình chi tiết tải lại case. Nếu máy chủ từ chối do trạng thái đã đổi, giao diện yêu cầu tải lại thay vì gửi lặp.</p><button className="admin-inline-link admin-link-button" type="button" onClick={() => void load()}>Tải lại hàng đợi</button></AdminSurface>
    </> : null}
  </div>;
}
