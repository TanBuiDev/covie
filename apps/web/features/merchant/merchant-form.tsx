"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import {
  ArrowRight,
  Check,
  CheckCircle,
  FloppyDisk,
  Image as ImageIcon,
  MagnifyingGlass,
  Sparkle,
  Storefront,
  UploadSimple,
} from "@phosphor-icons/react";
import type { Etagged } from "./merchant-api";
import { createMerchant, updateMerchant } from "./merchant-api";

type MerchantSummary = components["schemas"]["MerchantSummaryResponse"];

function merchantRoleCopy(role?: MerchantSummary["currentUserRole"]) {
  switch (role) {
    case "OWNER": return "Chủ cửa hàng";
    case "MANAGER": return "Quản lý";
    case "STAFF": return "Nhân viên";
    default: return "Thành viên";
  }
}

function merchantStatusPresentation(status?: MerchantSummary["status"]): { label: string; tone: StatusTone } {
  switch (status) {
    case "ACTIVE": return { label: "Đang hoạt động", tone: "success" };
    case "PAUSED": return { label: "Tạm dừng", tone: "warning" };
    case "SUSPENDED": return { label: "Bị hạn chế", tone: "error" };
    case "DELETED": return { label: "Đã đóng", tone: "neutral" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

export function MerchantList({ merchants }: { merchants: MerchantSummary[] }) {
  const [searchQuery, setSearchQuery] = useState("");

  const filteredMerchants = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return merchants;
    return merchants.filter((m) => (m.name ?? "").toLowerCase().includes(q));
  }, [merchants, searchQuery]);

  return (
    <div className="seller-merchant-container">
      {merchants.length > 2 ? (
        <div className="seller-merchant-search">
          <MagnifyingGlass size={18} className="seller-merchant-search__icon" aria-hidden="true" />
          <input
            type="search"
            placeholder="Tìm kiếm cửa hàng theo tên…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="seller-merchant-search__input"
            aria-label="Tìm kiếm cửa hàng"
          />
        </div>
      ) : null}

      {filteredMerchants.length === 0 ? (
        <div className="seller-merchant-empty-search">
          <p>Không tìm thấy cửa hàng nào khớp với từ khóa tìm kiếm.</p>
        </div>
      ) : (
        <div className="seller-row-list seller-merchant-grid">
          {filteredMerchants.map((merchant) => {
            if (!merchant.id) return null;
            const status = merchantStatusPresentation(merchant.status);
            return (
              <Link
                className="seller-row seller-merchant-card"
                key={merchant.id}
                href={`/seller/${encodeURIComponent(merchant.id)}`}
              >
                <div className="seller-merchant-card__main">
                  <div className="seller-merchant-card__icon" aria-hidden="true">
                    <Storefront size={24} weight="duotone" />
                  </div>
                  <div className="seller-merchant-card__content">
                    <strong>{merchant.name ?? "Cửa hàng"}</strong>
                    <div className="seller-merchant-card__subinfo">
                      <small className="seller-merchant-card__role">{merchantRoleCopy(merchant.currentUserRole)}</small>
                      {merchant.slug ? (
                        <span className="seller-merchant-card__slug">@{merchant.slug}</span>
                      ) : null}
                    </div>
                  </div>
                </div>
                <div className="seller-merchant-card__meta">
                  <StatusChip label={status.label} tone={status.tone} />
                  <span className="seller-merchant-card__action">
                    <span>Vào quản lý</span>
                    <ArrowRight size={14} weight="bold" />
                  </span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}

export function MerchantForm({ merchant, onCreated, onReload }: {
  merchant?: Etagged<components["schemas"]["MerchantManagementResponse"]>;
  onCreated?: () => void;
  onReload?: () => void;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [showMedia, setShowMedia] = useState(false);
  const editing = Boolean(merchant?.data.id);
  const [previewName, setPreviewName] = useState(merchant?.data.name ?? "");
  const [previewDesc, setPreviewDesc] = useState(merchant?.data.description ?? "");
  const reload = onReload ?? (() => router.refresh());
  const saved = onCreated ?? (() => router.refresh());

  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [logoPreview, setLogoPreview] = useState<string | null>((merchant?.data as unknown as { logoUrl?: string | null })?.logoUrl ?? null);
  const [coverPreview, setCoverPreview] = useState<string | null>((merchant?.data as unknown as { coverUrl?: string | null })?.coverUrl ?? null);
  const [uploadStatus, setUploadStatus] = useState<string>();

  const mediaVersion = `${merchant?.data.id ?? ""}:${merchant?.etag ?? ""}`;
  const [previousMediaVersion, setPreviousMediaVersion] = useState(mediaVersion);
  if (mediaVersion !== previousMediaVersion) {
    setPreviousMediaVersion(mediaVersion);
    setLogoPreview(merchant?.data.logoUrl ?? null);
    setCoverPreview(merchant?.data.coverUrl ?? null);
    setLogoFile(null);
    setCoverFile(null);
  }

  useEffect(() => {
    return () => {
      if (logoPreview?.startsWith("blob:")) URL.revokeObjectURL(logoPreview);
      if (coverPreview?.startsWith("blob:")) URL.revokeObjectURL(coverPreview);
    };
  }, [logoPreview, coverPreview]);

  function handleLogoSelect(file?: File) {
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setMessage("Ảnh logo phải là JPEG, PNG hoặc WebP.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setMessage("Ảnh logo phải nhỏ hơn hoặc bằng 10 MB.");
      return;
    }
    setLogoFile(file);
    setLogoPreview(URL.createObjectURL(file));
    setMessage(undefined);
  }

  function handleCoverSelect(file?: File) {
    if (!file) return;
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setMessage("Ảnh bìa phải là JPEG, PNG hoặc WebP.");
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setMessage("Ảnh bìa phải nhỏ hơn hoặc bằng 10 MB.");
      return;
    }
    setCoverFile(file);
    setCoverPreview(URL.createObjectURL(file));
    setMessage(undefined);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);

    setPending(true);
    setMessage(undefined);
    setUploadStatus(undefined);
    try {
      let logoMediaId: string | undefined = merchant?.data.logoMediaId ?? undefined;
      let coverMediaId: string | undefined = merchant?.data.coverMediaId ?? undefined;

      if (editing) {
        const formLogo = form.get("logo");
        const formCover = form.get("cover");
        const targetLogo = logoFile ?? (formLogo instanceof File && formLogo.size > 0 ? formLogo : null);
        const targetCover = coverFile ?? (formCover instanceof File && formCover.size > 0 ? formCover : null);

        if (targetLogo) {
          setUploadStatus("Đang tải lên logo shop…");
          const uploaded = await uploadImage(targetLogo, "MERCHANT_LOGO");
          if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
          logoMediaId = uploaded.mediaId;
          if (uploaded.url) {
            setLogoPreview(String(uploaded.url));
          }
        }
        if (targetCover) {
          setUploadStatus("Đang tải lên ảnh bìa shop…");
          const uploaded = await uploadImage(targetCover, "MERCHANT_COVER");
          if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
          coverMediaId = uploaded.mediaId;
          if (uploaded.url) {
            setCoverPreview(String(uploaded.url));
          }
        }
      }

      setUploadStatus("Đang lưu thông tin cửa hàng…");

      const input = {
        name: String(form.get("name") ?? "").trim(),
        description: String(form.get("description") ?? "").trim() || undefined,
        ...(logoMediaId ? { logoMediaId } : {}),
        ...(coverMediaId ? { coverMediaId } : {}),
      };

      if (editing && merchant?.data.id) await updateMerchant(merchant.data.id, merchant.etag, input);
      else await createMerchant(input);
      setMessage(editing ? "Đã lưu thay đổi hồ sơ shop." : "Đã tạo cửa hàng.");
      saved();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Dữ liệu đã được thay đổi ở nơi khác. Hãy tải dữ liệu mới trước khi lưu lại.");
      } else if (error instanceof Error && error.message === "MEDIA_TYPE_NOT_SUPPORTED") {
        setMessage("Ảnh phải là JPEG, PNG hoặc WebP.");
      } else if (error instanceof Error && error.message === "MEDIA_FILE_TOO_LARGE") {
        setMessage("Mỗi ảnh phải nhỏ hơn hoặc bằng 10 MB.");
      } else {
        setMessage("Không thể lưu cửa hàng. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
      setUploadStatus(undefined);
    }
  }

  return (
    <form className={editing ? "seller-merchant-form" : undefined} onSubmit={submit} aria-busy={pending}>
      {!editing ? <h2>Tạo cửa hàng</h2> : null}
      {editing ? <h2 className="seller-settings-card-title seller-desktop-only">Hồ sơ shop công khai</h2> : null}

      <label>
        <span className="seller-field-title">
          {editing ? "Tên shop" : "Tên cửa hàng"} <span className="seller-required-star" aria-hidden="true">*</span>
        </span>
        <input
          aria-label={editing ? "Tên shop" : "Tên cửa hàng"}
          name="name"
          required
          disabled={pending}
          defaultValue={merchant?.data.name ?? ""}
          onChange={(e) => setPreviewName(e.target.value)}
          autoComplete="organization"
        />
        {editing ? <small>Tên hiển thị công khai</small> : null}
      </label>
      <label>
        <span className="seller-field-title">
          {editing ? "Giới thiệu shop" : "Mô tả"}
        </span>
        <textarea
          name="description"
          maxLength={4000}
          disabled={pending}
          defaultValue={merchant?.data.description ?? ""}
          onChange={(e) => setPreviewDesc(e.target.value)}
        />
        {editing ? <small>Hiển thị trên trang shop công khai</small> : null}
      </label>

      <div className="seller-live-preview" aria-hidden="true" style={{ marginTop: "12px", marginBottom: "16px" }}>
        <div className="seller-live-preview__header">
          <Sparkle size={15} weight="fill" className="seller-live-preview__sparkle" />
          <span>{editing ? "Xem trước thẻ hiển thị cho cư dân CoVie" : "Xem trước thẻ hiển thị cho cư dân"}</span>
        </div>
        <div
          className="seller-live-preview__card"
          style={coverPreview ? {
            backgroundImage: `linear-gradient(rgba(15, 23, 42, 0.45), rgba(15, 23, 42, 0.75)), url(${coverPreview})`,
            backgroundSize: "cover",
            backgroundPosition: "center",
          } : undefined}
        >
          <div
            className="seller-live-preview__icon"
            style={logoPreview ? {
              background: "#ffffff",
              padding: 0,
              overflow: "hidden",
              border: "1px solid rgba(255, 255, 255, 0.8)",
            } : undefined}
          >
            {logoPreview ? (
              // eslint-disable-next-line @next/next/no-img-element
              <img
                src={logoPreview}
                alt=""
                style={{ width: "100%", height: "100%", objectFit: "cover", display: "block" }}
              />
            ) : (
              <Storefront size={26} weight="duotone" />
            )}
          </div>
          <div className="seller-live-preview__info">
            <strong className="seller-live-preview__title" style={coverPreview ? { color: "#ffffff" } : undefined}>
              {previewName.trim() || (editing ? "Tên shop của bạn" : "Tên cửa hàng của bạn")}
            </strong>
            <p className="seller-live-preview__desc" style={coverPreview ? { color: "#e2e8f0" } : undefined}>
              {previewDesc.trim() || (editing ? "Mô tả giới thiệu gian hàng, sản phẩm hoặc dịch vụ…" : "Mô tả món ăn, sản phẩm hoặc dịch vụ nội khu của bạn…")}
            </p>
            <div className="seller-live-preview__tags">
              <span className="seller-live-preview__tag" style={coverPreview ? { background: "rgba(255, 255, 255, 0.2)", color: "#ffffff" } : undefined}>
                {editing ? "Đang hoạt động" : "Cửa hàng mới"}
              </span>
              <span className="seller-live-preview__tag" style={coverPreview ? { background: "rgba(255, 255, 255, 0.2)", color: "#ffffff" } : undefined}>
                Nội khu CoVie
              </span>
            </div>
          </div>
        </div>
      </div>

      {editing ? (
        <section className="seller-media-settings" aria-label="Logo và ảnh bìa">
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <ImageIcon size={18} weight="duotone" style={{ color: "#047857" }} />
            <strong>Logo & ảnh bìa</strong>
          </div>
          <p>Dùng ảnh đã tải lên CoVie và thuộc shop này.</p>
          <button className="seller-button--secondary" type="button" disabled={pending} onClick={() => setShowMedia((value) => !value)}>
            {showMedia ? "Đóng quản lý hình ảnh" : "Quản lý hình ảnh"}
          </button>
          {showMedia ? (
            <div className="seller-media-settings__fields">
              <div className="seller-media-field">
                <div className="seller-media-field__header">
                  <span className="seller-media-field__title">Logo shop</span>
                  <span className="seller-media-field__hint">Ảnh vuông hiển thị đại diện</span>
                </div>
                {logoPreview ? (
                  <div className="seller-media-preview-box">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={logoPreview} alt="Xem trước logo" className="seller-media-preview-img seller-media-preview-img--logo" />
                    <div className="seller-media-preview-meta">
                      <strong>{logoFile ? logoFile.name : "Logo shop đã lưu"}</strong>
                      <small>{logoFile ? `${(logoFile.size / 1024).toFixed(0)} KB · Sẵn sàng lưu` : "Đang kích hoạt"}</small>
                    </div>
                    <label className="seller-media-btn">
                      <UploadSimple size={16} weight="bold" />
                      <span>Đổi ảnh</span>
                      <input
                        name="logo"
                        aria-label="Logo shop"
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={pending}
                        className="sr-only"
                        onChange={(e) => handleLogoSelect(e.target.files?.[0])}
                      />
                    </label>
                  </div>
                ) : merchant?.data.logoMediaId ? (
                  <div className="seller-media-preview-box">
                    <div
                      style={{
                        width: "60px",
                        height: "60px",
                        borderRadius: "12px",
                        background: "#ecfdf5",
                        border: "1px solid #a7f3d0",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        color: "#047857",
                        flexShrink: 0,
                      }}
                    >
                      <CheckCircle size={28} weight="fill" />
                    </div>
                    <div className="seller-media-preview-meta">
                      <strong>Logo shop đã lưu trên hệ thống</strong>
                      <small>Mã tài nguyên: {merchant.data.logoMediaId.slice(0, 8)}… · Đang kích hoạt</small>
                    </div>
                    <label className="seller-media-btn">
                      <UploadSimple size={16} weight="bold" />
                      <span>Đổi ảnh</span>
                      <input
                        name="logo"
                        aria-label="Logo shop"
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={pending}
                        className="sr-only"
                        onChange={(e) => handleLogoSelect(e.target.files?.[0])}
                      />
                    </label>
                  </div>
                ) : (
                  <label className="seller-media-dropzone">
                    <UploadSimple size={24} weight="duotone" className="seller-media-dropzone__icon" />
                    <span className="seller-media-dropzone__label">Chọn ảnh Logo shop</span>
                    <small className="seller-media-dropzone__desc">JPEG, PNG hoặc WebP · Tối đa 10 MB</small>
                    <input
                      name="logo"
                      aria-label="Logo shop"
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      disabled={pending}
                      className="sr-only"
                      onChange={(e) => handleLogoSelect(e.target.files?.[0])}
                    />
                  </label>
                )}
              </div>

              <div className="seller-media-field">
                <div className="seller-media-field__header">
                  <span className="seller-media-field__title">Ảnh bìa</span>
                  <span className="seller-media-field__hint">Banner đầu trang và thẻ shop</span>
                </div>
                {coverPreview ? (
                  <div className="seller-media-preview-box">
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img src={coverPreview} alt="Xem trước ảnh bìa" className="seller-media-preview-img seller-media-preview-img--cover" />
                    <div className="seller-media-preview-meta">
                      <strong>{coverFile ? coverFile.name : "Ảnh bìa đã lưu"}</strong>
                      <small>{coverFile ? `${(coverFile.size / 1024).toFixed(0)} KB · Sẵn sàng lưu` : "Đang kích hoạt"}</small>
                    </div>
                    <label className="seller-media-btn">
                      <UploadSimple size={16} weight="bold" />
                      <span>Đổi ảnh</span>
                      <input
                        name="cover"
                        aria-label="Ảnh bìa"
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={pending}
                        className="sr-only"
                        onChange={(e) => handleCoverSelect(e.target.files?.[0])}
                      />
                    </label>
                  </div>
                ) : merchant?.data.coverMediaId ? (
                  <div className="seller-media-preview-box">
                    <div
                      style={{
                        width: "90px",
                        height: "50px",
                        borderRadius: "8px",
                        background: "#ecfdf5",
                        border: "1px solid #a7f3d0",
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "center",
                        color: "#047857",
                        flexShrink: 0,
                      }}
                    >
                      <CheckCircle size={24} weight="fill" />
                    </div>
                    <div className="seller-media-preview-meta">
                      <strong>Ảnh bìa đã lưu trên hệ thống</strong>
                      <small>Mã tài nguyên: {merchant.data.coverMediaId.slice(0, 8)}… · Đang kích hoạt</small>
                    </div>
                    <label className="seller-media-btn">
                      <UploadSimple size={16} weight="bold" />
                      <span>Đổi ảnh</span>
                      <input
                        name="cover"
                        aria-label="Ảnh bìa"
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        disabled={pending}
                        className="sr-only"
                        onChange={(e) => handleCoverSelect(e.target.files?.[0])}
                      />
                    </label>
                  </div>
                ) : (
                  <label className="seller-media-dropzone">
                    <UploadSimple size={24} weight="duotone" className="seller-media-dropzone__icon" />
                    <span className="seller-media-dropzone__label">Chọn ảnh bìa shop</span>
                    <small className="seller-media-dropzone__desc">JPEG, PNG hoặc WebP · Tối đa 10 MB</small>
                    <input
                      name="cover"
                      aria-label="Ảnh bìa"
                      type="file"
                      accept="image/jpeg,image/png,image/webp"
                      disabled={pending}
                      className="sr-only"
                      onChange={(e) => handleCoverSelect(e.target.files?.[0])}
                    />
                  </label>
                )}
              </div>
            </div>
          ) : null}
        </section>
      ) : null}

      {uploadStatus ? <p role="status" className="seller-upload-status">{uploadStatus}</p> : null}
      {message ? (
        <p
          role={message.startsWith("Đã") ? "status" : "alert"}
          style={{
            padding: "10px 14px",
            borderRadius: "8px",
            background: message.startsWith("Đã") ? "#ecfdf5" : "#fef2f2",
            color: message.startsWith("Đã") ? "#047857" : "#b91c1c",
            border: `1px solid ${message.startsWith("Đã") ? "#a7f3d0" : "#fecaca"}`,
            fontSize: "13px",
            fontWeight: 600,
            display: "flex",
            alignItems: "center",
            gap: "8px",
            margin: "12px 0",
          }}
        >
          {message.startsWith("Đã") ? <CheckCircle size={18} weight="fill" /> : null}
          <span>{message}</span>
        </p>
      ) : null}
      {message?.startsWith("Dữ liệu") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending} style={{ display: "inline-flex", alignItems: "center", justifyContent: "center", gap: "6px" }}>
        {editing ? <FloppyDisk size={16} weight="bold" /> : <Storefront size={16} weight="bold" />}
        <span>{pending ? (uploadStatus || "Đang lưu…") : editing ? "Lưu thay đổi" : "Tạo cửa hàng"}</span>
      </button>
    </form>
  );
}
