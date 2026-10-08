"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import { ArrowRight, MagnifyingGlass, Sparkle, Storefront } from "@phosphor-icons/react";
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);

    setPending(true);
    setMessage(undefined);
    try {
      let logoMediaId: string | undefined;
      let coverMediaId: string | undefined;
      if (editing) {
        const logo = form.get("logo");
        const cover = form.get("cover");
        if (logo instanceof File && logo.size > 0) {
          const uploaded = await uploadImage(logo, "MERCHANT_LOGO");
          if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
          logoMediaId = uploaded.mediaId;
        }
        if (cover instanceof File && cover.size > 0) {
          const uploaded = await uploadImage(cover, "MERCHANT_COVER");
          if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
          coverMediaId = uploaded.mediaId;
        }
      }

      const input = {
        name: String(form.get("name") ?? "").trim(),
        description: String(form.get("description") ?? "").trim() || undefined,
        ...(logoMediaId ? { logoMediaId } : {}),
        ...(coverMediaId ? { coverMediaId } : {}),
      };

      if (editing && merchant?.data.id) await updateMerchant(merchant.data.id, merchant.etag, input);
      else await createMerchant(input);
      setMessage(editing ? "Đã lưu thay đổi." : "Đã tạo cửa hàng.");
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
    }
  }

  return (
    <form className={editing ? "seller-merchant-form" : undefined} onSubmit={submit} aria-busy={pending}>
      {!editing ? <h2>Tạo cửa hàng</h2> : null}
      {editing ? <h2 className="seller-settings-card-title seller-desktop-only">Hồ sơ shop công khai</h2> : null}

      <label>
        {editing ? "Tên shop" : "Tên cửa hàng"}
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
        {editing ? "Giới thiệu shop" : "Mô tả"}
        <textarea
          name="description"
          maxLength={4000}
          disabled={pending}
          defaultValue={merchant?.data.description ?? ""}
          onChange={(e) => setPreviewDesc(e.target.value)}
        />
        {editing ? <small>Hiển thị trên trang shop công khai</small> : null}
      </label>

      {!editing ? (
        <div className="seller-live-preview" aria-hidden="true">
          <div className="seller-live-preview__header">
            <Sparkle size={15} weight="fill" className="seller-live-preview__sparkle" />
            <span>Xem trước thẻ hiển thị cho cư dân</span>
          </div>
          <div className="seller-live-preview__card">
            <div className="seller-live-preview__icon">
              <Storefront size={26} weight="duotone" />
            </div>
            <div className="seller-live-preview__info">
              <strong className="seller-live-preview__title">
                {previewName.trim() || "Tên cửa hàng của bạn"}
              </strong>
              <p className="seller-live-preview__desc">
                {previewDesc.trim() || "Mô tả món ăn, sản phẩm hoặc dịch vụ nội khu của bạn…"}
              </p>
              <div className="seller-live-preview__tags">
                <span className="seller-live-preview__tag">Cửa hàng mới</span>
                <span className="seller-live-preview__tag">Nội khu CoVie</span>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {editing ? (
        <section className="seller-media-settings" aria-label="Logo và ảnh bìa">
          <strong>Logo & ảnh bìa</strong>
          <p>Dùng ảnh đã tải lên CoVie và thuộc shop này.</p>
          <button className="seller-button--secondary" type="button" disabled={pending} onClick={() => setShowMedia((value) => !value)}>
            {showMedia ? "Đóng quản lý hình ảnh" : "Quản lý hình ảnh"}
          </button>
          {showMedia ? (
            <div className="seller-media-settings__fields">
              <label>
                Logo shop
                <input name="logo" type="file" accept="image/jpeg,image/png,image/webp" disabled={pending} />
              </label>
              <label>
                Ảnh bìa
                <input name="cover" type="file" accept="image/jpeg,image/png,image/webp" disabled={pending} />
              </label>
              <small>JPEG, PNG hoặc WebP; tối đa 10 MB mỗi ảnh.</small>
            </div>
          ) : null}
        </section>
      ) : null}
      {message ? <p role={message.startsWith("Đã") ? "status" : "alert"}>{message}</p> : null}
      {message?.startsWith("Dữ liệu") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Tạo cửa hàng"}</button>
    </form>
  );
}
