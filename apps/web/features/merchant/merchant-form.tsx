"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
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
  return (
    <div className="seller-row-list">
      {merchants.map((merchant) => {
        if (!merchant.id) return null;
        const status = merchantStatusPresentation(merchant.status);
        return (
          <Link className="seller-row" key={merchant.id} href={`/seller/${encodeURIComponent(merchant.id)}`}>
            <div>
              <strong>{merchant.name ?? "Cửa hàng"}</strong>
              <small>{merchantRoleCopy(merchant.currentUserRole)}</small>
            </div>
            <StatusChip label={status.label} tone={status.tone} />
          </Link>
        );
      })}
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

      <label>
        {editing ? "Tên shop" : "Tên cửa hàng"}
        <input name="name" required disabled={pending} defaultValue={merchant?.data.name ?? ""} autoComplete="organization" />
        {editing ? <small>Tên hiển thị công khai</small> : null}
      </label>
      <label>
        {editing ? "Giới thiệu shop" : "Mô tả"}
        <textarea name="description" maxLength={4000} disabled={pending} defaultValue={merchant?.data.description ?? ""} />
        {editing ? <small>Hiển thị trên trang shop công khai</small> : null}
      </label>
      {message ? <p role={message.startsWith("Đã") ? "status" : "alert"}>{message}</p> : null}
      {message?.startsWith("Dữ liệu") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Tạo cửa hàng"}</button>
    </form>
  );
}
