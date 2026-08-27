"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate } from "./admin-ui";

type ModerationCase = Record<string, unknown> & { id: string; caseType?: string; caseStatus?: string; targetType?: string; targetId?: string; openedAt?: string; version?: number };

export function AdminReportsQueue() {
  const [items, setItems] = useState<ModerationCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  useEffect(() => {
    let active = true;
    requestBrowserApi("GET", "/api/v1/admin/moderation-cases?status=OPEN")
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json(); })
      .then(payload => { if (active) setItems((payload.content ?? payload.items ?? []) as ModerationCase[]); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải hàng đợi báo cáo"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);
  return <div className="admin-page-stack">
    <AdminPageHeader title="Báo cáo" subtitle="Hàng đợi kiểm duyệt · quyền hành động do máy chủ quyết định" />
    <AdminSurface className="admin-contract-note"><strong>Nguồn dữ liệu chuẩn</strong><p>Danh sách báo cáo dùng moderation case API. Mọi allowed action và version phải được đọc lại từ case detail trước khi thực thi.</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải hàng đợi…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <AdminSurface className="admin-table-surface"><div className="admin-table-scroll"><table className="admin-data-table"><thead><tr><th>Loại</th><th>Đối tượng</th><th>Trạng thái</th><th>Mở lúc</th><th>Tác vụ</th></tr></thead><tbody>{items.length ? items.map(item => <tr key={item.id}><td>{String(item.caseType ?? "—")}</td><td>{String(item.targetType ?? "—")} {item.targetId ? `· ${item.targetId}` : ""}</td><td><AdminStatusChip label={String(item.caseStatus ?? "Trạng thái đã được cập nhật")} tone="neutral" /></td><td>{formatAdminDate(item.openedAt)}</td><td><Link className="admin-row-action" href={`/admin/moderation/${item.id}`}>Xem hồ sơ</Link></td></tr>) : <tr><td colSpan={5}><span className="admin-muted">Không có case đang mở.</span></td></tr>}</tbody></table></div></AdminSurface> : null}
  </div>;
}
