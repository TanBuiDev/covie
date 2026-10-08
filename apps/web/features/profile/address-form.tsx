"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import { CovieApiError } from "@/lib/api/api-error";
import { applyFieldErrors } from "@/lib/forms/apply-field-errors";
import {
  Buildings,
  House,
  Users,
} from "@phosphor-icons/react";
import { createSavedAddress, listSavedAddressesClient, reverseGeocodeAddress, updateSavedAddress } from "./address-api";
import styles from "./address-form.module.css";
import { DeliveryPointMap } from "./delivery-point-map";

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
  const [labelValue, setLabelValue] = useState(address?.label ?? "");
  const [note, setNote] = useState(address?.note ?? "");
  const [addressText, setAddressText] = useState(address?.addressText ?? "");
  const [manualLatitude, setManualLatitude] = useState("");
  const [manualLongitude, setManualLongitude] = useState("");
  const [pointSource, setPointSource] = useState<"CURRENT" | "GPS" | "SAVED" | "MANUAL">(
    address?.id || location ? "CURRENT" : "GPS"
  );
  const [gpsPoint, setGpsPoint] = useState<{ latitude: number; longitude: number }>();
  const [gpsPending, setGpsPending] = useState(false);
  const gpsGeneration = useRef(0);
  const savedGeneration = useRef(0);
  const addressRevision = useRef(0);
  const [gpsSuggestion, setGpsSuggestion] = useState(false);
  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [adjustedPoint, setAdjustedPoint] = useState<{ latitude: number; longitude: number }>();

  useEffect(() => () => {
    gpsGeneration.current++;
    savedGeneration.current++;
  }, []);

  const [savedPoints, setSavedPoints] = useState<Address[]>([]);
  const [selectedSavedId, setSelectedSavedId] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<AddressField, string>>>({});
  const editing = Boolean(address?.id);

  const initialCoordinates = (() => {
    if (adjustedPoint) return adjustedPoint;
    if (pointSource === "GPS") return gpsPoint;
    if (pointSource === "SAVED") {
      const selected = savedPoints.find((item) => item.id === selectedSavedId);
      return selected?.latitude != null && selected.longitude != null
        ? { latitude: selected.latitude, longitude: selected.longitude }
        : undefined;
    }
    if (pointSource === "MANUAL") {
      const latitude = Number(manualLatitude), longitude = Number(manualLongitude);
      return manualLatitude.trim() && manualLongitude.trim() && Number.isFinite(latitude) && latitude >= -90 && latitude <= 90 && Number.isFinite(longitude) && longitude >= -180 && longitude <= 180 ? { latitude, longitude } : undefined;
    }
    if (address?.latitude != null && address.longitude != null)
      return { latitude: address.latitude, longitude: address.longitude };
    if (location?.kind === "coordinates") return { latitude: location.lat, longitude: location.lng };
    return undefined;
  })();


  function requestGpsAddress() {
    setMessage(undefined);
    setGpsPoint(undefined);
    setAdjustedPoint(undefined);
    setGpsSuggestion(false);
    setPointSource("GPS");
    savedGeneration.current++;
    const generation = ++gpsGeneration.current;
    const revision = addressRevision.current;
    if (!navigator.geolocation) {
      setMessage("Thiết bị không hỗ trợ GPS. Hãy chọn điểm từ địa chỉ đã lưu hoặc nhập tọa độ nâng cao.");
      return;
    }
    setGpsPending(true);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        if (generation !== gpsGeneration.current) return;
        const { latitude, longitude } = position.coords;
        setGpsPoint({ latitude, longitude });
        void reverseGeocodeAddress(latitude, longitude)
          .then((suggestion) => {
            if (generation !== gpsGeneration.current || revision !== addressRevision.current) return;
            setAddressText(suggestion.addressText ?? "");
            setGpsSuggestion(true);
          })
          .catch(() => {
            if (generation === gpsGeneration.current)
              setMessage("Đã lấy tọa độ nhưng chưa tra được địa chỉ. Bạn có thể nhập địa chỉ đầy đủ rồi lưu.");
          })
          .finally(() => {
            if (generation === gpsGeneration.current) setGpsPending(false);
          });
      },
      () => {
        if (generation !== gpsGeneration.current) return;
        setGpsPending(false);
        setMessage("Không thể lấy vị trí hiện tại. Hãy cho phép truy cập vị trí hoặc chọn nguồn khác.");
      },
      { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }
    );
  }

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

    if (
      !Number.isFinite(latitude) ||
      latitude < -90 ||
      latitude > 90 ||
      !Number.isFinite(longitude) ||
      longitude < -180 ||
      longitude > 180
    ) {
      setMessage("Vị trí chưa hợp lệ. Hãy chọn khu vực ở thanh điều hướng hoặc nhập tọa độ thủ công.");
      return;
    }

    const input = {
      label: String(form.get("label")).trim(),
      addressText: String(form.get("addressText")).trim(),
      latitude,
      longitude,
      pinSource: pointSource === "GPS" && !adjustedPoint ? ("GPS" as const) : ("USER_ADJUSTED" as const),
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

  const errorFor = (field: AddressField) => (fieldErrors[field] ? `${field}-error` : undefined);
  const title = editing ? "Chỉnh sửa địa chỉ" : "Thêm địa chỉ";

  return (
    <section className={styles.screen} data-consumer-focus>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}>
          <CoVieIcon name="back" />
        </button>
        <h1>{title}</h1>
      </header>

      <div className={styles.content}>
        <div className={styles.headerInfo}>
          <h1 className={styles.desktopTitle}>{title}</h1>
          <p className={styles.desktopSubtitle}>
            {editing
              ? "Cập nhật thông tin người nhận và tọa độ giao hàng đã lưu"
              : "Thêm địa chỉ mới để đặt hàng và sử dụng dịch vụ trong khu dân cư"}
          </p>
        </div>

        <form className={styles.form} onSubmit={submit} aria-busy={saving}>
          <h2>{title}</h2>

          <div className={styles.field}>
            <label htmlFor="address-label-input">
              <span>Nhãn <em>*</em></span>
            </label>
            <input
              id="address-label-input"
              name="label"
              required
              placeholder="Nhà"
              value={labelValue}
              onChange={(e) => setLabelValue(e.target.value)}
              aria-invalid={Boolean(fieldErrors.label)}
              aria-describedby={errorFor("label")}
            />
            <div className={styles.quickChips} role="group" aria-label="Gợi ý nhãn nhanh">
              <button
                type="button"
                className={styles.quickChip}
                onClick={() => setLabelValue("Nhà")}
              >
                <House size={12} weight="bold" />
                <span>Nhà riêng</span>
              </button>
              <button
                type="button"
                className={styles.quickChip}
                onClick={() => setLabelValue("Công ty")}
              >
                <Buildings size={12} weight="bold" />
                <span>Văn phòng / Công ty</span>
              </button>
              <button
                type="button"
                className={styles.quickChip}
                onClick={() => setLabelValue("Người thân")}
              >
                <Users size={12} weight="bold" />
                <span>Người thân</span>
              </button>
            </div>
            <small
              className={fieldErrors.label ? styles.errorText : undefined}
              id={errorFor("label")}
              role={fieldErrors.label ? "alert" : undefined}
            >
              {fieldErrors.label ?? "Ví dụ: Nhà, Công ty"}
            </small>
          </div>

          <div className={styles.formGrid}>
            <label className={styles.field}>
              <span>Người nhận <em>*</em></span>
              <input
                name="recipientName"
                required
                placeholder="Nguyễn Văn Minh"
                defaultValue={address?.recipientName ?? ""}
                aria-invalid={Boolean(fieldErrors.recipientName)}
                aria-describedby={errorFor("recipientName")}
              />
              <small
                className={fieldErrors.recipientName ? styles.errorText : undefined}
                id={errorFor("recipientName")}
                role={fieldErrors.recipientName ? "alert" : undefined}
              >
                {fieldErrors.recipientName ?? "Tên người nhận"}
              </small>
            </label>

            <label className={styles.field}>
              <span>Số điện thoại <em>*</em></span>
              <input
                name="phone"
                required
                inputMode="tel"
                placeholder="0909 123 456"
                defaultValue={address?.phone ?? ""}
                aria-invalid={Boolean(fieldErrors.phone)}
                aria-describedby={errorFor("phone")}
              />
              <small
                className={fieldErrors.phone ? styles.errorText : undefined}
                id={errorFor("phone")}
                role={fieldErrors.phone ? "alert" : undefined}
              >
                {fieldErrors.phone ?? "Số liên hệ khi giao hàng"}
              </small>
            </label>
          </div>

          <fieldset className={styles.locationCard}>
            <legend>Địa chỉ giao hàng</legend>
            <p className={styles.sourceNote}>Chọn nơi nhận hàng, rồi bổ sung số nhà, tòa nhà hoặc căn hộ trong địa chỉ.</p>
          <label className={styles.field}>
            <span>Địa chỉ <em>*</em></span>
            <input
              name="addressText"
              required
              placeholder="S5.03, Vinhomes Grand Park"
              value={addressText}
              onChange={(event) => {
                addressRevision.current++;
                setAddressText(event.target.value);
              }}
              aria-invalid={Boolean(fieldErrors.addressText)}
              aria-describedby={errorFor("addressText")}
            />
            {fieldErrors.addressText && <small
              className={fieldErrors.addressText ? styles.errorText : undefined}
              id={errorFor("addressText")}
              role={fieldErrors.addressText ? "alert" : undefined}
            >
              {fieldErrors.addressText}
            </small>}
          </label>

          {gpsSuggestion && (
            <p className={styles.sourceNote} role="status">
              Địa chỉ gợi ý có thể thiếu số nhà. Hãy kiểm tra và sửa trước khi lưu. ©{" "}
              <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap contributors</a>
            </p>
          )}

          <details className={styles.advanced} open={advancedOpen} onToggle={event => setAdvancedOpen(event.currentTarget.open)}>
            <summary>Nâng cao: chọn nguồn và nhập tọa độ</summary>
            <label className={styles.field}>
              <span>Nguồn vị trí</span>
              <select
                aria-label="Nguồn vị trí"
                value={pointSource}
                onChange={(event) => {
                  const next = event.target.value as typeof pointSource;
                  setAdvancedOpen(true);
                  if (next === "MANUAL") {
                    setManualLatitude(initialCoordinates?.latitude.toString() ?? "");
                    setManualLongitude(initialCoordinates?.longitude.toString() ?? "");
                  }
                  gpsGeneration.current++;
                  setGpsSuggestion(false);
                  setGpsPending(false);
                  setGpsPoint(undefined);
                  setAdjustedPoint(undefined);
                  const generation = ++savedGeneration.current;
                  setPointSource(next);
                  setMessage(undefined);
                  if (next === "SAVED") {
                    setSavedPoints([]);
                    void listSavedAddressesClient()
                      .then((points) => {
                        if (generation === savedGeneration.current) setSavedPoints(points);
                      })
                      .catch(() => {
                        if (generation === savedGeneration.current)
                          setMessage("Không thể tải địa chỉ đã lưu. Chọn lại nguồn vị trí để thử lại.");
                      });
                  }
                }}
              >
                {(address?.id || location) && (
                  <option value="CURRENT">
                    {address?.id ? "Điểm của địa chỉ đang sửa" : "Điểm đang chọn để khám phá"}
                  </option>
                )}
                <option value="GPS">Vị trí hiện tại (GPS)</option>
                <option value="SAVED">Điểm từ địa chỉ đã lưu</option>
                <option value="MANUAL">Nhập tọa độ (nâng cao)</option>
              </select>
            </label>

            {pointSource === "SAVED" && (
              <label className={styles.field}>
                <span>Địa chỉ lấy điểm</span>
                <select
                  aria-label="Địa chỉ lấy điểm"
                  value={selectedSavedId}
                  onChange={(event) => {
                    setSelectedSavedId(event.target.value);
                    setAdjustedPoint(undefined);
                    const selected = savedPoints.find(item => item.id === event.target.value);
                    if (selected?.addressText) {
                      addressRevision.current++;
                      setAddressText(selected.addressText);
                    }
                  }}
                >
                  <option value="">Chọn địa chỉ đã lưu</option>
                  {savedPoints.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.label} — {item.addressText}
                    </option>
                  ))}
                </select>
              </label>
            )}

            {pointSource === "MANUAL" && !adjustedPoint ? (
              <div className={styles.coordinateGrid}>
                <label className={styles.field}>
                  <span>Vĩ độ <em>*</em></span>
                  <input
                    name="latitude"
                    type="number"
                    step="any"
                    required
                    value={manualLatitude}
                    onChange={(event) => {
                      setManualLatitude(event.target.value);
                    }}
                    aria-describedby={errorFor("latitude")}
                  />
                </label>
                <label className={styles.field}>
                  <span>Kinh độ <em>*</em></span>
                  <input
                    name="longitude"
                    type="number"
                    step="any"
                    required
                    value={manualLongitude}
                    onChange={(event) => {
                      setManualLongitude(event.target.value);
                    }}
                    aria-describedby={errorFor("longitude")}
                  />
                </label>
              </div>
            ) : null}
          </details>

          <DeliveryPointMap
            locating={gpsPending}
            onLocate={requestGpsAddress}
            point={initialCoordinates}
            onChange={point => {
              gpsGeneration.current++;
              savedGeneration.current++;
              setGpsPending(false);
              setGpsSuggestion(false);
              setAdjustedPoint(point);
              setMessage(undefined);
            }}
          />
          {initialCoordinates && (
            <>
              <input type="hidden" name="latitude" value={initialCoordinates.latitude} />
              <input type="hidden" name="longitude" value={initialCoordinates.longitude} />
            </>
          )}

            {fieldErrors.latitude ? (
              <p className={styles.fieldError} id="latitude-error" role="alert">
                {fieldErrors.latitude}
              </p>
            ) : null}
            {fieldErrors.longitude ? (
              <p className={styles.fieldError} id="longitude-error" role="alert">
                {fieldErrors.longitude}
              </p>
            ) : null}

          </fieldset>

          <label className={styles.field}>
            <span>Ghi chú</span>
            <textarea name="note" placeholder="Giao tại sảnh chính hoặc để ở kệ hàng..." value={note} onChange={event => setNote(event.currentTarget.value)} aria-invalid={Boolean(fieldErrors.note)} aria-describedby={errorFor("note")} />
            <small className={fieldErrors.note ? styles.errorText : undefined} id={errorFor("note")} role={fieldErrors.note ? "alert" : undefined}>{fieldErrors.note ?? "Không bắt buộc"}</small>
          </label>

          <label className={styles.switch}>
            <input name="defaultAddress" type="checkbox" defaultChecked={address?.defaultAddress ?? false} />
            <span>Đặt làm địa chỉ mặc định</span>
          </label>

          {message ? (
            <p className={styles.message} role="alert">
              {message}
            </p>
          ) : null}

          <div className={styles.formActions}>
            <button className={styles.submit} type="submit" disabled={saving}>
              {saving ? "Đang lưu…" : editing ? "Lưu thay đổi" : "Lưu địa chỉ"}
            </button>
            <button
              className={styles.cancelButton}
              type="button"
              disabled={saving}
              onClick={() => router.back()}
            >
              Hủy
            </button>
          </div>
        </form>
      </div>
    </section>
  );
}
