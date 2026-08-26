"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { createLocation, updateLocation } from "./merchant-api";

export function LocationForm({ merchantId, location, onSaved, onReload }: {
  merchantId: string;
  location?: Etagged<components["schemas"]["MerchantLocationResponse"]>;
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
    const input: components["schemas"]["MerchantLocationUpsertRequest"] = {
      name: String(form.get("name") ?? "").trim(),
      addressText: String(form.get("addressText") ?? "").trim(),
      latitude: Number(form.get("latitude")),
      longitude: Number(form.get("longitude")),
      pinSource: "USER_ADJUSTED",
      timezone: String(form.get("timezone") ?? "").trim(),
      pickupEnabled: form.get("pickupEnabled") === "on",
      sellerDeliveryEnabled: form.get("sellerDeliveryEnabled") === "on",
      serviceEnabled: form.get("serviceEnabled") === "on",
      minimumDeliveryOrder: Math.max(0, Math.trunc(Number(form.get("minimumDeliveryOrder")) || 0)),
      currency: "VND",
    };

    setPending(true);
    setMessage(undefined);
    try {
      if (editing && location?.data.id) await updateLocation(merchantId, location.data.id, location.etag, input);
      else await createLocation(merchantId, input);
      saved();
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
    <form onSubmit={submit} aria-busy={pending}>
      <h2>{editing ? "Thông tin điểm bán" : "Thêm điểm bán"}</h2>
      <label>Tên điểm bán <input name="name" required disabled={pending} defaultValue={location?.data.name ?? ""} /></label>
      <label>Địa chỉ <textarea name="addressText" required disabled={pending} defaultValue={location?.data.addressText ?? ""} /></label>
      <div className="seller-grid seller-grid--two">
        <label>Vĩ độ <input name="latitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.latitude ?? ""} /></label>
        <label>Kinh độ <input name="longitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.longitude ?? ""} /></label>
      </div>
      <label>Múi giờ <input name="timezone" required disabled={pending} defaultValue={location?.data.timezone ?? "Asia/Ho_Chi_Minh"} /></label>
      <label><input name="pickupEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.pickupEnabled ?? true} /> Nhận tại cửa hàng</label>
      <label><input name="sellerDeliveryEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.sellerDeliveryEnabled ?? false} /> Tự giao hàng</label>
      <label><input name="serviceEnabled" type="checkbox" disabled={pending} defaultChecked={location?.data.serviceEnabled ?? false} /> Có dịch vụ</label>
      <label>
        Đơn giao tối thiểu (VND)
        <input name="minimumDeliveryOrder" type="number" inputMode="numeric" min="0" step="1" disabled={pending} defaultValue={location?.data.minimumDeliveryOrder ?? 0} />
      </label>
      {message ? <p role="alert">{message}</p> : null}
      {message?.startsWith("Điểm bán đã") ? (
        <button type="button" disabled={pending} onClick={reload}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu điểm bán" : "Tạo điểm bán"}</button>
    </form>
  );
}
