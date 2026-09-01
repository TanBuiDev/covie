"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { CovieApiError } from "@/lib/api/api-error";
import { applyFieldErrors } from "@/lib/forms/apply-field-errors";
import { createSavedAddress, updateSavedAddress } from "./address-api";
import styles from "./address-form.module.css";

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

export function AddressForm({ address }: { address?: Address }) {
  const router = useRouter();
  const { location } = useBrowseLocation();
  const [message, setMessage] = useState<string>();
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<AddressField, string>>>({});
  const editing = Boolean(address?.id);
  const initialCoordinates = address?.latitude != null && address.longitude != null
    ? { latitude: address.latitude, longitude: address.longitude }
    : location?.kind === "coordinates" ? { latitude: location.lat, longitude: location.lng } : undefined;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFieldErrors({});
    const form = new FormData(event.currentTarget);
    const latitude = Number(form.get("latitude"));
    const longitude = Number(form.get("longitude"));

    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Vị trí chưa hợp lệ. Hãy chọn khu vực ở thanh điều hướng hoặc nhập tọa độ thủ công.");
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
      router.push("/me/addresses");
      router.refresh();
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
  const title = editing ? "Chỉnh sửa địa chỉ" : "Thêm địa chỉ";

  return (
    <section className={styles.screen} data-consumer-focus>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button>
        <h1>{title}</h1>
      </header>
      <div className={styles.content}>
        <h1 className={styles.desktopTitle}>{title}</h1>
        <form className={styles.form} onSubmit={submit}>
          <h2>{title}</h2>

          <label className={styles.field}>
            <span>Nhãn <em>*</em></span>
            <input name="label" required defaultValue={address?.label ?? ""} aria-describedby={errorFor("label")} />
            <small>Ví dụ: Nhà, Công ty</small>
          </label>
          {fieldErrors.label ? <p className={styles.fieldError} id="label-error" role="alert">{fieldErrors.label}</p> : null}

          <label className={styles.field}>
            <span>Người nhận <em>*</em></span>
            <input name="recipientName" required defaultValue={address?.recipientName ?? ""} aria-describedby={errorFor("recipientName")} />
            <small>Tên người nhận</small>
          </label>
          {fieldErrors.recipientName ? <p className={styles.fieldError} id="recipientName-error" role="alert">{fieldErrors.recipientName}</p> : null}

          <label className={styles.field}>
            <span>Số điện thoại <em>*</em></span>
            <input name="phone" required inputMode="tel" defaultValue={address?.phone ?? ""} aria-describedby={errorFor("phone")} />
            <small>Số liên hệ khi giao hàng</small>
          </label>
          {fieldErrors.phone ? <p className={styles.fieldError} id="phone-error" role="alert">{fieldErrors.phone}</p> : null}

          <label className={styles.field}>
            <span>Địa chỉ <em>*</em></span>
            <input name="addressText" required defaultValue={address?.addressText ?? ""} aria-describedby={errorFor("addressText")} />
            <small>Nhập địa chỉ đầy đủ</small>
          </label>
          {fieldErrors.addressText ? <p className={styles.fieldError} id="addressText-error" role="alert">{fieldErrors.addressText}</p> : null}

          <label className={styles.field}>
            <span>Ghi chú</span>
            <textarea name="note" defaultValue={address?.note ?? ""} aria-describedby={errorFor("note")} />
            <small>Không bắt buộc</small>
          </label>
          {fieldErrors.note ? <p className={styles.fieldError} id="note-error" role="alert">{fieldErrors.note}</p> : null}

          <label className={styles.field}>
            <span>Nguồn vị trí</span>
            <select aria-label="Nguồn vị trí" defaultValue="USER_ADJUSTED" disabled>
              <option value="USER_ADJUSTED">Chọn thủ công</option>
            </select>
          </label>

          {initialCoordinates ? (
            <>
              <input type="hidden" name="latitude" value={initialCoordinates.latitude} />
              <input type="hidden" name="longitude" value={initialCoordinates.longitude} />
              <p className={styles.sourceNote}>Tọa độ được lấy từ vị trí đang chọn và chỉ gửi khi bạn lưu địa chỉ.</p>
            </>
          ) : (
            <div className={styles.coordinateGrid}>
              <label className={styles.field}>
                <span>Vĩ độ <em>*</em></span>
                <input name="latitude" type="number" step="any" required aria-describedby={errorFor("latitude")} />
              </label>
              <label className={styles.field}>
                <span>Kinh độ <em>*</em></span>
                <input name="longitude" type="number" step="any" required aria-describedby={errorFor("longitude")} />
              </label>
            </div>
          )}
          {fieldErrors.latitude ? <p className={styles.fieldError} id="latitude-error" role="alert">{fieldErrors.latitude}</p> : null}
          {fieldErrors.longitude ? <p className={styles.fieldError} id="longitude-error" role="alert">{fieldErrors.longitude}</p> : null}

          <label className={styles.switch}>
            <input name="defaultAddress" type="checkbox" defaultChecked={address?.defaultAddress ?? false} /> Đặt làm địa chỉ mặc định
          </label>

          {message ? <p className={styles.message} role="alert">{message}</p> : null}
          <button className={styles.submit} type="submit">{editing ? "Lưu thay đổi" : "Lưu địa chỉ"}</button>
        </form>
      </div>
    </section>
  );
}
