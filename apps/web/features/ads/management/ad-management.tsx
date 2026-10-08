"use client";
import Link from "next/link";
import { useCallback, useEffect, useRef, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { listAdCampaigns, createAdCampaign, updateAdCampaign, deleteAdCampaign, performAdCampaignAction, type AdCampaign, type AdCampaignInput, type AdScope } from "../ad-api";
import { AdCampaignForm } from "./ad-campaign-form";
import { AdAnalytics } from "./ad-analytics";
import { StatusChip } from "@/components/ui/status-chip";
import styles from "./management.module.css";
const statuses: Record<string, string> = { DRAFT: "Bản nháp", PENDING: "Chờ duyệt", APPROVED: "Đã duyệt", REJECTED: "Cần chỉnh sửa" };
const placements: Record<string, string> = { SIDEBAR_LEFT: "Banner trái", HOME_HERO: "Banner trang chủ", IN_FEED: "Trong danh sách", ITEM_DETAIL: "Chi tiết sản phẩm" };
type ManagedCampaign = AdCampaign & Required<Pick<AdCampaign, "id" | "version" | "status" | "placement" | "startsAt" | "endsAt" | "allowedActions">>;
function validCampaign(item: AdCampaign): item is ManagedCampaign {
  return typeof item.id === "string" && !!item.id && typeof item.version === "number" && Number.isInteger(item.version) && item.version >= 0 && typeof item.status === "string" && typeof item.placement === "string" && typeof item.startsAt === "string" && Number.isFinite(Date.parse(item.startsAt)) && typeof item.endsAt === "string" && Number.isFinite(Date.parse(item.endsAt)) && Array.isArray(item.allowedActions);
}
export function AdManagement({ scope }: { scope: AdScope }) {
  return <AdManagementContent key={scope.kind === "admin" ? "admin" : `seller:${scope.merchantId}`} scope={scope} />;
}
function AdManagementContent({ scope }: { scope: AdScope }) {
  const [items, setItems] = useState<ManagedCampaign[]>([]);
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
    else if (cause instanceof CovieApiError && cause.status === 403) setError("Bạn không có quyền thực hiện thao tác này.");
    else if (cause instanceof CovieApiError && [409, 412].includes(cause.status)) { setStale(true); setError("Chiến dịch đã thay đổi. Tải lại danh sách trước khi tiếp tục."); }
    else setError(message);
  }, []);
  const load = useCallback(async () => {
    const revision = ++readGeneration.current;
    try {
      const rows = await listAdCampaigns(merchantId ? { kind: "seller", merchantId } : { kind: "admin" });
      if (mounted.current && revision === readGeneration.current) { const valid = rows.filter(validCampaign); setItems(valid); offset.current = rows.length; setHasMore(rows.length === 100); setMoreError(""); setError(valid.length === rows.length ? "" : "Một số dữ liệu chiến dịch chưa hợp lệ. Tải lại trước khi thao tác."); setStale(valid.length !== rows.length); }
    } catch (cause) { if (mounted.current && revision === readGeneration.current) { setStale(true); handleError(cause, "Không thể tải danh sách. Bạn có thể thử tải lại."); } }
    finally { if (mounted.current && revision === readGeneration.current) setLoading(false); }
  }, [merchantId, handleError]);
  useEffect(() => { mounted.current = true; void Promise.resolve().then(() => { if (mounted.current) return load(); }); return () => { mounted.current = false; }; }, [load]);
  async function loadMore() {
    if (moreLock.current || lock.current || !hasMore || stale || auth) return;
    const revision = readGeneration.current;
    moreLock.current = true; setMoreLoading(true); setMoreError("");
    try {
      const rows = await listAdCampaigns(scope, offset.current);
      if (!mounted.current || revision !== readGeneration.current) return;
      const valid = rows.filter(validCampaign);
      if (valid.length !== rows.length) { setStale(true); setError("Một số dữ liệu chiến dịch chưa hợp lệ. Tải lại trước khi thao tác."); return; }
      setItems(current => { const byId = new Map(current.map(item => [item.id, item])); for (const item of valid) byId.set(item.id, item); return [...byId.values()]; });
      offset.current += rows.length; setHasMore(rows.length === 100);
    } catch (cause) {
      if (mounted.current && revision === readGeneration.current) { if (cause instanceof CovieApiError && cause.status === 401) setAuth(true); else setMoreError("Không thể tải thêm chiến dịch. Danh sách đã tải được giữ lại."); }
    } finally { moreLock.current = false; if (mounted.current) setMoreLoading(false); }
  }
  async function command(write: () => Promise<unknown>) {
    if (lock.current || moreLock.current || auth || stale) return;
    lock.current = true; setPending(true); setError("");
    try { await write(); if (mounted.current) { setEditor(null); setConfirmation(null); } await load(); }
    catch (cause) { handleError(cause, "Thao tác chưa được xác nhận. Tải lại danh sách để kiểm tra trước khi thử lại."); setStale(true); }
    finally { lock.current = false; if (mounted.current) setPending(false); }
  }
  function save(body: AdCampaignInput) { return command(() => editor && editor !== "new" ? updateAdCampaign(scope, editor.id, editor.version, body) : createAdCampaign(scope, body)); }
  if (auth) return <section className={styles.panel}><h1>Quảng cáo</h1><p>Phiên đăng nhập đã hết hạn.</p><Link href={`/login?returnTo=${encodeURIComponent(path)}`}>Đăng nhập lại</Link></section>;
  return <section className={styles.root}>
    <header className="surface-header"><p className="eyebrow">{scope.kind === "admin" ? "Quản trị" : "Trung tâm bán hàng"}</p><h1>Quảng cáo</h1><p>Quản lý nội dung, lịch hiển thị và hiệu quả chiến dịch.</p><button className={scope.kind === "admin" ? "admin-button admin-button--primary" : "button-primary"} disabled={pending || stale || loading} onClick={() => setEditor("new")}>Tạo chiến dịch</button></header>
    {error && <div role="alert" className={styles.notice}>{error} <button disabled={pending} onClick={() => void load()}>Tải lại danh sách</button></div>}
    {loading ? <p role="status">Đang tải chiến dịch…</p> : <>
      {!items.length && !error && <div className={styles.panel}><h2>Chưa có chiến dịch quảng cáo.</h2><p>Tạo nội dung đầu tiên và chọn lịch hiển thị phù hợp.</p></div>}
      {hasMore && <p role="status">Đang hiển thị {items.length} chiến dịch. Tải thêm để xem các chiến dịch trước.</p>}
      <div className={styles.list}>{items.map(item => <article key={item.id} className={styles.panel} aria-label={item.title}>
        <div className={styles.row}><h2>{item.title}</h2><StatusChip label={statuses[item.status] ?? "Trạng thái đã được cập nhật"} tone="neutral" /></div>
        <p>{placements[item.placement]} · {item.device === "ALL" ? "Mọi thiết bị" : item.device === "MOBILE" ? "Di động" : "Máy tính"} · {item.enabled ? "Đã bật" : "Đã tắt"} · Ưu tiên {item.priority}</p>
        <p>{new Date(item.startsAt).toLocaleString("vi-VN")} – {new Date(item.endsAt).toLocaleString("vi-VN")}</p>
        {item.reviewReason && <p>Lý do kiểm duyệt: {item.reviewReason}</p>}
        <div className={styles.actions}>
          {item.allowedActions.includes("UPDATE") && <button disabled={pending || stale} onClick={() => setEditor(item)}>Chỉnh sửa</button>}
          {item.allowedActions.includes("DELETE") && <button disabled={pending || stale} onClick={() => setConfirmation({ campaign: item, action: "DELETE" })}>Xóa</button>}
          {item.allowedActions.includes("SUBMIT") && <button disabled={pending || stale} onClick={() => void command(() => performAdCampaignAction(scope, item.id, item.version, "SUBMIT", undefined))}>Gửi duyệt</button>}
          {item.allowedActions.includes("APPROVE") && <button disabled={pending || stale} onClick={() => void command(() => performAdCampaignAction(scope, item.id, item.version, "APPROVE", undefined))}>Duyệt</button>}
          {item.allowedActions.includes("REJECT") && <button disabled={pending || stale} onClick={() => { setReason(""); setConfirmation({ campaign: item, action: "REJECT" }); }}>Từ chối</button>}
          {item.allowedActions.includes("VIEW_STATS") && <button disabled={pending || stale} onClick={() => setStats(item)}>Thống kê</button>}
        </div>
      </article>)}</div>
      {moreError && <p role="alert">{moreError}</p>}
      {hasMore && <div className={styles.actions}><button disabled={pending || stale || moreLoading} onClick={() => void loadMore()}>{moreLoading ? "Đang tải thêm…" : moreError ? "Thử tải thêm" : "Xem thêm chiến dịch"}</button></div>}
    </>}
    {editor && <AdCampaignForm key={editor === "new" ? "new" : `${editor.id}:${editor.version}`} campaign={editor === "new" ? undefined : editor} busy={pending || stale} onSave={save} onCancel={() => setEditor(null)} onAuth={expired} />}
    {confirmation && <section className={styles.panel} aria-label="Xác nhận thao tác"><h2>{confirmation.action === "DELETE" ? "Xóa chiến dịch?" : "Từ chối chiến dịch"}</h2><p>{confirmation.campaign.title}</p>
      {confirmation.action === "REJECT" && <label>Lý do từ chối<textarea value={reason} onChange={e => setReason(e.target.value)} required maxLength={500} /></label>}
      <div className={styles.actions}><button disabled={pending || stale || (confirmation.action === "REJECT" && !reason.trim())} onClick={() => void command(() => confirmation.action === "DELETE" ? deleteAdCampaign(scope, confirmation.campaign.id, confirmation.campaign.version) : performAdCampaignAction(scope, confirmation.campaign.id, confirmation.campaign.version, "REJECT", reason.trim()))}>{confirmation.action === "DELETE" ? "Xác nhận xóa" : "Xác nhận từ chối"}</button><button disabled={pending} onClick={() => setConfirmation(null)}>Hủy</button></div>
    </section>}
    {stats && <AdAnalytics key={stats.id} scope={scope} campaign={stats} onClose={() => setStats(null)} onAuth={expired} />}
  </section>;
}
