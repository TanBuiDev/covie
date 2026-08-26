"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { createLocation, updateLocation } from "./merchant-api";

type Location = components["schemas"]["MerchantLocationResponse"];

export function LocationForm({ merchantId, location, onSaved, onReload }: {
  merchantId: string;
  location?: Etagged<Location>;
  onSaved?: () => void;
  onReload?: () => void;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const editing = Boolean(location?.data.id);
  const reload = onReload ?? (() => router.refresh());
  const saved = onSaved ?? (() => router.refresh());

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const latitude = Number(form.get("latitude"));
    const longitude = Number(form.get("longitude"));
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Tọa độ chưa hợp lệ. API hiện yêu cầu latitude/longitude để lưu điểm bán.");
      return;
    }

    const input: components["schemas"]["MerchantLocationUpsertRequest"] = {
      name: String(form.get("name") ?? "").trim(),
      addressText: String(form.get("addressText") ?? "").trim(),
      locationAreaId: location?.data.locationAreaId,
      latitude,
      longitude,
      pinSource: location?.data.pinSource ?? "USER_ADJUSTED",
      timezone: String(form.get("timezone") ?? "").trim(),
      pickupEnabled: form.get("pickupEnabled") === "on",
      sellerDeliveryEnabled: form.get("sellerDeliveryEnabled") === "on",
      serviceEnabled: form.get("serviceEnabled") === "on",
      minimumDeliveryOrder: location?.data.minimumDeliveryOrder ?? 0,
      currency: location?.data.currency ?? "VND",
    };

    setPending(true);
    setMessage(undefined);
    try {
      if (editing && location?.data.id) await updateLocation(merchantId, location.data.id, location.etag, input);
      else await createLocation(merchantId, input);
      saved();
      if (!editing) router.push(`/seller/${encodeURIComponent(merchantId)}/locations`);
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Điểm bán đã được thay đổi ở nơi khác. Hãy tải dữ liệu mới trước khi lưu lại.");
      } else {
        setMessage("Không thể lưu điểm bán. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="seller-location-form" onSubmit={submit} aria-busy={pending}>
      <label>
        Tên điểm bán <span aria-hidden="true">*</span>
        <input name="name" required maxLength={150} disabled={pending} defaultValue={location?.data.name ?? ""} />
        <small>Tên giúp bạn phân biệt điểm bán này</small>
      </label>
      <label>
        Địa chỉ <span aria-hidden="true">*</span>
        <input name="addressText" required maxLength={1000} disabled={pending} defaultValue={location?.data.addressText ?? ""} />
        <small>Mỗi điểm bán được quản lý riêng.</small>
      </label>
      <label>
        Múi giờ <span aria-hidden="true">*</span>
        <select name="timezone" required disabled={pending} defaultValue={location?.data.timezone ?? "Asia/Ho_Chi_Minh"}>
          <option value="Asia/Ho_Chi_Minh">Asia/Ho_Chi_Minh</option>
        </select>
        <small>Dùng cho giờ vận hành và deadline</small>
      </label>

      <label className="seller-switch-row">
        <input name="pickupEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.pickupEnabled ?? true} />
        <span>Cho phép nhận tại chỗ</span>
      </label>
      <label className="seller-switch-row">
        <input name="sellerDeliveryEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.sellerDeliveryEnabled ?? false} />
        <span>Cho phép giao đến địa chỉ khách</span>
      </label>
      <label className="seller-switch-row">
        <input name="serviceEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.serviceEnabled ?? false} />
        <span>Có phục vụ dịch vụ tại khu vực khách</span>
      </label>

      <details className="seller-technical-fields" open={!editing}>
        <summary>Vị trí kỹ thuật</summary>
        <p className="seller-muted">Figma không yêu cầu nhập tọa độ, nhưng API V1 hiện bắt buộc latitude/longitude và chưa có geocode/pin-picker contract. Không được tự điền tọa độ giả.</p>
        <div className="seller-grid seller-grid--two">
          <label>
            Vĩ độ
            <input name="latitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.latitude ?? ""} />
          </label>
          <label>
            Kinh độ
            <input name="longitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.longitude ?? ""} />
          </label>
        </div>
      </details>

      {message ? <p role="alert">{message}</p> : null}
      {message?.startsWith("Điểm bán đã") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu điểm bán" : "Thêm điểm bán"}</button>
    </form>
  );
}
