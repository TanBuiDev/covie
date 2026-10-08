"use client";

import type { components } from "@covie/api-client";
import { useEffect, useMemo, useState, type FormEvent } from "react";
import {
  Buildings,
  Crosshair,
  HouseLine,
  MagnifyingGlass,
  MapPin,
  SlidersHorizontal,
  X,
} from "@phosphor-icons/react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { listSavedAddressesClient } from "@/features/profile/address-api";
import { useBrowseLocation } from "./use-browse-location";
import styles from "./location-chooser.module.css";

type Address = components["schemas"]["UserAddressResponse"];
type Mode = "choice" | "presets" | "manual" | "saved";

export type CommunityPreset = {
  id: string;
  name: string;
  area: string;
  lat: number;
  lng: number;
  tag: string;
};

export const POPULAR_COMMUNITIES: CommunityPreset[] = [
  {
    id: "vhgp",
    name: "Vinhomes Grand Park",
    area: "P. Long Bình & Long Thạnh Mỹ, TP. Thủ Đức",
    lat: 10.8400,
    lng: 106.8100,
    tag: "Đại đô thị",
  },
  {
    id: "rainbow",
    name: "The Rainbow (Toà S1 - S5)",
    area: "Vinhomes Grand Park, TP. Thủ Đức",
    lat: 10.8420,
    lng: 106.8120,
    tag: "Cụm toà S1 - S5",
  },
  {
    id: "origami",
    name: "The Origami (Toà S6 - S10)",
    area: "Vinhomes Grand Park, TP. Thủ Đức",
    lat: 10.8380,
    lng: 106.8150,
    tag: "Cụm toà S6 - S10",
  },
  {
    id: "masteri-cp",
    name: "Masteri Centre Point",
    area: "Vinhomes Grand Park, TP. Thủ Đức",
    lat: 10.8410,
    lng: 106.8140,
    tag: "Căn hộ cao cấp",
  },
  {
    id: "beverly",
    name: "The Beverly & Solari",
    area: "Vinhomes Grand Park, TP. Thủ Đức",
    lat: 10.8450,
    lng: 106.8180,
    tag: "Phân khu mới",
  },
  {
    id: "vhcp",
    name: "Vinhomes Central Park",
    area: "P. 22, Q. Bình Thạnh, TP.HCM",
    lat: 10.7950,
    lng: 106.7220,
    tag: "Quận Bình Thạnh",
  },
  {
    id: "masteri-td",
    name: "Masteri Thảo Điền",
    area: "P. Thảo Điền, TP. Thủ Đức, TP.HCM",
    lat: 10.8020,
    lng: 106.7410,
    tag: "Thảo Điền",
  },
];

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
  const [searchQuery, setSearchQuery] = useState("");

  // Handle ESC key to dismiss modal
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") {
        onCancel?.();
      }
    }
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  useEffect(() => {
    let active = true;
    void listSavedAddressesClient().then((items) => {
      if (!active || !items.length) return;
      const ordered = [...items].sort((a, b) => Number(Boolean(b.defaultAddress)) - Number(Boolean(a.defaultAddress)));
      setAddresses(ordered);
      const preferred = ordered.find((address) => address.id && (location?.kind !== "address" || address.id === location.addressId)) ?? ordered.find((address) => address.id);
      setSelectedAddressId(preferred?.id ?? "");
    }).catch(() => {
      // Guest/unauthorized users stay on the chooser.
    });
    return () => { active = false; };
  }, [location]);

  const selectedAddress = useMemo(
    () => addresses.find((address) => address.id === selectedAddressId),
    [addresses, selectedAddressId],
  );

  const filteredCommunities = useMemo(() => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return POPULAR_COMMUNITIES;
    return POPULAR_COMMUNITIES.filter(
      (c) => c.name.toLowerCase().includes(query) || c.area.toLowerCase().includes(query) || c.tag.toLowerCase().includes(query),
    );
  }, [searchQuery]);

  function complete() {
    onComplete?.();
  }

  function selectCommunity(community: CommunityPreset) {
    setLocation({
      kind: "coordinates",
      lat: community.lat,
      lng: community.lng,
      label: community.name,
    });
    complete();
  }

  function useGps() {
    if (!("geolocation" in navigator)) {
      setMessage("Thiết bị không hỗ trợ định vị. Bạn có thể chọn toà nhà từ danh sách có sẵn.");
      setMode("presets");
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
        setMessage("Quyền vị trí bị từ chối hoặc thiết bị không phản hồi. Bạn có thể chọn toà nhà từ danh sách bên dưới.");
        setLocating(false);
        setMode("presets");
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
      <div
        className={styles.overlay}
        role="presentation"
        onClick={(e) => { if (e.target === e.currentTarget) onCancel?.(); }}
      >
        <section className={styles.sheet} role="dialog" aria-modal="true" aria-labelledby="browse-location-title">
          <div className={styles.handle} aria-hidden="true" />

          <div className={styles.sheetHeader}>
            <div className={styles.sheetHeaderTitles}>
              <h2 id="browse-location-title">Chọn vị trí xem chợ</h2>
              <p className={styles.sheetSubtitle}>Ưu tiên món ăn, dịch vụ và đồ thanh lý gần toà nhà của bạn</p>
            </div>
            {onCancel && (
              <button className={styles.closeBtn} type="button" aria-label="Đóng" onClick={onCancel}>
                <X size={18} weight="bold" />
              </button>
            )}
          </div>

          <div className={styles.primaryOptions}>
            <button className={styles.optionCard} type="button" onClick={useGps} disabled={locating}>
              <div className={`${styles.optionIcon} ${styles.gpsIcon}`} aria-hidden="true">
                <Crosshair size={24} weight="duotone" />
              </div>
              <div className={styles.optionText}>
                <strong>{locating ? "Đang xác định vị trí qua GPS…" : "Dùng vị trí hiện tại"}</strong>
                <span>Tự động kết nối chợ toà nhà gần nhất</span>
              </div>
            </button>

            <button className={styles.optionCard} type="button" onClick={() => { setMessage(undefined); setMode("presets"); }}>
              <div className={`${styles.optionIcon} ${styles.buildingIcon}`} aria-hidden="true">
                <Buildings size={24} weight="duotone" />
              </div>
              <div className={styles.optionText}>
                <strong>Chọn toà nhà &amp; Khu dân cư</strong>
                <span>Vinhomes Grand Park, The Rainbow, Masteri...</span>
              </div>
            </button>

            {addresses.length > 0 && (
              <button className={styles.optionCard} type="button" onClick={() => { setMessage(undefined); setMode("saved"); }}>
                <div className={`${styles.optionIcon} ${styles.savedIcon}`} aria-hidden="true">
                  <HouseLine size={24} weight="duotone" />
                </div>
                <div className={styles.optionText}>
                  <strong>Địa chỉ đã lưu ({addresses.length})</strong>
                  <span>Chọn nhanh từ sổ địa chỉ của bạn</span>
                </div>
              </button>
            )}
          </div>

          <div className={styles.quickPicksSection}>
            <div className={styles.quickPicksHeader}>
              <span>Gợi ý toà nhà nội khu (1 chạm):</span>
            </div>
            <div className={styles.quickPicksRow}>
              {POPULAR_COMMUNITIES.slice(0, 4).map((c) => (
                <button
                  key={c.id}
                  type="button"
                  className={styles.quickPickChip}
                  onClick={() => selectCommunity(c)}
                >
                  <MapPin size={14} weight="duotone" aria-hidden="true" />
                  <span>{c.name}</span>
                </button>
              ))}
            </div>
          </div>
        </section>
      </div>
    );
  }

  if (mode === "presets") {
    return (
      <div
        className={styles.fullOverlay}
        role="presentation"
        onClick={(e) => { if (e.target === e.currentTarget) onCancel?.(); }}
      >
        <section className={styles.fullPanel} role="dialog" aria-modal="true" aria-labelledby="browse-location-presets-title">
          <header className={styles.pageHeader}>
            <button className={styles.back} type="button" aria-label="Quay lại" onClick={() => { setMessage(undefined); setMode("choice"); }}>
              <CoVieIcon name="back" />
            </button>
            <h2 id="browse-location-presets-title">Chọn khu dân cư &amp; toà nhà</h2>
            {onCancel && (
              <button className={styles.closeBtn} type="button" aria-label="Đóng" onClick={onCancel}>
                <X size={18} weight="bold" />
              </button>
            )}
          </header>

          <div className={styles.presetsContent}>
            <div className={styles.searchBox}>
              <span className={styles.searchIcon} aria-hidden="true">
                <MagnifyingGlass size={18} weight="bold" />
              </span>
              <input
                type="search"
                className={styles.searchInput}
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Tìm toà nhà, khu vực (ví dụ: S5, Rainbow, Masteri...)"
                aria-label="Tìm toà nhà, khu vực"
                autoFocus
              />
              {searchQuery && (
                <button type="button" className={styles.clearSearchBtn} onClick={() => setSearchQuery("")} aria-label="Xoá tìm kiếm">
                  <X size={14} weight="bold" />
                </button>
              )}
            </div>

            {message && (
              <div className={styles.alert} role="alert">
                <strong>Thông báo</strong>
                <p>{message}</p>
              </div>
            )}

            <div className={styles.communityList} role="listbox" aria-label="Danh sách khu dân cư">
              {filteredCommunities.map((community) => (
                <button
                  key={community.id}
                  type="button"
                  className={styles.communityItem}
                  onClick={() => selectCommunity(community)}
                >
                  <div className={styles.communityItemIcon} aria-hidden="true">
                    <Buildings size={22} weight="duotone" />
                  </div>
                  <div className={styles.communityItemInfo}>
                    <div className={styles.communityNameGroup}>
                      <strong className={styles.communityName}>{community.name}</strong>
                      <span className={styles.communityTag}>{community.tag}</span>
                    </div>
                    <span className={styles.communityArea}>{community.area}</span>
                  </div>
                  <span className={styles.communitySelectArrow} aria-hidden="true">›</span>
                </button>
              ))}

              {filteredCommunities.length === 0 && (
                <div className={styles.emptyPresets}>
                  <p>Không tìm thấy toà nhà hoặc khu vực khớp với &ldquo;{searchQuery}&rdquo;</p>
                  <button type="button" className={styles.secondary} onClick={() => setSearchQuery("")}>
                    Xem tất cả khu vực
                  </button>
                </div>
              )}
            </div>

            <div className={styles.advancedFooter}>
              <button
                type="button"
                className={styles.advancedToggleBtn}
                onClick={() => { setMessage(undefined); setMode("manual"); }}
              >
                <SlidersHorizontal size={16} weight="bold" aria-hidden="true" />
                <span>Nhập tọa độ vĩ độ/kinh độ thủ công &rsaquo;</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div
      className={styles.fullOverlay}
      role="presentation"
      onClick={(e) => { if (e.target === e.currentTarget) onCancel?.(); }}
    >
      <section className={styles.fullPanel} role="dialog" aria-modal="true" aria-labelledby="browse-location-page-title">
        <header className={styles.pageHeader}>
          <button className={styles.back} type="button" aria-label="Quay lại" onClick={() => { setMessage(undefined); setMode("choice"); }}>
            <CoVieIcon name="back" />
          </button>
          <h2 id="browse-location-page-title">Chọn vị trí</h2>
          {onCancel && (
            <button className={styles.closeBtn} type="button" aria-label="Đóng" onClick={onCancel}>
              <X size={18} weight="bold" />
            </button>
          )}
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
            <button className={styles.secondary} type="button" disabled={locating} onClick={useGps}>{locating ? "Đang xác định vị trí…" : "Dùng vị trí hiện tại (GPS)"}</button>
          </div>
        ) : (
          <div className={styles.manualContent}>
            <h2>Nhập tọa độ thủ công</h2>
            <p className={styles.lead}>Dành cho nhà phát triển hoặc trường hợp kiểm thử tọa độ cụ thể.</p>
            {message ? (
              <div className={styles.alert} role="alert">
                <strong>Thông báo vị trí</strong>
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
              <button className={styles.secondary} type="button" disabled={locating} onClick={useGps}>{locating ? "Đang xác định vị trí…" : "Thử vị trí hiện tại (GPS)"}</button>
            </form>
          </div>
        )}
      </section>
    </div>
  );
}
