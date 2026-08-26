import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { StatusChip } from "@/components/ui/status-chip";
import { formatMoney } from "@/lib/format/money";

export default async function LocationDetailPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Điểm bán</p>
        <h1>{location.data.name ?? "Điểm bán"}</h1>
        <p>{location.data.addressText ?? "Chưa có địa chỉ hiển thị"}</p>
      </header>

      <section className="seller-section" aria-labelledby="location-summary-heading">
        <div className="seller-section__heading">
          <h2 id="location-summary-heading">Tóm tắt vận hành</h2>
          <StatusChip status={location.data.status} />
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
      </nav>

      <LocationForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
    </section>
  );
}
