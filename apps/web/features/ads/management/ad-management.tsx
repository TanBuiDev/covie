"use client";

import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import {
  PlusCircle,
  MegaphoneSimple,
  CheckCircle,
  Clock,
  WarningCircle,
  ArrowsClockwise,
  PencilSimple,
  Trash,
  PaperPlaneTilt,
  XCircle,
  ChartLineUp,
  CalendarBlank,
  PlayCircle,
  ImageSquare,
  Sparkle,
} from "@phosphor-icons/react";
import { CovieApiError } from "@/lib/api/api-error";
import {
  listAdCampaigns,
  createAdCampaign,
  updateAdCampaign,
  deleteAdCampaign,
  performAdCampaignAction,
  type AdCampaign,
  type AdCampaignInput,
  type AdScope,
} from "../ad-api";
import { AdCampaignForm } from "./ad-campaign-form";
import { AdAnalytics } from "./ad-analytics";
import { StatusChip } from "@/components/ui/status-chip";
import styles from "./management.module.css";

const statuses: Record<string, string> = {
  DRAFT: "Bản nháp",
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Cần chỉnh sửa",
};

const toneMap: Record<string, "neutral" | "warning" | "success" | "error"> = {
  DRAFT: "neutral",
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "error",
};

const placements: Record<string, string> = {
  SIDEBAR_LEFT: "Banner trái",
  HOME_HERO: "Banner trang chủ",
  IN_FEED: "Trong danh sách",
  ITEM_DETAIL: "Chi tiết sản phẩm",
};

type ManagedCampaign = AdCampaign &
  Required<Pick<AdCampaign, "id" | "version" | "status" | "placement" | "startsAt" | "endsAt" | "allowedActions">>;

function validCampaign(item: AdCampaign): item is ManagedCampaign {
  return (
    typeof item.id === "string" &&
    !!item.id &&
    typeof item.version === "number" &&
    Number.isInteger(item.version) &&
    item.version >= 0 &&
    typeof item.status === "string" &&
    typeof item.placement === "string" &&
    typeof item.startsAt === "string" &&
    Number.isFinite(Date.parse(item.startsAt)) &&
    typeof item.endsAt === "string" &&
    Number.isFinite(Date.parse(item.endsAt)) &&
    Array.isArray(item.allowedActions)
  );
}

export function AdManagement({ scope }: { scope: AdScope }) {
  return <AdManagementContent key={scope.kind === "admin" ? "admin" : `seller:${scope.merchantId}`} scope={scope} />;
}

