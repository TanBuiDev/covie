"use client";

import { useEffect, useRef, useState } from "react";
import type { CircleMarker, Map as LeafletMap } from "leaflet";
import "leaflet/dist/leaflet.css";
import styles from "./delivery-point-map.module.css";
import { Crosshair, CircleNotch, MapPin } from "@phosphor-icons/react";

type Point = { latitude: number; longitude: number };

export function DeliveryPointMap({ point, onChange, onLocate, locating = false }: { point?: Point; onChange: (point: Point) => void; onLocate: () => void; locating?: boolean }) {
  const container = useRef<HTMLDivElement>(null);
  const mapRef = useRef<LeafletMap>(null);
  const markerRef = useRef<CircleMarker>(null);
  const latestPoint = useRef(point);
  const latestChange = useRef(onChange);
  useEffect(() => { latestPoint.current = point; latestChange.current = onChange; }, [point, onChange]);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(false);
  const [preview, setPreview] = useState<{ x: number; y: number }>();

  useEffect(() => {
    let disposed = false;
    void import("leaflet").then(L => {
      if (disposed || !container.current) return;
      const initial = latestPoint.current;
      const map = L.map(container.current, { scrollWheelZoom: false }).setView(initial ? [initial.latitude, initial.longitude] : [10.7769, 106.7009], initial ? 17 : 12);
      mapRef.current = map;
      const marker = L.circleMarker(initial ? [initial.latitude, initial.longitude] : [10.7769, 106.7009], { radius: 9, color: "#ffffff", weight: 3, fillColor: "#047857", fillOpacity: 1 });
      markerRef.current = marker;
      if (initial) marker.addTo(map);
      L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", { maxZoom: 19, attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap contributors</a>' }).on("tileerror", () => { if (!disposed) setFailed(true); }).addTo(map);
      map.on("click", event => latestChange.current({ latitude: event.latlng.lat, longitude: event.latlng.lng }));
      setReady(true);
    }).catch(() => { if (!disposed) setFailed(true); });
    return () => { disposed = true; mapRef.current?.remove(); mapRef.current = null; markerRef.current = null; };
  }, []);

  const latitude = point?.latitude, longitude = point?.longitude;
  useEffect(() => {
    const map = mapRef.current, marker = markerRef.current;
    if (!map || !marker) return;
    if (latitude != null && longitude != null) {
      const zoom = map.hasLayer(marker) ? map.getZoom() : 17;
      marker.setLatLng([latitude, longitude]).addTo(map);
      map.setView([latitude, longitude], zoom, { animate: false });
    } else marker.remove();
  }, [latitude, longitude, ready]);

  return <div className={styles.wrapper}>
    <div className={styles.mapFrame}>
      <div ref={container} className={`${styles.map} ${preview ? styles.previewing : ""}`} role="region" aria-label="Bản đồ địa chỉ giao hàng" aria-keyshortcuts="Enter"
        onKeyDown={event => { if (event.key === "Enter" && event.target === event.currentTarget) { event.preventDefault(); const center = mapRef.current?.getCenter(); if (center) latestChange.current({ latitude: center.lat, longitude: center.lng }); } }}
        onPointerMove={event => {
          if (event.pointerType !== "mouse" || event.buttons !== 0 || (event.target instanceof Element && event.target.closest(".leaflet-control"))) { setPreview(undefined); return; }
          const bounds = event.currentTarget.getBoundingClientRect();
          setPreview({ x: event.clientX - bounds.left, y: event.clientY - bounds.top });
        }}
        onPointerLeave={() => setPreview(undefined)} onPointerDown={() => setPreview(undefined)}
      />
      {preview && <span className={styles.previewPin} data-testid="delivery-pin-preview" aria-hidden="true" style={{ left: preview.x, top: preview.y }}><MapPin size={32} weight="fill" /></span>}
      <button type="button" className={styles.locate} aria-label="Dùng vị trí hiện tại" title={locating ? "Đang lấy vị trí…" : "Dùng vị trí hiện tại"} disabled={locating} onClick={onLocate}>
        {locating ? <CircleNotch size={22} className={styles.spinner} /> : <Crosshair size={22} weight="bold" />}
      </button>
    </div>
    {!ready && !failed && <p role="status" className={styles.hint}>Đang tải bản đồ…</p>}
    {failed && <p role="status" className={styles.hint}>Chưa tải được nền bản đồ. Bạn vẫn có thể dùng GPS hoặc nhập tọa độ trong mục Nâng cao.</p>}
  </div>;
}
