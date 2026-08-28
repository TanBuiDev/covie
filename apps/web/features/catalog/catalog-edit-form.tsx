"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { CovieApiError } from "@/lib/api/api-error";
import { transitionCatalogItem, updateCatalogItem, updateCatalogLocationOffer } from "./catalog-api";

type Detail = components["schemas"]["CatalogItemManagementResponse"];
type Offer = components["schemas"]["LocationOfferResponse"];

export function CatalogEditForm({ merchantId, item }: { merchantId: string; item: Detail }) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();

  function handleFailure(cause: unknown) {
    setMessage(cause instanceof CovieApiError && cause.code === "RESOURCE_VERSION_CONFLICT"
      ? "Nội dung đã thay đổi ở nơi khác. Trang sẽ tải lại để bạn kiểm tra phiên bản mới."
      : cause instanceof Error ? cause.message : "Không thể lưu thay đổi.");
    router.refresh();
  }

  async function saveItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!item.id || item.version == null || pending) return;
    const form = new FormData(event.currentTarget);
    setPending(true);
    setMessage(undefined);
    try {
      await updateCatalogItem(merchantId, item.id, item.version, {
        name: String(form.get("name") ?? "").trim(),
        description: String(form.get("description") ?? "").trim() || undefined,
      });
      setMessage("Đã lưu thông tin nội dung.");
      router.refresh();
    } catch (cause) {
      handleFailure(cause);
    } finally {
      setPending(false);
    }
  }

  async function saveOffer(offer: Offer, formElement: HTMLFormElement) {
    if (!item.id || !offer.id || offer.version == null || pending) return;
    const form = new FormData(formElement);
    setPending(true);
    setMessage(undefined);
    try {
      await updateCatalogLocationOffer(merchantId, item.id, offer.id, offer.version, {
        price: Number(form.get("price")),
        available: form.get("available") === "on",
        inventoryMode: offer.inventoryMode,
        preparationTimeMinutes: Number(form.get("preparationTimeMinutes")),
      });
      setMessage("Đã lưu giá và trạng thái nhận đơn.");
      router.refresh();
    } catch (cause) {
      handleFailure(cause);
    } finally {
      setPending(false);
    }
  }

  async function transition(action: "publish" | "pause" | "activate" | "archive") {
    if (!item.id || item.version == null || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await transitionCatalogItem(merchantId, item.id, item.version, action);
      router.refresh();
    } catch (cause) {
      handleFailure(cause);
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="seller-stack" aria-busy={pending}>
      <header>
        <p className="eyebrow">Catalog / Chỉnh sửa</p>
        <h1>{item.name ?? "Nội dung catalog"}</h1>
        <p className="seller-muted">Trạng thái: {item.listingStatus ?? "Đang cập nhật"} · Kiểm duyệt: {item.moderationStatus ?? "Đang cập nhật"}</p>
      </header>

      <form className="seller-catalog-form" onSubmit={saveItem}>
        <label>Tên<input name="name" required maxLength={240} defaultValue={item.name ?? ""} disabled={pending} /></label>
        <label>Mô tả<textarea name="description" maxLength={4000} defaultValue={item.description ?? ""} disabled={pending} /></label>
        <button type="submit" disabled={pending}>Lưu thông tin</button>
      </form>

      <section className="seller-stack" aria-label="Giá và trạng thái tại điểm bán">
        <h2>Giá và trạng thái tại điểm bán</h2>
        {(item.locationOffers ?? []).map((offer, index) => offer.id ? (
          <form className="seller-catalog-form" key={offer.id} onSubmit={(event) => {
            event.preventDefault();
            void saveOffer(offer, event.currentTarget);
          }}>
            <strong>Điểm bán {index + 1}</strong>
            <label>Giá<input name="price" type="number" min="0" step="1" required defaultValue={offer.price ?? 0} disabled={pending} /></label>
            <label>Thời gian chuẩn bị (phút)<input name="preparationTimeMinutes" type="number" min="0" step="1" defaultValue={offer.preparationTimeMinutes ?? 0} disabled={pending} /></label>
            <label className="seller-check-row"><input name="available" type="checkbox" defaultChecked={offer.available} disabled={pending} /><span>Đang nhận đơn</span></label>
            <button type="submit" disabled={pending}>Lưu điểm bán</button>
          </form>
        ) : null)}
      </section>

      <div className="seller-row__actions" aria-label="Vòng đời nội dung">
        {item.lifecycle?.canPublish ? <button disabled={pending} onClick={() => void transition("publish")}>Đăng nội dung</button> : null}
        {item.lifecycle?.canPause ? <button disabled={pending} onClick={() => void transition("pause")}>Tạm dừng</button> : null}
        {item.lifecycle?.canActivate ? <button disabled={pending} onClick={() => void transition("activate")}>Kích hoạt lại</button> : null}
        {item.lifecycle?.canArchive ? <button className="button-danger" disabled={pending} onClick={() => void transition("archive")}>Lưu trữ</button> : null}
      </div>
      {message ? <p role="status">{message}</p> : null}
    </section>
  );
}
