"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { ArrowRight, Handshake, MagnifyingGlass, MapPin, Storefront, Truck } from "@phosphor-icons/react";
import { sellerBasePath } from "./seller-context";

type MerchantLocation = components["schemas"]["MerchantLocationResponse"];

function capabilityLabels(location: MerchantLocation) {
  return [
    location.pickupEnabled ? "Nhận tại chỗ" : null,
    location.sellerDeliveryEnabled ? "Giao hàng" : null,
    location.serviceEnabled ? "Dịch vụ" : null,
  ].filter((value): value is string => Boolean(value));
}

export function LocationList({
  merchantId,
  locations,
  coverageConfiguredByLocation = {},
}: {
  merchantId: string;
  locations: MerchantLocation[];
  coverageConfiguredByLocation?: Record<string, boolean>;
}) {
  const base = sellerBasePath(merchantId);
  const [searchQuery, setSearchQuery] = useState("");

  const deliveryCount = useMemo(() => locations.filter((loc) => loc.sellerDeliveryEnabled).length, [locations]);
  const pickupCount = useMemo(() => locations.filter((loc) => loc.pickupEnabled).length, [locations]);

  const filteredLocations = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return locations;
    return locations.filter(
      (loc) =>
        (loc.name ?? "").toLowerCase().includes(q) ||
        (loc.addressText ?? "").toLowerCase().includes(q)
    );
  }, [locations, searchQuery]);

  if (!locations.length) {
    return (
      <EmptyState
        title="Chưa có điểm bán"
        description="Tạo điểm bán đầu tiên để cấu hình địa chỉ, phạm vi phục vụ và giao hàng."
        action={<Link className="seller-button" href={`${base}/locations/new`}>Thêm điểm bán</Link>}
      />
    );
  }

  return (
    <div className="seller-location-wrapper">
      {/* KPI OVERVIEW BANNER */}
      <div className="seller-location-kpi-banner">
        <div className="seller-location-kpi-card">
          <div className="seller-location-kpi-icon seller-location-kpi-icon--emerald" aria-hidden="true">
            <Storefront size={22} weight="duotone" />
          </div>
          <div className="seller-location-kpi-content">
            <span className="seller-location-kpi-label">Tổng chi nhánh</span>
            <strong className="seller-location-kpi-value">{locations.length} <small>điểm bán</small></strong>
          </div>
        </div>

        <div className="seller-location-kpi-card">
          <div className="seller-location-kpi-icon seller-location-kpi-icon--blue" aria-hidden="true">
            <Truck size={22} weight="duotone" />
          </div>
          <div className="seller-location-kpi-content">
            <span className="seller-location-kpi-label">Hỗ trợ giao hàng</span>
            <strong className="seller-location-kpi-value">{deliveryCount} <small>chi nhánh</small></strong>
          </div>
        </div>

        <div className="seller-location-kpi-card">
          <div className="seller-location-kpi-icon seller-location-kpi-icon--teal" aria-hidden="true">
            <Handshake size={22} weight="duotone" />
          </div>
          <div className="seller-location-kpi-content">
            <span className="seller-location-kpi-label">Nhận tại chỗ (Pickup)</span>
            <strong className="seller-location-kpi-value">{pickupCount} <small>chi nhánh</small></strong>
          </div>
        </div>
      </div>

      {/* SEARCH TOOLBAR */}
      {locations.length > 1 ? (
        <div className="seller-location-search-bar">
          <div className="seller-catalog-search">
            <MagnifyingGlass size={18} className="seller-catalog-search__icon" aria-hidden="true" />
            <input
              type="search"
              placeholder="Tìm kiếm theo tên chi nhánh hoặc địa chỉ…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="seller-catalog-search__input"
              aria-label="Tìm kiếm điểm bán"
            />
          </div>
        </div>
      ) : null}

      {/* LOCATION LIST */}
      {!filteredLocations.length ? (
        <div className="seller-catalog-empty-search">
          <p>Không tìm thấy điểm bán nào khớp với từ khóa &quot;{searchQuery}&quot;.</p>
        </div>
      ) : (
        <div className="seller-location-list" aria-label="Danh sách điểm bán">
          {filteredLocations.map((location) => {
            if (!location.id) return null;
            const capabilities = capabilityLabels(location);
            const configured = coverageConfiguredByLocation[location.id] === true;
            return (
              <Link
                className="seller-location-card"
                key={location.id}
                href={`${base}/locations/${encodeURIComponent(location.id)}`}
              >
                <div className="seller-location-card__head-row">
                  <div className="seller-location-card__icon-badge" aria-hidden="true">
                    <Storefront size={20} weight="duotone" />
                  </div>
                  <div className="seller-location-card__copy">
                    <strong>{location.name ?? "Điểm bán"}</strong>
                    {location.addressText ? (
                      <small className="seller-location-card__address">
                        <MapPin size={13} weight="bold" aria-hidden="true" />
                        <span>{location.addressText}</span>
                      </small>
                    ) : null}
                  </div>
                </div>

                <div className="seller-location-card__center">
                  {capabilities.length ? (
                    <div className="seller-capability-list" aria-label="Hình thức phục vụ">
                      {capabilities.map((capability) => {
                        const toneClass =
                          capability === "Nhận tại chỗ"
                            ? "seller-capability-tag--emerald"
                            : capability === "Giao hàng"
                            ? "seller-capability-tag--blue"
                            : "seller-capability-tag--purple";
                        return (
                          <span key={capability} className={`seller-capability-tag ${toneClass}`}>
                            {capability}
                          </span>
                        );
                      })}
                    </div>
                  ) : (
                    <small className="seller-muted">Chưa bật hình thức phục vụ</small>
                  )}
                </div>

                <div className="seller-location-card__footer">
                  <StatusChip
                    label={configured ? "Đã cấu hình khu vực" : "Chưa cấu hình giao hàng"}
                    tone={configured ? "success" : "warning"}
                  />
                  <span className="seller-location-card__action-link">
                    <span>Quản lý</span>
                    <ArrowRight size={14} weight="bold" aria-hidden="true" />
                  </span>
                </div>
              </Link>
            );
          })}
        </div>
      )}
    </div>
  );
}
