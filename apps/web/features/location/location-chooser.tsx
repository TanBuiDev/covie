"use client";

import { useState, type FormEvent } from "react";
import { useBrowseLocation } from "./use-browse-location";

export function LocationChooser() {
  const { location, setLocation } = useBrowseLocation();
  const [lat, setLat] = useState("");
  const [lng, setLng] = useState("");
  const [label, setLabel] = useState("");
  const [message, setMessage] = useState<string>();
  const [locating, setLocating] = useState(false);

  function useGps() {
    if (!("geolocation" in navigator)) {
      setMessage("Thiết bị không hỗ trợ định vị. Bạn có thể nhập vị trí thủ công.");
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
      },
      () => {
        setMessage("Không thể lấy vị trí. Bạn có thể chọn vị trí thủ công.");
        setLocating(false);
      },
      { enableHighAccuracy: false, timeout: 10_000, maximumAge: 60_000 },
    );
  }

  function submitManual(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const latitude = Number(lat);
    const longitude = Number(lng);
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Nhập vĩ độ và kinh độ hợp lệ.");
      return;
    }
    setLocation({ kind: "coordinates", lat: latitude, lng: longitude, label: label.trim() || "Vị trí đã chọn" });
    setMessage(undefined);
  }

  return (
    <section aria-labelledby="browse-location-title">
      <h2 id="browse-location-title">Chọn khu vực khám phá</h2>
      {location && <p>Đang xem quanh: <strong>{location.label}</strong></p>}
      <button type="button" onClick={useGps} disabled={locating}>
        {locating ? "Đang xác định vị trí…" : "Dùng vị trí hiện tại"}
      </button>
      <form onSubmit={submitManual}>
        <label>Vĩ độ <input inputMode="decimal" value={lat} onChange={(event) => setLat(event.target.value)} /></label>
        <label>Kinh độ <input inputMode="decimal" value={lng} onChange={(event) => setLng(event.target.value)} /></label>
        <label>Tên khu vực <input value={label} onChange={(event) => setLabel(event.target.value)} placeholder="Ví dụ: Quận 2" /></label>
        <button type="submit">Dùng vị trí thủ công</button>
      </form>
      {message && <p role="alert">{message}</p>}
    </section>
  );
}
