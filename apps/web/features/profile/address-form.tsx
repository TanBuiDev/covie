"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { CovieApiError } from "@/lib/api/api-error";
import { applyFieldErrors } from "@/lib/forms/apply-field-errors";
import { createSavedAddress, listSavedAddressesClient, updateSavedAddress } from "./address-api";
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
  const [saving, setSaving] = useState(false);
  const savePending = useRef(false);
  const [note, setNote] = useState(address?.note ?? "");
  const [addressText,setAddressText] = useState(address?.addressText ?? "");
  const [manualLatitude,setManualLatitude] = useState("");
  const [manualLongitude,setManualLongitude] = useState("");
  const [pointSource,setPointSource] = useState<"CURRENT" | "GPS" | "SAVED" | "MANUAL">(address?.id || location ? "CURRENT" : "GPS");
  const [gpsPoint,setGpsPoint] = useState<{latitude:number;longitude:number}>();
  const [gpsPending,setGpsPending] = useState(false);
  const gpsGeneration = useRef(0);
  const savedGeneration = useRef(0);
  useEffect(() => () => {
    gpsGeneration.current++;
    savedGeneration.current++;
  }, []);
  const [savedPoints,setSavedPoints] = useState<Address[]>([]);
  const [selectedSavedId,setSelectedSavedId] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<AddressField, string>>>({});
  const editing = Boolean(address?.id);
  const initialCoordinates = (() => {
    if (pointSource === "GPS") return gpsPoint;
    if (pointSource === "SAVED") {
      const selected = savedPoints.find(item=>item.id === selectedSavedId);
      return selected?.latitude != null && selected.longitude != null ? {latitude:selected.latitude,longitude:selected.longitude} : undefined;
    }
    if (pointSource === "MANUAL") return undefined;
    if (address?.latitude != null && address.longitude != null) return { latitude: address.latitude, longitude: address.longitude };
    if (location?.kind === "coordinates") return { latitude: location.lat, longitude: location.lng };
    return undefined;
  })();
  const pinKey = JSON.stringify([addressText.trim(), initialCoordinates?.latitude, initialCoordinates?.longitude,
    pointSource, selectedSavedId, location?.kind === "address" ? location.addressId : undefined, manualLatitude, manualLongitude]);
  const [confirmedPinKey,setConfirmedPinKey] = useState<string | undefined>(address?.id && initialCoordinates ? pinKey : undefined);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (savePending.current) return;
    const form = new FormData(event.currentTarget);
    savePending.current = true;
    setSaving(true);
    try {
      await saveAddress(form);
    } finally {
      savePending.current = false;
      setSaving(false);
    }
  }

  async function saveAddress(form: FormData) {
    setMessage(undefined);
    setFieldErrors({});
    let latitude = initialCoordinates?.latitude;
    let longitude = initialCoordinates?.longitude;

    if (pointSource === "CURRENT" && latitude == null && longitude == null && location?.kind === "address") {
      try {
        const selectedAddress = (await listSavedAddressesClient()).find((item) => item.id === location.addressId);
        latitude = selectedAddress?.latitude ?? undefined;
        longitude = selectedAddress?.longitude ?? undefined;
      } catch {
        setMessage("Không thể đọc vị trí từ địa chỉ đã lưu. Vui lòng thử lại hoặc chọn vị trí khác.");
        return;
      }
    }

    const coordinateValue = (value: FormDataEntryValue | null) =>
      typeof value === "string" && value.trim() !== "" ? Number(value) : Number.NaN;
    if (latitude == null) latitude = coordinateValue(form.get("latitude"));
    if (longitude == null) longitude = coordinateValue(form.get("longitude"));

    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Vị trí chưa hợp lệ. Hãy chọn khu vực ở thanh điều hướng hoặc nhập tọa độ thủ công.");
      return;
    }
    if (confirmedPinKey !== pinKey) {
      setMessage("Xác nhận điểm giao hàng cho địa chỉ này trước khi lưu.");
      return;
    }

    const input = {
      label: String(form.get("label")).trim(),
      addressText: String(form.get("addressText")).trim(),
      latitude,
      longitude,
      pinSource: pointSource === "GPS" ? "GPS" as const : "USER_ADJUSTED" as const,
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
        <form className={styles.form} onSubmit={submit} aria-busy={saving}>
          <h2>{title}</h2>

          <label className={styles.field}>
            <span>Nhãn <em>*</em></span>
            <input name="label" required placeholder="Nhà" defaultValue={address?.label ?? ""} aria-invalid={Boolean(fieldErrors.label)} aria-describedby={errorFor("label")} />
            <small className={fieldErrors.label ? styles.errorText : undefined} id={errorFor("label")} role={fieldErrors.label ? "alert" : undefined}>{fieldErrors.label ?? "Ví dụ: Nhà, Công ty"}</small>
          </label>

          <label className={styles.field}>
            <span>Người nhận <em>*</em></span>
            <input name="recipientName" required placeholder="Nguyễn Văn Minh" defaultValue={address?.recipientName ?? ""} aria-invalid={Boolean(fieldErrors.recipientName)} aria-describedby={errorFor("recipientName")} />
            <small className={fieldErrors.recipientName ? styles.errorText : undefined} id={errorFor("recipientName")} role={fieldErrors.recipientName ? "alert" : undefined}>{fieldErrors.recipientName ?? "Tên người nhận"}</small>
          </label>

          <label className={styles.field}>
            <span>Số điện thoại <em>*</em></span>
            <input name="phone" required inputMode="tel" placeholder="0909 123 456" defaultValue={address?.phone ?? ""} aria-invalid={Boolean(fieldErrors.phone)} aria-describedby={errorFor("phone")} />
            <small className={fieldErrors.phone ? styles.errorText : undefined} id={errorFor("phone")} role={fieldErrors.phone ? "alert" : undefined}>{fieldErrors.phone ?? "Số liên hệ khi giao hàng"}</small>
          </label>

          <label className={styles.field}>
            <span>Địa chỉ <em>*</em></span>
            <input name="addressText" required placeholder="S5.03, Vinhomes Grand Park" value={addressText} onChange={event=>{setAddressText(event.target.value);setConfirmedPinKey(undefined);}} aria-invalid={Boolean(fieldErrors.addressText)} aria-describedby={errorFor("addressText")} />
            <small className={fieldErrors.addressText ? styles.errorText : undefined} id={errorFor("addressText")} role={fieldErrors.addressText ? "alert" : undefined}>{fieldErrors.addressText ?? "Nhập địa chỉ đầy đủ"}</small>
          </label>

          <label className={styles.field}>
            <span>Ghi chú</span>
            <textarea
              name="note"
              placeholder="Giao tại sảnh chính"
              value={note}
              onChange={(event) => setNote(event.currentTarget.value)}
              aria-invalid={Boolean(fieldErrors.note)}
              aria-describedby={errorFor("note")}
            />
            <small className={fieldErrors.note ? styles.errorText : undefined} id={errorFor("note")} role={fieldErrors.note ? "alert" : undefined}>{fieldErrors.note ?? "Không bắt buộc"}</small>
          </label>

          <label className={styles.field}>
            <span>Nguồn vị trí</span>
            <select aria-label="Nguồn vị trí" value={pointSource} onChange={event=>{
              const next=event.target.value as typeof pointSource;
              gpsGeneration.current++;setGpsPending(false);setGpsPoint(undefined);
              const generation = ++savedGeneration.current;
              setPointSource(next);setConfirmedPinKey(undefined);setMessage(undefined);
              if(next === "SAVED") {
                setSavedPoints([]);
                void listSavedAddressesClient().then(points => {
                  if (generation === savedGeneration.current) setSavedPoints(points);
                }).catch(() => {
                  if (generation === savedGeneration.current) setMessage("Không thể tải địa chỉ đã lưu. Chọn lại nguồn vị trí để thử lại.");
                });
              }
            }}>
              {(address?.id || location) && <option value="CURRENT">{address?.id ? "Điểm của địa chỉ đang sửa" : "Điểm đang chọn để khám phá"}</option>}
              <option value="GPS">Vị trí hiện tại (GPS)</option>
              <option value="SAVED">Điểm từ địa chỉ đã lưu</option>
              <option value="MANUAL">Nhập tọa độ (nâng cao)</option>
            </select>
          </label>
          {pointSource === "GPS" && <button className={`button-secondary ${styles.locationAction}`} type="button" disabled={gpsPending} onClick={()=>{
            setMessage(undefined);setConfirmedPinKey(undefined);setGpsPoint(undefined);
            if(!navigator.geolocation){setMessage("Thiết bị không hỗ trợ GPS. Hãy chọn điểm từ địa chỉ đã lưu hoặc nhập tọa độ nâng cao.");return;}
            setGpsPending(true);
            const generation=++gpsGeneration.current;
            navigator.geolocation.getCurrentPosition(position=>{
              if(generation !== gpsGeneration.current) return;
              setGpsPoint({latitude:position.coords.latitude,longitude:position.coords.longitude});setGpsPending(false);
            },()=>{if(generation !== gpsGeneration.current) return;setGpsPending(false);setMessage("Không thể lấy vị trí hiện tại. Hãy cho phép truy cập vị trí hoặc chọn nguồn khác.");},
            {enableHighAccuracy:true,timeout:15000,maximumAge:0});
          }}>{gpsPending ? "Đang lấy vị trí…" : "Lấy vị trí hiện tại"}</button>}
          {pointSource === "SAVED" && <label className={styles.field}><span>Địa chỉ lấy điểm</span><select aria-label="Địa chỉ lấy điểm" value={selectedSavedId} onChange={event=>{setSelectedSavedId(event.target.value);setConfirmedPinKey(undefined);}}><option value="">Chọn địa chỉ đã lưu</option>{savedPoints.map(item=><option key={item.id} value={item.id}>{item.label} — {item.addressText}</option>)}</select></label>}

          {initialCoordinates || (pointSource === "CURRENT" && location?.kind === "address") ? (
            <>
              {initialCoordinates ? (
                <>
                  <input type="hidden" name="latitude" value={initialCoordinates.latitude} />
                  <input type="hidden" name="longitude" value={initialCoordinates.longitude} />
                </>
              ) : null}
              <p className={styles.sourceNote}>Điểm giao hàng: {pointSource === "GPS" ? "vị trí GPS vừa lấy" : pointSource === "SAVED" ? savedPoints.find(item=>item.id === selectedSavedId)?.label : address?.id ? "điểm đã lưu của địa chỉ này" : location?.label}. Nội dung địa chỉ không tự xác định điểm giao hàng. Nếu đây không phải nơi nhận hàng, hãy chọn lại vị trí trước khi xác nhận.</p>
            </>
          ) : pointSource === "MANUAL" ? (
            <div className={styles.coordinateGrid}>
              <label className={styles.field}>
                <span>Vĩ độ <em>*</em></span>
                <input name="latitude" type="number" step="any" required value={manualLatitude} onChange={event=>{setManualLatitude(event.target.value);setConfirmedPinKey(undefined);}} aria-describedby={errorFor("latitude")} />
              </label>
              <label className={styles.field}>
                <span>Kinh độ <em>*</em></span>
                <input name="longitude" type="number" step="any" required value={manualLongitude} onChange={event=>{setManualLongitude(event.target.value);setConfirmedPinKey(undefined);}} aria-describedby={errorFor("longitude")} />
              </label>
            </div>
          ) : <p className={styles.sourceNote}>Chọn điểm giao hàng trước khi xác nhận và lưu địa chỉ.</p>}
          {fieldErrors.latitude ? <p className={styles.fieldError} id="latitude-error" role="alert">{fieldErrors.latitude}</p> : null}
          {fieldErrors.longitude ? <p className={styles.fieldError} id="longitude-error" role="alert">{fieldErrors.longitude}</p> : null}

          <label className={styles.switch}>
            <input type="checkbox" checked={confirmedPinKey === pinKey} onChange={event=>setConfirmedPinKey(event.target.checked ? pinKey : undefined)}/> Xác nhận điểm giao hàng cho địa chỉ này
          </label>
          <label className={styles.switch}>
            <input name="defaultAddress" type="checkbox" defaultChecked={address?.defaultAddress ?? false} /> Đặt làm địa chỉ mặc định
          </label>

          {message ? <p className={styles.message} role="alert">{message}</p> : null}
          <button className={styles.submit} type="submit" disabled={saving}>{saving ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Lưu địa chỉ"}</button>
        </form>
      </div>
    </section>
  );
}
