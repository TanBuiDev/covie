"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import {
  ArrowRight,
  ArrowsClockwise,
  ChatCenteredText,
  Clock,
  Flag,
  ShieldCheck,
  ShieldWarning,
  Storefront,
  Tag,
  User,
} from "@phosphor-icons/react";
import {
  AdminPageHeader,
  AdminStatusChip,
  AdminSurface,
  formatAdminDate,
  presentModerationStatus,
} from "./admin-ui";

type ModerationCase = { id: string; caseType?: string; status?: string; targetType?: string; targetId?: string; reasonCode?: string; openedAt?: string; resolvedAt?: string };
type PagePayload = { content?: ModerationCase[]; totalPages?: number; totalElements?: number; number?: number; first?: boolean; last?: boolean };
const PAGE_SIZE = 50;

function targetLabel(type: unknown) {
  switch (type) {
    case "CATALOG_ITEM": return "Nội dung / Sản phẩm";
    case "MERCHANT": return "Cửa hàng / Người bán";
    case "USER": return "Người dùng";
    case "NEED_REQUEST": return "Cần mua";
    case "REVIEW": return "Đánh giá";
    case "SOCIAL_COMMENT": return "Bình luận sản phẩm";
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

function TargetIcon({ type }: { type: unknown }) {
  switch (type) {
    case "CATALOG_ITEM":
      return <Tag size={18} weight="duotone" />;
    case "MERCHANT":
      return <Storefront size={18} weight="duotone" />;
    case "USER":
      return <User size={18} weight="duotone" />;
    case "NEED_REQUEST":
      return <Flag size={18} weight="duotone" />;
    case "REVIEW":
    case "SOCIAL_COMMENT":
      return <ChatCenteredText size={18} weight="duotone" />;
    default:
      return <ShieldWarning size={18} weight="duotone" />;
  }
}

export function AdminReportsQueue() {
  const [items, setItems] = useState<ModerationCase[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  const [page, setPage] = useState(0);
  const [pageInfo, setPageInfo] = useState({ totalPages: 1, totalElements: 0, first: true, last: true });

  const load = useCallback(async (isActive: () => boolean = () => true) => {
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/moderation-cases?status=OPEN&page=${page}&size=${PAGE_SIZE}&sort=OPENED_DESC`);
      if (!response.ok) throw await readApiError(response);
      const payload = await response.json() as PagePayload;
      const content = payload.content ?? [];
      if (!isActive()) return;
      setItems(content);
      setPageInfo({
        totalPages: Math.max(1, payload.totalPages ?? 1),
        totalElements: payload.totalElements ?? content.length,
        first: payload.first ?? page === 0,
        last: payload.last ?? true,
      });
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải hàng đợi báo cáo");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, [page]);

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

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Hàng đợi báo cáo"
        subtitle="CoVie Admin · Tiếp nhận và điều phối xử lý báo cáo vi phạm"
      />

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải hàng đợi…</p>
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

      {!loading && !error ? (
        <>
          <div className="admin-report-grid">
            {items.length ? (
              items.map(item => {
                const status = presentModerationStatus(item.status);
                return (
                  <article className="admin-report-card" key={item.id}>
                    <div className="admin-report-card__header">
                      <div style={{ display: "flex", alignItems: "center", gap: "10px", minWidth: 0 }}>
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
                          <TargetIcon type={item.targetType} />
                        </div>
                        <div className="admin-report-card__identity">
                          <strong>Báo cáo #{item.id.slice(0, 8)}</strong>
                          <small>{targetLabel(item.targetType)} · {item.targetId ?? "—"}</small>
                        </div>
                      </div>
                      <AdminStatusChip label={status.label} tone={status.tone} />
                    </div>

                    <div className="admin-report-card__body">
                      <span className="admin-report-card__reason">
                        {item.reasonCode ?? caseTypeLabel(item.caseType)}
                      </span>
                      <p className="admin-report-card__note">
                        Chỉ hiển thị dữ liệu case cần thiết; mở hồ sơ để xem lịch sử hành động và ngữ cảnh contract.
                      </p>
                    </div>

                    <div className="admin-report-card__footer">
                      <small className="admin-report-card__date" style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}>
                        <Clock size={12} weight="bold" />
                        <span>{formatAdminDate(item.openedAt)}</span>
                      </small>
                      <Link
                        className="admin-row-action"
                        href={`/admin/moderation/${item.id}`}
                        aria-label={`Tác vụ cho báo cáo ${item.id}`}
                        style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                      >
                        <span>Xem hồ sơ</span>
                        <ArrowRight size={14} weight="bold" />
                      </Link>
                    </div>
                  </article>
                );
              })
            ) : (
              <AdminSurface>
                <p className="admin-muted">Không có hồ sơ đang mở.</p>
              </AdminSurface>
            )}
          </div>

          {pageInfo.totalPages > 1 ? (
            <div className="admin-pagination">
              <span>Trang {page + 1} / {pageInfo.totalPages} · {pageInfo.totalElements} hồ sơ</span>
              <div>
                <button
                  className="admin-button admin-button--secondary"
                  disabled={pageInfo.first}
                  onClick={() => { setLoading(true); setError(undefined); setPage(value => Math.max(0, value - 1)); }}
                >
                  Trước
                </button>
                <button
                  className="admin-button admin-button--secondary"
                  disabled={pageInfo.last}
                  onClick={() => { setLoading(true); setError(undefined); setPage(value => value + 1); }}
                >
                  Sau
                </button>
              </div>
            </div>
          ) : null}

          <div className="admin-report-safety">
            <AdminSurface>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                <ShieldCheck size={18} weight="duotone" style={{ color: "#047857" }} />
                <h2 style={{ margin: 0 }}>Tóm tắt đối tượng an toàn</h2>
              </div>
              <p>
                Chỉ hiển thị thông tin cần thiết để xem xét báo cáo. Không có độ ưu tiên nội bộ, người phụ trách, cam kết xử lý hay trường kết quả do người báo cáo điều khiển.
              </p>
            </AdminSurface>

            <AdminSurface>
              <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
                <ShieldWarning size={18} weight="duotone" style={{ color: "#ea580c" }} />
                <h2 style={{ margin: 0 }}>Hành động theo trạng thái hồ sơ</h2>
              </div>
              <p>
                Hành động kiểm duyệt chỉ xuất hiện trong hồ sơ chi tiết khi trạng thái và loại case phù hợp với API. Không có chức năng phân xử tranh chấp giao dịch.
              </p>
            </AdminSurface>
          </div>

          <AdminSurface className="admin-warning-alert">
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <ShieldWarning size={18} weight="fill" style={{ color: "#b45309" }} />
              <strong>Hồ sơ có thể đã thay đổi</strong>
            </div>
            <p>
              Trước khi thực hiện hành động, màn hình chi tiết tải lại case. Nếu máy chủ từ chối do trạng thái đã đổi, giao diện yêu cầu tải lại thay vì gửi lặp.
            </p>
            <button
              className="admin-inline-link admin-link-button"
              type="button"
              onClick={reload}
              style={{ display: "inline-flex", alignItems: "center", gap: "6px", width: "fit-content", marginTop: "4px" }}
            >
              <ArrowsClockwise size={14} weight="bold" />
              <span>Tải lại hàng đợi</span>
            </button>
          </AdminSurface>
        </>
      ) : null}
    </div>
  );
}
