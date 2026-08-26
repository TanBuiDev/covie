import Link from "next/link";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import { LocationForm } from "@/features/merchant/location-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { formatMoney } from "@/lib/format/money";

type LocationStatus = "ACTIVE" | "PAUSED" | "SUSPENDED" | "DELETED" | undefined;

function locationStatus(status: LocationStatus): { label: string; tone: StatusTone } {
  switch (status) {
    case "ACTIVE": return { label: "Đang hoạt động", tone: "success" };
    case "PAUSED": return { label: "Tạm dừng", tone: "warning" };
    case "SUSPENDED": return { label: "Tạm đình chỉ", tone: "error" };
    case "DELETED": return { label: "Đã đóng", tone: "neutral" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

export default async function LocationDetailPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;
  const status = locationStatus(location.data.status);

  return (
    <section className="seller-stack">
      <section className="seller-section" aria-labelledby="location-summary-heading">
        <div className="seller-section__heading">
          <div>
            <h2 id="location-summary-heading">{location.data.name ?? "Điểm bán"}</h2>
            <p className="seller-muted">{location.data.addressText ?? "Chưa có địa chỉ hiển thị"}</p>
          </div>
          <StatusChip label={status.label} tone={status.tone} />
        </div>
        <div className="seller-grid seller-grid--two">
          <div className="seller-metric"><span>Pickup</span><strong>{location.data.pickupEnabled ? "Bật" : "Tắt"}</strong></div>
          <div className="seller-metric"><span>Tự giao</span><strong>{location.data.sellerDeliveryEnabled ? "Bật" : "Tắt"}</strong></div>
          <div className="seller-metric"><span>Dịch vụ</span><strong>{location.data.serviceEnabled ? "Bật" : "Tắt"}</strong></div>
          <div className="seller-metric">
            <span>Đơn giao tối thiểu</span>
            <strong>{location.data.minimumDeliveryOrder != null && location.data.currency
              ? formatMoney({ amount: location.data.minimumDeliveryOrder, currency: location.data.currency })
              : "—"}</strong>
          </div>
        </div>
      </section>

      <nav className="subnav" aria-label="Cấu hình điểm bán">
        <Link href={`${base}/delivery`}>Vùng phục vụ</Link>
        <Link href={`${base}/shipping`}>Phí giao hàng</Link>
        <Link href={`${base}/contacts`}>Liên hệ</Link>
      </nav>

      <LocationForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
    </section>
  );
}
