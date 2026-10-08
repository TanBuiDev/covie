"use client";
import { useEffect, useRef, useState, type FormEvent } from "react";
import { AdCreative } from "@/components/ads/ad-creative";
import { uploadAdMedia } from "@/features/media/direct-ad-upload";
import { CovieApiError } from "@/lib/api/api-error";
import type { AdCampaign, AdCampaignInput } from "../ad-api";
import styles from "./management.module.css";
function localTime(value: string) { const date = new Date(value); return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
export function AdCampaignForm({ campaign, busy, onSave, onCancel, onAuth }: { campaign?: AdCampaign; busy: boolean; onSave: (input: AdCampaignInput) => Promise<void>; onCancel: () => void; onAuth: () => void }) {
  const [input, setInput] = useState<AdCampaignInput>(() => ({ title: campaign?.title ?? "", body: campaign?.body ?? "", ctaLabel: campaign?.ctaLabel ?? "Xem ngay", targetUrl: campaign?.targetUrl ?? "", creativeType: campaign?.creativeType ?? "CSS", template: campaign?.template ?? "HERO", mediaId: campaign?.mediaId, placement: campaign?.placement ?? "IN_FEED", device: campaign?.device ?? "ALL", startsAt: campaign?.startsAt ?? new Date().toISOString(), endsAt: campaign?.endsAt ?? new Date(Date.now() + 86400000 * 7).toISOString(), enabled: campaign?.enabled ?? false, priority: campaign?.priority ?? 0 }));
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState(campaign?.mediaUrl ?? "");
  const [error, setError] = useState("");
  const uploadLock = useRef(false);
  const submitLock = useRef(false);
  const disabled = busy || uploading;
  useEffect(() => () => { if (preview.startsWith("blob:")) URL.revokeObjectURL(preview); }, [preview]);
  function applyDuration(days: number) {
    const base = new Date(input.startsAt);
    const validBase = Number.isFinite(base.getTime()) ? base : new Date();
    const target = new Date(validBase.getTime() + days * 86400000);
    change("endsAt", target.toISOString());
  }

  function change<K extends keyof AdCampaignInput>(key: K, value: AdCampaignInput[K]) { setInput(current => ({ ...current, [key]: value })); }
  async function upload(file?: File) {
    if (!file || uploadLock.current || busy || input.creativeType === "CSS") return;
    const expected = input.creativeType === "VIDEO" ? ["video/mp4", "video/webm"] : ["image/png", "image/jpeg", "image/webp"];
    if (!expected.includes(file.type)) { setError("Chọn tệp đúng định dạng của nội dung quảng cáo."); return; }
    uploadLock.current = true; setUploading(true); setError("");
    try { const media = await uploadAdMedia(file); if (!media.mediaId) throw new Error(); change("mediaId", media.mediaId); setPreview(URL.createObjectURL(file)); }
    catch (cause) { if (cause instanceof CovieApiError && cause.status === 401) onAuth(); else setError("Không thể tải tệp lên. Chọn lại tệp để thử lại."); }
    finally { uploadLock.current = false; setUploading(false); }
  }
  async function submit(event: FormEvent) {
    event.preventDefault(); if (disabled || submitLock.current) return;
    if (new Date(input.endsAt) <= new Date(input.startsAt)) { setError("Thời gian kết thúc phải sau thời gian bắt đầu."); return; }
    if (input.creativeType !== "CSS" && !input.mediaId) { setError("Tải ảnh hoặc video trước khi lưu chiến dịch."); return; }
    submitLock.current = true; setError("");
    try { await onSave({ ...input, template: input.creativeType === "CSS" ? input.template : undefined, mediaId: input.creativeType === "CSS" ? undefined : input.mediaId }); }
    finally { submitLock.current = false; }
  }
  return <form className={`${styles.panel} ${styles.form}`} onSubmit={submit} aria-label="Nội dung chiến dịch">
    <h2>{campaign ? "Chỉnh sửa chiến dịch" : "Tạo chiến dịch mới"}</h2>
    {campaign && <p>Thay đổi nội dung quảng cáo của người bán cần được duyệt lại trước khi hiển thị.</p>}
    {error && <p role="alert">{error}</p>}
    <fieldset disabled={disabled} className={styles.form}><legend className="sr-only">Cấu hình chiến dịch</legend><div className={styles.fields}>
      <label>Tiêu đề<input required maxLength={160} value={input.title} onChange={e => change("title", e.target.value)} /></label>
      <label>Link đích<input required maxLength={2048} value={input.targetUrl} placeholder="/items/… hoặc https://…" onChange={e => change("targetUrl", e.target.value)} /></label>
      <label>Nội dung<textarea maxLength={500} value={input.body ?? ""} onChange={e => change("body", e.target.value)} /></label>
      <label>Nhãn nút<input required maxLength={60} value={input.ctaLabel} onChange={e => change("ctaLabel", e.target.value)} /></label>
      <label>Loại nội dung<select value={input.creativeType} onChange={e => { change("creativeType", e.target.value as AdCampaignInput["creativeType"]); change("mediaId", undefined); setPreview(""); }}><option value="IMAGE">Ảnh</option><option value="VIDEO">Video</option><option value="CSS">Mẫu động</option></select></label>
      {input.creativeType === "CSS" && <label>Mẫu trình bày<select value={input.template} onChange={e => change("template", e.target.value as AdCampaignInput["template"])}><option value="FLASH">Ưu đãi nhanh</option><option value="NEARBY">Gần bạn</option><option value="SPOTLIGHT">Nổi bật</option><option value="HERO">Tiêu điểm</option></select></label>}
      <label>Vị trí<select value={input.placement} onChange={e => change("placement", e.target.value as AdCampaignInput["placement"])}><option value="SIDEBAR_LEFT">Banner trái · trang chủ / tìm kiếm</option><option value="HOME_HERO">Banner trang chủ</option><option value="IN_FEED">Trong danh sách</option><option value="ITEM_DETAIL">Dưới chi tiết sản phẩm</option></select></label>
      <label>Thiết bị<select value={input.device} onChange={e => change("device", e.target.value as AdCampaignInput["device"])}><option value="ALL">Mọi thiết bị</option><option value="DESKTOP">Máy tính</option><option value="MOBILE">Di động</option></select></label>
      <label>Bắt đầu<input required type="datetime-local" value={localTime(input.startsAt)} onChange={e => { if (e.target.value) change("startsAt", new Date(e.target.value).toISOString()); }} /></label>
      <div className={styles.dateFieldWrapper}>
        <label>Kết thúc<input required type="datetime-local" value={localTime(input.endsAt)} onChange={e => { if (e.target.value) change("endsAt", new Date(e.target.value).toISOString()); }} /></label>
        <div className={styles.durationPresets} aria-label="Chọn nhanh thời hạn">
          <button type="button" className={styles.presetBtn} onClick={() => applyDuration(7)}>+7 ngày</button>
          <button type="button" className={styles.presetBtn} onClick={() => applyDuration(14)}>+14 ngày</button>
          <button type="button" className={styles.presetBtn} onClick={() => applyDuration(30)}>+1 tháng</button>
          <button type="button" className={styles.presetBtn} onClick={() => applyDuration(90)}>+3 tháng</button>
        </div>
      </div>
      <label>Độ ưu tiên<input required type="number" min={0} max={1000} value={input.priority} onChange={e => change("priority", Number(e.target.value))} /></label>
      <label>Hiển thị<select value={input.enabled ? "yes" : "no"} onChange={e => change("enabled", e.target.value === "yes")}><option value="no">Tắt</option><option value="yes">Bật khi đủ điều kiện</option></select></label>
    </div></fieldset>
    {input.creativeType !== "CSS" && <div className={styles.drop} onDragOver={e => e.preventDefault()} onDrop={e => { e.preventDefault(); if (!disabled) void upload(e.dataTransfer.files[0]); }}><label>Ảnh / video quảng cáo<input type="file" disabled={disabled} accept={input.creativeType === "VIDEO" ? "video/mp4,video/webm" : "image/webp,image/jpeg,image/png"} onChange={e => { void upload(e.target.files?.[0]); e.target.value = ""; }} /></label><p>Kéo tệp vào đây hoặc chọn tệp. Ảnh tối đa 10 MB; video tối đa 50 MB.</p>{uploading && <p role="status">Đang tải và xác minh tệp…</p>}{input.mediaId && <p role="status">Tệp đã được xác minh.</p>}</div>}
    <aside className={styles.preview} aria-label="Xem trước quảng cáo"><p>Xem trước</p><AdCreative key={`${input.creativeType}:${input.mediaId}:${preview}`} paused ad={{ id: campaign?.id ?? "preview", ...input, mediaUrl: preview || undefined, title: input.title || "Tiêu đề quảng cáo", targetUrl: "#", deliveryToken: "", deliveryExpiresAt: input.endsAt }} /></aside>
    <div className={styles.actions}><button type="submit" disabled={disabled}>Lưu chiến dịch</button><button type="button" disabled={disabled} onClick={onCancel}>Hủy chỉnh sửa</button></div>
  </form>;
}