function AdManagementContent({ scope }: { scope: AdScope }) {
  const [items, setItems] = useState<ManagedCampaign[]>([]);
  const [filterStatus, setFilterStatus] = useState<string>("ALL");
  const [loading, setLoading] = useState(true);
  const [moreLoading, setMoreLoading] = useState(false);
  const [moreError, setMoreError] = useState("");
  const [hasMore, setHasMore] = useState(false);
  const [error, setError] = useState("");
  const [auth, setAuth] = useState(false);
  const [stale, setStale] = useState(false);
  const [pending, setPending] = useState(false);
  const [editor, setEditor] = useState<ManagedCampaign | "new" | null>(null);
  const [confirmation, setConfirmation] = useState<{ campaign: ManagedCampaign; action: "DELETE" | "REJECT" } | null>(null);
  const [reason, setReason] = useState("");
  const [stats, setStats] = useState<AdCampaign | null>(null);
  const lock = useRef(false);
  const mounted = useRef(true);
  const readGeneration = useRef(0);
  const moreLock = useRef(false);
  const offset = useRef(0);
  const expired = useCallback(() => setAuth(true), []);
  const merchantId = scope.kind === "seller" ? scope.merchantId : undefined;
  const path = scope.kind === "admin" ? "/admin/ads" : `/seller/${merchantId}/ads`;

  const handleError = useCallback((cause: unknown, message: string) => {
    if (!mounted.current) return;
    if (cause instanceof CovieApiError && cause.status === 401) setAuth(true);
    else if (cause instanceof CovieApiError && cause.status === 403)
      setError("Bạn không có quyền thực hiện thao tác này.");
    else if (cause instanceof CovieApiError && [409, 412].includes(cause.status)) {
      setStale(true);
      setError("Chiến dịch đã thay đổi. Tải lại danh sách trước khi tiếp tục.");
    } else setError(message);
  }, []);

  const load = useCallback(async () => {
    const revision = ++readGeneration.current;
    try {
      const rows = await listAdCampaigns(merchantId ? { kind: "seller", merchantId } : { kind: "admin" });
      if (mounted.current && revision === readGeneration.current) {
        const valid = rows.filter(validCampaign);
        setItems(valid);
        offset.current = rows.length;
        setHasMore(rows.length === 100);
        setMoreError("");
        setError(valid.length === rows.length ? "" : "Một số dữ liệu chiến dịch chưa hợp lệ. Tải lại trước khi thao tác.");
        setStale(valid.length !== rows.length);
      }
    } catch (cause) {
      if (mounted.current && revision === readGeneration.current) {
        setStale(true);
        handleError(cause, "Không thể tải danh sách. Bạn có thể thử tải lại.");
      }
    } finally {
      if (mounted.current && revision === readGeneration.current) setLoading(false);
    }
  }, [merchantId, handleError]);

  useEffect(() => {
    mounted.current = true;
    void Promise.resolve().then(() => {
      if (mounted.current) return load();
    });
    return () => {
      mounted.current = false;
    };
  }, [load]);

  async function loadMore() {
    if (moreLock.current || lock.current || !hasMore || stale || auth) return;
    const revision = readGeneration.current;
    moreLock.current = true;
    setMoreLoading(true);
    setMoreError("");
    try {
      const rows = await listAdCampaigns(scope, offset.current);
      if (!mounted.current || revision !== readGeneration.current) return;
      const valid = rows.filter(validCampaign);
      if (valid.length !== rows.length) {
        setStale(true);
        setError("Một số dữ liệu chiến dịch chưa hợp lệ. Tải lại trước khi thao tác.");
        return;
      }
      setItems(current => {
        const byId = new Map(current.map(item => [item.id, item]));
        for (const item of valid) byId.set(item.id, item);
        return [...byId.values()];
      });
      offset.current += rows.length;
      setHasMore(rows.length === 100);
    } catch (cause) {
      if (mounted.current && revision === readGeneration.current) {
        if (cause instanceof CovieApiError && cause.status === 401) setAuth(true);
        else setMoreError("Không thể tải thêm chiến dịch. Danh sách đã tải được giữ lại.");
      }
    } finally {
      moreLock.current = false;
      if (mounted.current) setMoreLoading(false);
    }
  }

  async function command(write: () => Promise<unknown>) {
    if (lock.current || moreLock.current || auth || stale) return;
    lock.current = true;
    setPending(true);
    setError("");
    try {
      await write();
      if (mounted.current) {
        setEditor(null);
        setConfirmation(null);
      }
      await load();
    } catch (cause) {
      handleError(cause, "Thao tác chưa được xác nhận. Tải lại danh sách để kiểm tra trước khi thử lại.");
      setStale(true);
    } finally {
      lock.current = false;
      if (mounted.current) setPending(false);
    }
  }

  function save(body: AdCampaignInput) {
    return command(() =>
      editor && editor !== "new"
        ? updateAdCampaign(scope, editor.id, editor.version, body)
        : createAdCampaign(scope, body),
    );
  }

  if (auth)
    return (
      <section className={styles.panel}>
        <h1>Quảng cáo</h1>
        <p>Phiên đăng nhập đã hết hạn.</p>
        <Link href={`/login?returnTo=${encodeURIComponent(path)}`}>Đăng nhập lại</Link>
      </section>
    );

  const pendingCount = items.filter(i => i.status === "PENDING").length;
  const approvedCount = items.filter(i => i.status === "APPROVED").length;
  const rejectedCount = items.filter(i => i.status === "REJECTED").length;
  const draftCount = items.filter(i => i.status === "DRAFT").length;
  const activeCount = items.filter(i => i.status === "APPROVED" && i.enabled).length;

  const visibleItems = filterStatus === "ALL" ? items : items.filter(i => i.status === filterStatus);

  return (
    <section className={styles.root}>
      <header className={`surface-header ${styles.surfaceHeader}`}>
        <div className={styles.headerText}>
          <p className="eyebrow">{scope.kind === "admin" ? "Quản trị" : "Trung tâm bán hàng"}</p>
          <h1 className={styles.title}>Quảng cáo</h1>
          <p className={styles.subtitle}>Quản lý nội dung, lịch hiển thị và hiệu quả chiến dịch.</p>
        </div>
        <button
          className={scope.kind === "admin" ? "admin-button admin-button--primary" : "button-primary"}
          disabled={pending || stale || loading}
          onClick={() => setEditor("new")}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <PlusCircle size={18} weight="bold" aria-hidden="true" />
          <span>Tạo chiến dịch</span>
        </button>
      </header>

      {/* Summary Stats Overview */}
      {!loading && !error && items.length > 0 ? (
        <div className={styles.statsGrid}>
          <div className={styles.statTile}>
            <div className={styles.statTileContent}>
              <span className={styles.statTileLabel}>Tổng chiến dịch</span>
              <span className={styles.statTileValue}>{items.length}</span>
            </div>
            <div className={styles.statTileIcon}>
              <MegaphoneSimple size={20} weight="bold" color="#047857" aria-hidden="true" />
            </div>
          </div>
          <div className={styles.statTile}>
            <div className={styles.statTileContent}>
              <span className={styles.statTileLabel}>
                {scope.kind === "admin" ? "Chờ duyệt" : "Đang phát sóng"}
              </span>
              <span
                className={styles.statTileValue}
                style={{
                  color: scope.kind === "admin" && pendingCount > 0 ? "#b45309" : "#047857",
                }}
              >
                {scope.kind === "admin" ? pendingCount : activeCount}
              </span>
            </div>
            <div
              className={styles.statTileIcon}
              style={{
                background: scope.kind === "admin" && pendingCount > 0 ? "#fffbeb" : "#ecfdf5",
                color: scope.kind === "admin" && pendingCount > 0 ? "#b45309" : "#047857",
              }}
            >
              {scope.kind === "admin" ? (
                <Clock size={20} weight="bold" aria-hidden="true" />
              ) : (
                <CheckCircle size={20} weight="bold" aria-hidden="true" />
              )}
            </div>
          </div>
          <div className={styles.statTile}>
            <div className={styles.statTileContent}>
              <span className={styles.statTileLabel}>{scope.kind === "admin" ? "Đã duyệt" : "Chờ duyệt"}</span>
              <span
                className={styles.statTileValue}
                style={{ color: scope.kind === "admin" ? "#047857" : pendingCount > 0 ? "#b45309" : "#64748b" }}
              >
                {scope.kind === "admin" ? approvedCount : pendingCount}
              </span>
            </div>
            <div
              className={styles.statTileIcon}
              style={{
                background: scope.kind === "admin" ? "#ecfdf5" : "#fffbeb",
                color: scope.kind === "admin" ? "#047857" : "#b45309",
              }}
            >
              {scope.kind === "admin" ? (
                <CheckCircle size={20} weight="bold" aria-hidden="true" />
              ) : (
                <Clock size={20} weight="bold" aria-hidden="true" />
              )}
            </div>
          </div>
          <div className={styles.statTile}>
            <div className={styles.statTileContent}>
              <span className={styles.statTileLabel}>Cần chỉnh sửa</span>
              <span className={styles.statTileValue} style={{ color: rejectedCount > 0 ? "#dc2626" : "#64748b" }}>
                {rejectedCount}
              </span>
            </div>
            <div
              className={styles.statTileIcon}
              style={{
                background: rejectedCount > 0 ? "#fef2f2" : "#f8fafc",
                color: rejectedCount > 0 ? "#dc2626" : "#64748b",
              }}
            >
              <WarningCircle size={20} weight="bold" aria-hidden="true" />
            </div>
          </div>
        </div>
      ) : null}

      {/* Quick Filter Tabs */}
      {!loading && !error && items.length > 0 ? (
        <div className={styles.filterBar}>
          <div className={styles.filterTabs}>
            <button
              type="button"
              className={`${styles.filterTab} ${filterStatus === "ALL" ? styles.filterTabActive : ""}`}
              onClick={() => setFilterStatus("ALL")}
            >
              <span>Tất cả</span>
              <span className={styles.filterTabCount}>{items.length}</span>
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${filterStatus === "APPROVED" ? styles.filterTabActive : ""}`}
              onClick={() => setFilterStatus("APPROVED")}
            >
              <span>Đã duyệt</span>
              <span className={styles.filterTabCount}>{approvedCount}</span>
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${filterStatus === "PENDING" ? styles.filterTabActive : ""}`}
              onClick={() => setFilterStatus("PENDING")}
            >
              <span>Chờ duyệt</span>
              <span className={styles.filterTabCount}>{pendingCount}</span>
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${filterStatus === "REJECTED" ? styles.filterTabActive : ""}`}
              onClick={() => setFilterStatus("REJECTED")}
            >
              <span>Cần chỉnh sửa</span>
              <span className={styles.filterTabCount}>{rejectedCount}</span>
            </button>
            <button
              type="button"
              className={`${styles.filterTab} ${filterStatus === "DRAFT" ? styles.filterTabActive : ""}`}
              onClick={() => setFilterStatus("DRAFT")}
            >
              <span>Bản nháp</span>
              <span className={styles.filterTabCount}>{draftCount}</span>
            </button>
          </div>
        </div>
      ) : null}

      {error && (
        <div role="alert" className={styles.notice}>
          <span>{error}</span>{" "}
          <button
            disabled={pending}
            onClick={() => void load()}
            style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
          >
            <ArrowsClockwise size={16} aria-hidden="true" />
            <span>Tải lại danh sách</span>
          </button>
        </div>
      )}

      {loading ? (
        <p role="status" style={{ color: "#047857", fontWeight: 600 }}>
          Đang tải chiến dịch…
        </p>
      ) : (
        <>
          {!items.length && !error && (
            <div className={styles.panel} style={{ textAlign: "center", padding: "40px 20px" }}>
              <MegaphoneSimple size={48} weight="duotone" color="#94a3b8" aria-hidden="true" style={{ margin: "0 auto 12px" }} />
              <h2>Chưa có chiến dịch quảng cáo.</h2>
              <p>Tạo nội dung đầu tiên và chọn lịch hiển thị phù hợp.</p>
            </div>
          )}

          {items.length > 0 && !visibleItems.length && (
            <div className={styles.panel} style={{ textAlign: "center", padding: "30px 20px" }}>
              <p>Không có chiến dịch nào ở trạng thái này.</p>
              <button
                type="button"
                className={styles.btnPrimaryAction}
                onClick={() => setFilterStatus("ALL")}
                style={{ marginTop: "12px", display: "inline-flex", alignItems: "center", gap: "6px" }}
              >
                <span>Xem tất cả</span>
              </button>
            </div>
          )}

          {hasMore && (
            <p role="status" style={{ fontSize: "13px", color: "var(--covie-muted)" }}>
              Đang hiển thị {items.length} chiến dịch. Tải thêm để xem các chiến dịch trước.
            </p>
          )}

          <div className={styles.list}>
            {visibleItems.map(item => (
              <article key={item.id} className={styles.campaignCard} aria-label={item.title}>
                <div className={styles.cardHeader}>
                  <div className={styles.cardHeaderInfo}>
                    <div className={styles.mediaThumbnail}>
                      {item.mediaUrl ? (
                        <img src={item.mediaUrl} alt="" aria-hidden="true" />
                      ) : item.creativeType === "VIDEO" ? (
                        <PlayCircle size={28} color="#0284c7" weight="duotone" aria-hidden="true" />
                      ) : item.creativeType === "CSS" ? (
                        <Sparkle size={26} color="#047857" weight="duotone" aria-hidden="true" />
                      ) : (
                        <ImageSquare size={26} color="#64748b" weight="duotone" aria-hidden="true" />
                      )}
                    </div>
                    <div className={styles.cardTitleSection}>
                      <h2>{item.title}</h2>
                      <div className={styles.cardMetaPills}>
                        <span className={styles.metaPill}>{placements[item.placement] ?? item.placement}</span>
                        <span className={styles.metaPill}>
                          {item.device === "ALL" ? "Mọi thiết bị" : item.device === "MOBILE" ? "Di động" : "Máy tính"}
                        </span>
                        <span className={styles.metaPill} style={{ color: item.enabled ? "#047857" : "#64748b" }}>
                          {item.enabled ? "Đã bật" : "Đã tắt"}
                        </span>
                        <span className={styles.metaPill}>Ưu tiên {item.priority}</span>
                      </div>
                    </div>
                  </div>
                  <StatusChip
                    label={statuses[item.status] ?? "Trạng thái đã được cập nhật"}
                    tone={toneMap[item.status] ?? "neutral"}
                  />
                </div>

                <p className={styles.scheduleLine}>
                  <CalendarBlank size={15} color="#047857" aria-hidden="true" />
                  <span>
                    {new Date(item.startsAt).toLocaleString("vi-VN")} – {new Date(item.endsAt).toLocaleString("vi-VN")}
                  </span>
                </p>

                {item.reviewReason && (
                  <div className={styles.reviewAlert}>
                    <WarningCircle size={20} weight="bold" color="#991b1b" aria-hidden="true" style={{ flexShrink: 0 }} />
                    <div>
                      <strong>Lý do kiểm duyệt</strong>
                      <p style={{ color: "#991b1b", margin: 0 }}>Lý do kiểm duyệt: {item.reviewReason}</p>
                    </div>
                  </div>
                )}

                <div className={styles.actions}>
                  {item.allowedActions.includes("UPDATE") && (
                    <button
                      disabled={pending || stale}
                      onClick={() => setEditor(item)}
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <PencilSimple size={15} aria-hidden="true" />
                      <span>Chỉnh sửa</span>
                    </button>
                  )}
                  {item.allowedActions.includes("DELETE") && (
                    <button
                      className={styles.btnDangerAction}
                      disabled={pending || stale}
                      onClick={() => setConfirmation({ campaign: item, action: "DELETE" })}
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <Trash size={15} aria-hidden="true" />
                      <span>Xóa</span>
                    </button>
                  )}
                  {item.allowedActions.includes("SUBMIT") && (
                    <button
                      className={styles.btnPrimaryAction}
                      disabled={pending || stale}
                      onClick={() =>
                        void command(() => performAdCampaignAction(scope, item.id, item.version, "SUBMIT", undefined))
                      }
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <PaperPlaneTilt size={15} weight="bold" aria-hidden="true" />
                      <span>Gửi duyệt</span>
                    </button>
                  )}
                  {item.allowedActions.includes("APPROVE") && (
                    <button
                      className={styles.btnPrimaryAction}
                      disabled={pending || stale}
                      onClick={() =>
                        void command(() => performAdCampaignAction(scope, item.id, item.version, "APPROVE", undefined))
                      }
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <CheckCircle size={15} weight="bold" aria-hidden="true" />
                      <span>Duyệt</span>
                    </button>
                  )}
                  {item.allowedActions.includes("REJECT") && (
                    <button
                      className={styles.btnDangerAction}
                      disabled={pending || stale}
                      onClick={() => {
                        setReason("");
                        setConfirmation({ campaign: item, action: "REJECT" });
                      }}
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <XCircle size={15} weight="bold" aria-hidden="true" />
                      <span>Từ chối</span>
                    </button>
                  )}
                  {item.allowedActions.includes("VIEW_STATS") && (
                    <button
                      disabled={pending || stale}
                      onClick={() => setStats(item)}
                      style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
                    >
                      <ChartLineUp size={15} aria-hidden="true" />
                      <span>Thống kê</span>
                    </button>
                  )}
                </div>
              </article>
            ))}
          </div>

          {moreError && <p role="alert">{moreError}</p>}
          {hasMore && (
            <div className={styles.actions} style={{ marginTop: "16px" }}>
              <button disabled={pending || stale || moreLoading} onClick={() => void loadMore()}>
                {moreLoading ? "Đang tải thêm…" : moreError ? "Thử tải thêm" : "Xem thêm chiến dịch"}
              </button>
            </div>
          )}
        </>
      )}

      {editor && (
        <AdCampaignForm
          key={editor === "new" ? "new" : `${editor.id}:${editor.version}`}
          campaign={editor === "new" ? undefined : editor}
          busy={pending || stale}
          onSave={save}
          onCancel={() => setEditor(null)}
          onAuth={expired}
        />
      )}

      {confirmation && (
        <section className={styles.panel} aria-label="Xác nhận thao tác">
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <WarningCircle size={22} weight="bold" color="#dc2626" aria-hidden="true" />
            <h2 style={{ margin: 0 }}>
              {confirmation.action === "DELETE" ? "Xóa chiến dịch?" : "Từ chối chiến dịch"}
            </h2>
          </div>
          <p style={{ marginTop: "6px", fontWeight: 600, color: "#0f172a" }}>{confirmation.campaign.title}</p>
          {confirmation.action === "REJECT" && (
            <label style={{ marginTop: "12px" }}>
              Lý do từ chối
              <textarea
                value={reason}
                onChange={e => setReason(e.target.value)}
                required
                maxLength={500}
                placeholder="Nhập lý do từ chối để người bán biết và chỉnh sửa phù hợp..."
                rows={3}
              />
            </label>
          )}
          <div className={styles.actions} style={{ marginTop: "16px" }}>
            <button
              className={confirmation.action === "DELETE" ? styles.btnDangerAction : styles.btnPrimaryAction}
              disabled={pending || stale || (confirmation.action === "REJECT" && !reason.trim())}
              onClick={() =>
                void command(() =>
                  confirmation.action === "DELETE"
                    ? deleteAdCampaign(scope, confirmation.campaign.id, confirmation.campaign.version)
                    : performAdCampaignAction(
                        scope,
                        confirmation.campaign.id,
                        confirmation.campaign.version,
                        "REJECT",
                        reason.trim(),
                      ),
                )
              }
            >
              {confirmation.action === "DELETE" ? "Xác nhận xóa" : "Xác nhận từ chối"}
            </button>
            <button disabled={pending} onClick={() => setConfirmation(null)}>
              Hủy
            </button>
          </div>
        </section>
      )}

      {stats && (
        <AdAnalytics
          key={stats.id}
          scope={scope}
          campaign={stats}
          onClose={() => setStats(null)}
          onAuth={expired}
        />
      )}
    </section>
  );
}
