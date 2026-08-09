"use client";

import { FormEvent, useState } from "react";
import { createSavedAddress } from "./address-api";

export function AddressForm() {
  const [message, setMessage] = useState<string>();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const latitude = Number(form.get("latitude"));
    const longitude = Number(form.get("longitude"));
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Vĩ độ phải từ -90 đến 90 và kinh độ phải từ -180 đến 180.");
      return;
    }
    try {
      await createSavedAddress({
        label: String(form.get("label")),
        addressText: String(form.get("addressText")),
        latitude,
        longitude,
        pinSource: "USER_ADJUSTED",
        recipientName: String(form.get("recipientName") || ""),
        phone: String(form.get("phone") || ""),
        note: String(form.get("note") || ""),
      });
      window.location.reload();
    } catch {
      setMessage("Không thể lưu địa chỉ. Vui lòng thử lại.");
    }
  }

  return <form onSubmit={submit}>
    <h2>Thêm địa chỉ</h2>
    <label>Tên gợi nhớ <input name="label" required /></label>
    <label>Người nhận <input name="recipientName" /></label>
    <label>Số điện thoại <input name="phone" /></label>
    <label>Địa chỉ <textarea name="addressText" required /></label>
    <label>Vĩ độ <input name="latitude" type="number" step="any" required /></label>
    <label>Kinh độ <input name="longitude" type="number" step="any" required /></label>
    <label>Ghi chú <textarea name="note" /></label>
    {message && <p role="alert">{message}</p>}
    <button type="submit">Lưu địa chỉ</button>
  </form>;
}
