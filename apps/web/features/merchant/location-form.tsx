"use client";

import type { components } from "@covie/api-client";
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
  const [message, setMessage] = useState<string>();
  const editing = Boolean(location?.data.id);
  const reload = onReload ?? (() => window.location.reload());
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const input: components["schemas"]["MerchantLocationUpsertRequest"] = {
      name: String(form.get("name")).trim(), addressText: String(form.get("addressText")).trim(),
      latitude: Number(form.get("latitude")), longitude: Number(form.get("longitude")), pinSource: "USER_ADJUSTED",
      timezone: String(form.get("timezone")).trim(), pickupEnabled: form.get("pickupEnabled") === "on",
      sellerDeliveryEnabled: form.get("sellerDeliveryEnabled") === "on", serviceEnabled: form.get("serviceEnabled") === "on",
      minimumDeliveryOrder: Number(form.get("minimumDeliveryOrder")) || 0, currency: "VND",
    };
    try {
      if (editing && location?.data.id) await updateLocation(merchantId, location.data.id, location.etag, input);
      else await createLocation(merchantId, input);
      (onSaved ?? (() => window.location.reload()))();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Dữ liệu đã được thay đổi ở nơi khác. Hãy tải lại trước khi tiếp tục."); return;
      }
      setMessage("Không thể lưu địa điểm. Vui lòng thử lại.");
    }
  }
  return <form onSubmit={submit}>
    <h2>{editing ? "Chỉnh sửa địa điểm" : "Thêm địa điểm"}</h2>
    <label>Tên địa điểm <input name="name" required defaultValue={location?.data.name ?? ""} /></label>
    <label>Địa chỉ <textarea name="addressText" required defaultValue={location?.data.addressText ?? ""} /></label>
    <label>Vĩ độ <input name="latitude" type="number" step="any" required defaultValue={location?.data.latitude ?? ""} /></label>
    <label>Kinh độ <input name="longitude" type="number" step="any" required defaultValue={location?.data.longitude ?? ""} /></label>
    <label>Múi giờ <input name="timezone" required defaultValue={location?.data.timezone ?? "Asia/Ho_Chi_Minh"} /></label>
    <label><input name="pickupEnabled" type="checkbox" defaultChecked={location?.data.pickupEnabled ?? true} /> Nhận tại cửa hàng</label>
    <label><input name="sellerDeliveryEnabled" type="checkbox" defaultChecked={location?.data.sellerDeliveryEnabled ?? false} /> Tự giao hàng</label>
    <label><input name="serviceEnabled" type="checkbox" defaultChecked={location?.data.serviceEnabled ?? false} /> Có dịch vụ</label>
    <label>Đơn tối thiểu <input name="minimumDeliveryOrder" type="number" min="0" defaultValue={location?.data.minimumDeliveryOrder ?? 0} /></label>
    {message && <p role="alert">{message}</p>}
    {message?.startsWith("Dữ liệu") && <button type="button" onClick={reload}>Tải lại dữ liệu</button>}
    <button type="submit">{editing ? "Lưu địa điểm" : "Tạo địa điểm"}</button>
  </form>;
}
