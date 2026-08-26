import type { components } from "@covie/api-client";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
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
    <div className="seller-location-list" aria-label="Danh sách điểm bán">
      {locations.map((location) => {
        if (!location.id) return null;
        const capabilities = capabilityLabels(location);
        const configured = coverageConfiguredByLocation[location.id] === true;
        return (
          <Link className="seller-location-card" key={location.id} href={`${base}/locations/${encodeURIComponent(location.id)}`}>
            <div className="seller-location-card__copy">
              <strong>{location.name ?? "Điểm bán"}</strong>
              {location.addressText ? <small>{location.addressText}</small> : null}
            </div>
            {capabilities.length ? (
              <div className="seller-capability-list" aria-label="Hình thức phục vụ">
                {capabilities.map((capability) => <span key={capability}>{capability}</span>)}
              </div>
            ) : <small className="seller-muted">Chưa bật hình thức phục vụ</small>}
            <StatusChip
              label={configured ? "Đã cấu hình khu vực" : "Chưa cấu hình giao hàng"}
              tone={configured ? "success" : "warning"}
            />
          </Link>
        );
      })}
    </div>
  );
}
