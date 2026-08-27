"use client";

import type { components } from "@covie/api-client";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { listSavedAddressesClient } from "@/features/profile/address-api";
import { useBrowseLocation } from "./use-browse-location";
import styles from "./location-chooser.module.css";

type Address = components["schemas"]["UserAddressResponse"];
type Mode = "choice" | "manual" | "saved";

export function LocationChooser({ onComplete, onCancel }: { onComplete?: () => void; onCancel?: () => void }) {
  const { location, setLocation } = useBrowseLocation();
  const [mode, setMode] = useState<Mode>("choice");
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [label, setLabel] = useState("");
  const [message, setMessage] = useState<string>();
  const [locating, setLocating] = useState(false);
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState("");

  useEffect(() => {
    let active = true;
    void listSavedAddressesClient().then((items) => {
      if (!active || !items.length) return;
      const ordered = [...items].sort((a, b) => Number(Boolean(b.defaultAddress)) - Number(Boolean(a.defaultAddress)));
      setAddresses(ordered);
      const preferred = ordered.find((address) => address.id && (location?.kind !== "address" || address.id === location.addressId)) ?? ordered.find((address) => address.id);
      setSelectedAddressId(preferred?.id ?? "");
      setMode("saved");
    }).catch(() => {
      // Guest/unauthorized users stay on the two-option chooser. No auth state is inferred client-side.
    });
    return () => { active = false; };
  }, [location]);

  const selectedAddress = useMemo(
    () => addresses.find((address) => address.id === selectedAddressId),
    [addresses, selectedAddressId],
  );

  function complete() {
    onComplete?.();
  }

  function useGps() {
    if (!("geolocation" in navigator)) {
      setMessage("Thiết bị không hỗ trợ định vị. Bạn vẫn có thể chọn vị trí thủ công.");
      setMode("manual");
      return;
    }
    setLocating(true);
    setMessage(undefined);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocation({
          kind: "coordinates",
          lat: position.coords.latitude,
          lng: position.coords.longitude,
          label: "Vị trí hiện tại",
        });
        setLocating(false);
        complete();
      },
      () => {
        setMessage("Quyền vị trí bị từ chối hoặc thiết bị không phản hồi. Bạn vẫn có thể chọn thủ công.");
        setLocating(false);
        setMode("manual");
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  function submitManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const latitude = Number(lat);
    const longitude = Number(lng);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Nhập vĩ độ và kinh độ hợp lệ trước khi tiếp tục.");
      return;
    }
    setLocation({ kind: "coordinates", lat: latitude, lng: longitude, label: label.trim() || "Vị trí đã chọn" });
    setMessage(undefined);
    complete();
  }

  function useSavedAddress() {
    if (!selectedAddress?.id) return;
    setLocation({
      kind: "address",
      addressId: selectedAddress.id,
      label: selectedAddress.label?.trim() || selectedAddress.addressText?.trim() || "Địa chỉ đã lưu",
    });
    complete();
  }

  if (mode === "choice") {
    return (
      <div className={styles.overlay} role="presentation">
        <section className={styles.sheet} role="dialog" aria-modal="true" aria-labelledby="browse-location-title">
          <div className={styles.handle} aria-hidden="true" />
          <h2 id="browse-location-title">Chọn vị trí</h2>
          <div className={styles.options}>
            <button className={styles.option} type="button" onClick={useGps} disabled={locating}>
              {locating ? "Đang xác định vị trí…" : "Dùng vị trí hiện tại"}
            </button>
            <button className={styles.option} type="button" onClick={() => { setMessage(undefined); setMode("manual"); }}>Chọn vị trí thủ công</button>
            {onCancel ? <button className={styles.option} type="button" onClick={onCancel}>Để sau</button> : null}
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className={styles.fullOverlay}>
      <section className={styles.fullPanel} role="dialog" aria-modal="true" aria-labelledby="browse-location-page-title">
        <header className={styles.pageHeader}>
          <button className={styles.back} type="button" aria-label="Quay lại" onClick={() => { setMessage(undefined); setMode("choice"); }}>
            <CoVieIcon name="back" />
          </button>
          <h2 id="browse-location-page-title">Chọn vị trí</h2>
        </header>

        {mode === "saved" ? (
          <div className={styles.savedContent}>
            <h2>Địa chỉ đã lưu</h2>
            <p className={styles.lead}>Chọn địa chỉ dùng để khám phá khu vực gần bạn. Địa chỉ nhận hàng của bạn vẫn giữ nguyên.</p>
            <fieldset className={styles.addresses} aria-label="Địa chỉ dùng để khám phá">
              {addresses.map((address) => address.id ? (
                <label className={styles.radio} key={address.id}>
                  <input
                    type="radio"
                    name="browse-address"
                    value={address.id}
                    checked={selectedAddressId === address.id}
                    onChange={() => setSelectedAddressId(address.id ?? "")}
                  />
                  <span>{address.label || "Địa chỉ"}{address.addressText ? ` — ${address.addressText}` : ""}</span>
                </label>
              ) : null)}
            </fieldset>
            <button className={styles.primary} type="button" disabled={!selectedAddressId} onClick={useSavedAddress}>Dùng địa chỉ đã chọn</button>
            <button className={styles.secondary} type="button" disabled={locating} onClick={useGps}>{locating ? "Đang xác định vị trí…" : "Dùng vị trí hiện tại"}</button>
          </div>
        ) : (
          <div className={styles.manualContent}>
            <h2>Chọn vị trí thủ công</h2>
            <p className={styles.lead}>Backend hiện chỉ nhận tọa độ hoặc địa chỉ đã lưu. CoVie không tự suy diễn tọa độ từ một chuỗi địa chỉ chưa được xác thực.</p>
            {message ? (
              <div className={styles.alert} role="alert">
                <strong>Không thể dùng vị trí hiện tại</strong>
                <p>{message}</p>
              </div>
            ) : null}
            <form className={styles.form} onSubmit={submitManual}>
              <label className={styles.field}>
                <span>Tên khu vực</span>
                <input value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Ví dụ: TP. Thủ Đức" />
              </label>
              <div className={styles.coordinateGrid}>
                <label className={styles.field}>
                  <span>Vĩ độ</span>
                  <input inputMode="decimal" value={lat} onChange={(event) => setLat(event.target.value)} placeholder="10.8412" required />
                </label>
                <label className={styles.field}>
                  <span>Kinh độ</span>
                  <input inputMode="decimal" value={lng} onChange={(event) => setLng(event.target.value)} placeholder="106.8098" required />
                </label>
              </div>
              <p className={styles.sourceNote}>Chỉ tọa độ và nhãn hiển thị được lưu cho ngữ cảnh khám phá.</p>
              <button className={styles.primary} type="submit">Dùng vị trí này</button>
              <button className={styles.secondary} type="button" disabled={locating} onClick={useGps}>{locating ? "Đang xác định vị trí…" : "Thử vị trí hiện tại"}</button>
            </form>
          </div>
        )}
      </section>
    </div>
  );
}
