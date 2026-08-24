"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { applyFieldErrors } from "@/lib/forms/apply-field-errors";
import { createSavedAddress, updateSavedAddress } from "./address-api";

type Address = components["schemas"]["UserAddressResponse"];
type AddressField = "label" | "recipientName" | "phone" | "addressText" | "latitude" | "longitude" | "note";

const fieldMap: Record<string, AddressField> = {
  label: "label",
  recipientName: "recipientName",
  phone: "phone",
  addressText: "addressText",
  latitude: "latitude",
  longitude: "longitude",
  note: "note",
};

export function AddressForm({ address, onSaved }: { address?: Address; onSaved?: () => void }) {
  const [message, setMessage] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<AddressField, string>>>({});
  const editing = Boolean(address?.id);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFieldErrors({});
    const form = new FormData(event.currentTarget);
    const latitude = Number(form.get("latitude"));
    const longitude = Number(form.get("longitude"));

    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Vĩ độ phải từ -90 đến 90 và kinh độ phải từ -180 đến 180.");
      return;
    }

    const input = {
      label: String(form.get("label")).trim(),
      addressText: String(form.get("addressText")).trim(),
      latitude,
      longitude,
      pinSource: "USER_ADJUSTED" as const,
      recipientName: String(form.get("recipientName") || "").trim() || undefined,
      phone: String(form.get("phone") || "").trim() || undefined,
      note: String(form.get("note") || "").trim() || undefined,
      defaultAddress: form.get("defaultAddress") === "on",
    };

    try {
      if (editing && address?.id) await updateSavedAddress(address.id, input);
      else await createSavedAddress(input);
      (onSaved ?? (() => window.location.reload()))();
    } catch (error) {
      if (error instanceof CovieApiError && error.fields) {
        const next: Partial<Record<AddressField, string>> = {};
        const formErrors = applyFieldErrors(error.fields, fieldMap, (field, fieldError) => {
          next[field] = fieldError.message;
        });
        setFieldErrors(next);
        if (formErrors.length) setMessage("Một số dữ liệu địa chỉ chưa hợp lệ. Vui lòng kiểm tra lại.");
        return;
      }
      setMessage("Không thể lưu địa chỉ. Vui lòng thử lại.");
    }
  }

  const errorFor = (field: AddressField) => fieldErrors[field] ? `${field}-error` : undefined;

  return (
    <form onSubmit={submit}>
      <h2>{editing ? "Chỉnh sửa địa chỉ" : "Thêm địa chỉ"}</h2>
      <label>
        Tên gợi nhớ
        <input name="label" required defaultValue={address?.label ?? ""} aria-describedby={errorFor("label")} />
      </label>
      {fieldErrors.label ? <p id="label-error" role="alert">{fieldErrors.label}</p> : null}

      <label>
        Người nhận
        <input name="recipientName" defaultValue={address?.recipientName ?? ""} aria-describedby={errorFor("recipientName")} />
      </label>
      {fieldErrors.recipientName ? <p id="recipientName-error" role="alert">{fieldErrors.recipientName}</p> : null}

      <label>
        Số điện thoại
        <input name="phone" inputMode="tel" defaultValue={address?.phone ?? ""} aria-describedby={errorFor("phone")} />
      </label>
      {fieldErrors.phone ? <p id="phone-error" role="alert">{fieldErrors.phone}</p> : null}

      <label>
        Địa chỉ
        <textarea name="addressText" required defaultValue={address?.addressText ?? ""} aria-describedby={errorFor("addressText")} />
      </label>
      {fieldErrors.addressText ? <p id="addressText-error" role="alert">{fieldErrors.addressText}</p> : null}

      <label>
        Vĩ độ
        <input name="latitude" type="number" step="any" required defaultValue={address?.latitude ?? ""} aria-describedby={errorFor("latitude")} />
      </label>
      {fieldErrors.latitude ? <p id="latitude-error" role="alert">{fieldErrors.latitude}</p> : null}

      <label>
        Kinh độ
        <input name="longitude" type="number" step="any" required defaultValue={address?.longitude ?? ""} aria-describedby={errorFor("longitude")} />
      </label>
      {fieldErrors.longitude ? <p id="longitude-error" role="alert">{fieldErrors.longitude}</p> : null}

      <label>
        Ghi chú
        <textarea name="note" defaultValue={address?.note ?? ""} aria-describedby={errorFor("note")} />
      </label>
      {fieldErrors.note ? <p id="note-error" role="alert">{fieldErrors.note}</p> : null}

      <label>
        <input name="defaultAddress" type="checkbox" defaultChecked={address?.defaultAddress ?? false} /> Đặt làm địa chỉ mặc định
      </label>

      {message ? <p role="alert">{message}</p> : null}
      <button type="submit">{editing ? "Lưu thay đổi" : "Lưu địa chỉ"}</button>
    </form>
  );
}
