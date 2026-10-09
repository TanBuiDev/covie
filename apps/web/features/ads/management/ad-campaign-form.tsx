"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  CloudArrowUp,
  MegaphoneSimple,
  SlidersHorizontal,
  CalendarBlank,
  Eye,
  FloppyDisk,
  X,
  Sparkle,
} from "@phosphor-icons/react";
import { AdCreative } from "@/components/ads/ad-creative";
import { uploadAdMedia } from "@/features/media/direct-ad-upload";
import { CovieApiError } from "@/lib/api/api-error";
import type { AdCampaign, AdCampaignInput } from "../ad-api";
import styles from "./management.module.css";

function localTime(value: string) {
  const date = new Date(value);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
}

export function AdCampaignForm({
  campaign,
  busy,
  onSave,
  onCancel,
  onAuth,
}: {
  campaign?: AdCampaign;
  busy: boolean;
  onSave: (input: AdCampaignInput) => Promise<void>;
  onCancel: () => void;
  onAuth: () => void;
}) {
  const [input, setInput] = useState<AdCampaignInput>(() => ({
    title: campaign?.title ?? "",
    body: campaign?.body ?? "",
    ctaLabel: campaign?.ctaLabel ?? "Xem ngay",
    targetUrl: campaign?.targetUrl ?? "",
    creativeType: campaign?.creativeType ?? "CSS",
    template: campaign?.template ?? "HERO",
    mediaId: campaign?.mediaId,
    placement: campaign?.placement ?? "IN_FEED",
    device: campaign?.device ?? "ALL",
    startsAt: campaign?.startsAt ?? new Date().toISOString(),
    endsAt: campaign?.endsAt ?? new Date(Date.now() + 86400000 * 7).toISOString(),
    enabled: campaign?.enabled ?? false,
    priority: campaign?.priority ?? 0,
  }));
  const [uploading, setUploading] = useState(false);
  const [preview, setPreview] = useState(campaign?.mediaUrl ?? "");
  const [error, setError] = useState("");
  const uploadLock = useRef(false);
  const submitLock = useRef(false);
  const disabled = busy || uploading;

  useEffect(() => () => {
    if (preview.startsWith("blob:")) URL.revokeObjectURL(preview);
  }, [preview]);

  function applyDuration(days: number) {
    const base = new Date(input.startsAt);
    const validBase = Number.isFinite(base.getTime()) ? base : new Date();
    const target = new Date(validBase.getTime() + days * 86400000);
    change("endsAt", target.toISOString());
  }

  function change<K extends keyof AdCampaignInput>(key: K, value: AdCampaignInput[K]) {
    setInput(current => ({ ...current, [key]: value }));
  }

  async function upload(file?: File) {
    if (!file || uploadLock.current || busy || input.creativeType === "CSS") return;
    const expected =
      input.creativeType === "VIDEO" ? ["video/mp4", "video/webm"] : ["image/png", "image/jpeg", "image/webp"];
    if (!expected.includes(file.type)) {
      setError("Chọn tệp đúng định dạng của nội dung quảng cáo.");
      return;
    }
    uploadLock.current = true;
    setUploading(true);
    setError("");
    try {
      const media = await uploadAdMedia(file);
      if (!media.mediaId) throw new Error();
      change("mediaId", media.mediaId);
      setPreview(URL.createObjectURL(file));
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) onAuth();
      else setError("Không thể tải tệp lên. Chọn lại tệp để thử lại.");
    } finally {
      uploadLock.current = false;
      setUploading(false);
    }
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (disabled || submitLock.current) return;
    if (new Date(input.endsAt) <= new Date(input.startsAt)) {
      setError("Thời gian kết thúc phải sau thời gian bắt đầu.");
      return;
    }
    if (input.creativeType !== "CSS" && !input.mediaId) {
      setError("Tải ảnh hoặc video trước khi lưu chiến dịch.");
      return;
    }
    submitLock.current = true;
    setError("");
    try {
      await onSave({
        ...input,
        template: input.creativeType === "CSS" ? input.template : undefined,
        mediaId: input.creativeType === "CSS" ? undefined : input.mediaId,
      });
    } finally {
      submitLock.current = false;
    }
  }

  return (
    <form className={`${styles.panel} ${styles.form}`} onSubmit={submit} aria-label="Nội dung chiến dịch">
      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between", flexWrap: "wrap", gap: "10px" }}>
        <div>
          <h2>{campaign ? "Chỉnh sửa chiến dịch" : "Tạo chiến dịch mới"}</h2>
          {campaign && <p>Thay đổi nội dung quảng cáo của người bán cần được duyệt lại trước khi hiển thị.</p>}
        </div>
        <span
          style={{
            display: "inline-flex",
            alignItems: "center",
            gap: "5px",
            fontSize: "12px",
            color: "#047857",
            background: "#ecfdf5",
            border: "1px solid #a7f3d0",
            borderRadius: "999px",
            padding: "4px 10px",
            fontWeight: 600,
          }}
        >
          <Sparkle size={14} weight="bold" aria-hidden="true" />
          <span>Trực quan hóa quảng cáo</span>
        </span>
      </div>

      {error && <p role="alert" className={styles.reviewAlert}>{error}</p>}

      <fieldset disabled={disabled} className={styles.form}>
        <legend className="sr-only">Cấu hình chiến dịch</legend>

        {/* Khối 1: Thông điệp & Điều hướng */}
        <div className={styles.formCard}>
          <div className={styles.formSectionHeader}>
            <MegaphoneSimple size={18} weight="bold" color="#047857" aria-hidden="true" />
            <h3>Thông điệp & Điều hướng người dùng</h3>
          </div>
          <div className={styles.fields}>
            <label>
              Tiêu đề
              <input
                required
                maxLength={160}
                value={input.title}
                onChange={e => change("title", e.target.value)}
                placeholder="VD: Ưu đãi đặc quyền cuối tuần"
              />
            </label>
            <label>
              Link đích
              <input
                required
                maxLength={2048}
                value={input.targetUrl}
                placeholder="/items/… hoặc https://…"
                onChange={e => change("targetUrl", e.target.value)}
              />
            </label>
            <label style={{ gridColumn: "1 / -1" }}>
              Nội dung
              <textarea
                maxLength={500}
                value={input.body ?? ""}
                onChange={e => change("body", e.target.value)}
                placeholder="Mô tả thông điệp quảng cáo ngắn gọn thu hút khách hàng"
                rows={3}
              />
            </label>
            <label>
              Nhãn nút
              <input
                required
                maxLength={60}
                value={input.ctaLabel}
                onChange={e => change("ctaLabel", e.target.value)}
                placeholder="VD: Xem ngay, Mua liền tay"
              />
            </label>
            <label>
              Loại nội dung
              <select
                value={input.creativeType}
                onChange={e => {
                  change("creativeType", e.target.value as AdCampaignInput["creativeType"]);
                  change("mediaId", undefined);
                  setPreview("");
                }}
              >
                <option value="IMAGE">Ảnh</option>
                <option value="VIDEO">Video</option>
                <option value="CSS">Mẫu động</option>
              </select>
            </label>
            {input.creativeType === "CSS" && (
              <label style={{ gridColumn: "1 / -1" }}>
                Mẫu trình bày
                <select
                  value={input.template}
                  onChange={e => change("template", e.target.value as AdCampaignInput["template"])}
                >
                  <option value="FLASH">Ưu đãi nhanh</option>
                  <option value="NEARBY">Gần bạn</option>
                  <option value="SPOTLIGHT">Nổi bật</option>
                  <option value="HERO">Tiêu điểm</option>
                </select>
              </label>
            )}
          </div>
        </div>

        {/* Khối 2: Vị trí & Phân phối */}
        <div className={styles.formCard}>
          <div className={styles.formSectionHeader}>
            <SlidersHorizontal size={18} weight="bold" color="#047857" aria-hidden="true" />
            <h3>Vị trí hiển thị & Thiết bị mục tiêu</h3>
          </div>
          <div className={styles.fields}>
            <label>
              Vị trí
              <select
                value={input.placement}
                onChange={e => change("placement", e.target.value as AdCampaignInput["placement"])}
              >
                <option value="SIDEBAR_LEFT">Banner trái · trang chủ / tìm kiếm</option>
                <option value="HOME_HERO">Banner trang chủ</option>
                <option value="IN_FEED">Trong danh sách</option>
                <option value="ITEM_DETAIL">Dưới chi tiết sản phẩm</option>
              </select>
            </label>
            <label>
              Thiết bị
              <select
                value={input.device}
                onChange={e => change("device", e.target.value as AdCampaignInput["device"])}
              >
                <option value="ALL">Mọi thiết bị</option>
                <option value="DESKTOP">Máy tính</option>
                <option value="MOBILE">Di động</option>
              </select>
            </label>
            <label>
              Độ ưu tiên
              <input
                required
                type="number"
                min={0}
                max={1000}
                value={input.priority}
                onChange={e => change("priority", Number(e.target.value))}
              />
            </label>
            <label>
              Hiển thị
              <select
                value={input.enabled ? "yes" : "no"}
                onChange={e => change("enabled", e.target.value === "yes")}
              >
                <option value="no">Tắt</option>
                <option value="yes">Bật khi đủ điều kiện</option>
              </select>
            </label>
          </div>
        </div>

        {/* Khối 3: Lịch phát sóng & Thời hạn */}
        <div className={styles.formCard}>
          <div className={styles.formSectionHeader}>
            <CalendarBlank size={18} weight="bold" color="#047857" aria-hidden="true" />
            <h3>Lịch phát sóng & Thời hạn chiến dịch</h3>
          </div>
          <div className={styles.fields}>
            <label>
              Bắt đầu
              <input
                required
                type="datetime-local"
                value={localTime(input.startsAt)}
                onChange={e => {
                  if (e.target.value) change("startsAt", new Date(e.target.value).toISOString());
                }}
              />
            </label>
            <div className={styles.dateFieldWrapper}>
              <label>
                Kết thúc
                <input
                  required
                  type="datetime-local"
                  value={localTime(input.endsAt)}
                  onChange={e => {
                    if (e.target.value) change("endsAt", new Date(e.target.value).toISOString());
                  }}
                />
              </label>
              <div className={styles.durationPresets} aria-label="Chọn nhanh thời hạn">
                <span>Chọn nhanh:</span>
                <button type="button" className={styles.presetBtn} onClick={() => applyDuration(7)}>
                  +7 ngày
                </button>
                <button type="button" className={styles.presetBtn} onClick={() => applyDuration(14)}>
                  +14 ngày
                </button>
                <button type="button" className={styles.presetBtn} onClick={() => applyDuration(30)}>
                  +1 tháng
                </button>
                <button type="button" className={styles.presetBtn} onClick={() => applyDuration(90)}>
                  +3 tháng
                </button>
              </div>
            </div>
          </div>
        </div>
      </fieldset>

      {/* Upload Zone */}
      {input.creativeType !== "CSS" && (
        <div
          className={styles.drop}
          onDragOver={e => e.preventDefault()}
          onDrop={e => {
            e.preventDefault();
            if (!disabled) void upload(e.dataTransfer.files[0]);
          }}
        >
          <CloudArrowUp size={44} className={styles.dropIcon} weight="duotone" aria-hidden="true" />
          <label>
            Ảnh / video quảng cáo
            <input
              type="file"
              disabled={disabled}
              accept={input.creativeType === "VIDEO" ? "video/mp4,video/webm" : "image/webp,image/jpeg,image/png"}
              onChange={e => {
                void upload(e.target.files?.[0]);
                e.target.value = "";
              }}
            />
          </label>
          <p>Kéo tệp vào đây hoặc chọn tệp. Ảnh tối đa 10 MB; video tối đa 50 MB.</p>
          {uploading && <p role="status" style={{ color: "#047857", fontWeight: 600 }}>Đang tải và xác minh tệp…</p>}
          {input.mediaId && <p role="status" style={{ color: "#047857", fontWeight: 600 }}>Tệp đã được xác minh.</p>}
        </div>
      )}

      {/* Live Preview */}
      <aside className={styles.preview} aria-label="Xem trước quảng cáo">
        <div className={styles.previewHeader}>
          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <Eye size={16} weight="bold" color="#047857" aria-hidden="true" />
            <p style={{ margin: 0 }}>Xem trước</p>
          </div>
          <span style={{ fontSize: "11px", color: "#64748b", textTransform: "none", fontWeight: 500 }}>
            {input.creativeType === "CSS" ? "Mẫu động CSS" : input.creativeType === "VIDEO" ? "Video Media" : "Hình ảnh Media"}
          </span>
        </div>
        <AdCreative
          key={`${input.creativeType}:${input.mediaId}:${preview}`}
          paused
          ad={{
            id: campaign?.id ?? "preview",
            ...input,
            mediaUrl: preview || undefined,
            title: input.title || "Tiêu đề quảng cáo",
            targetUrl: "#",
            deliveryToken: "",
            deliveryExpiresAt: input.endsAt,
          }}
        />
      </aside>

      {/* Form Action Buttons */}
      <div className={styles.actions}>
        <button
          type="submit"
          className={styles.btnPrimaryAction}
          disabled={disabled}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <FloppyDisk size={16} weight="bold" aria-hidden="true" />
          <span>Lưu chiến dịch</span>
        </button>
        <button
          type="button"
          disabled={disabled}
          onClick={onCancel}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <X size={16} aria-hidden="true" />
          <span>Hủy chỉnh sửa</span>
        </button>
      </div>
    </form>
  );
}
