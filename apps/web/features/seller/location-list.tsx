import Link from "next/link";
import type { components } from "@covie/api-client";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { formatMoney } from "@/lib/format/money";
import { sellerBasePath } from "./seller-context";

type MerchantLocation = components["schemas"]["MerchantLocationResponse"];

function capabilityCopy(location: MerchantLocation) {
  const capabilities = [
    location.pickupEnabled ? "Nhận tại cửa hàng" : null,
    location.sellerDeliveryEnabled ? "Tự giao" : null,
    location.serviceEnabled ? "Dịch vụ" : null,
  ].filter(Boolean);
  return capabilities.length ? capabilities.join(" · ") : "Chưa bật hình thức phục vụ";
}

export function LocationList({ merchantId, locations }: { merchantId: string; locations: MerchantLocation[] }) {
  const base = sellerBasePath(merchantId);

  if (!locations.length) {
    return (
      <EmptyState
        title="Chưa có điểm bán"
        description="Tạo điểm bán bên dưới để cấu hình địa chỉ, phạm vi phục vụ và chính sách giao hàng."
      />
    );
  }

  return (
    <div className="seller-row-list" aria-label="Danh sách điểm bán">
      {locations.map((location) => location.id ? (
        <Link className="seller-row" key={location.id} href={`${base}/locations/${encodeURIComponent(location.id)}`}>
          <div>
            <strong>{location.name ?? "Điểm bán"}</strong>
            {location.addressText ? <small>{location.addressText}</small> : null}
            <small>{capabilityCopy(location)}</small>
            {location.minimumDeliveryOrder != null && location.currency ? (
              <small>Đơn giao tối thiểu: {formatMoney({ amount: location.minimumDeliveryOrder, currency: location.currency })}</small>
            ) : null}
          </div>
          <StatusChip status={location.status} />
        </Link>
      ) : null)}
    </div>
  );
}
