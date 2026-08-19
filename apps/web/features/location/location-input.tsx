"use client";

import { FormEvent, useState } from "react";
import type { DiscoveryLocation } from "./location-context";

export function LocationInput({ value, onChange }: { value?: DiscoveryLocation; onChange: (location: DiscoveryLocation) => void }) {
  const [kind, setKind] = useState<DiscoveryLocation["kind"]>(value?.kind ?? "coordinates");
  const [lat, setLat] = useState(value?.kind === "coordinates" ? String(value.lat) : "");
  const [lng, setLng] = useState(value?.kind === "coordinates" ? String(value.lng) : "");
  const [addressId, setAddressId] = useState(value?.kind === "saved-address" ? value.addressId : "");
  const [message, setMessage] = useState<string>();

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (kind === "saved-address") {
      if (!addressId.trim()) { setMessage("Nhập mã địa chỉ đã lưu để khám phá."); return; }
      setMessage(undefined);
      onChange({ kind, addressId: addressId.trim() });
      return;
    }
    const latitude = Number(lat);
    const longitude = Number(lng);
    if (!Number.isFinite(latitude) || !Number.isFinite(longitude) || latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      setMessage("Nhập vĩ độ và kinh độ hợp lệ."); return;
    }
    setMessage(undefined);
    onChange({ kind, lat: latitude, lng: longitude });
  }

  return <form onSubmit={submit} aria-label="Ngữ cảnh vị trí khám phá">
    <label>Định vị theo <select value={kind} onChange={(event) => { setKind(event.target.value as DiscoveryLocation["kind"]); setMessage(undefined); }}>
      <option value="coordinates">Tọa độ hiện tại</option>
      <option value="saved-address">Địa chỉ đã lưu</option>
    </select></label>
    {kind === "coordinates" ? <>
      <label>Vĩ độ <input name="lat" inputMode="decimal" value={lat} onChange={(event) => setLat(event.target.value)} /></label>
      <label>Kinh độ <input name="lng" inputMode="decimal" value={lng} onChange={(event) => setLng(event.target.value)} /></label>
    </> : <label>Mã địa chỉ <input name="addressId" value={addressId} onChange={(event) => setAddressId(event.target.value)} /></label>}
    {message && <p role="alert">{message}</p>}
    <button type="submit">Dùng vị trí này</button>
  </form>;
}
