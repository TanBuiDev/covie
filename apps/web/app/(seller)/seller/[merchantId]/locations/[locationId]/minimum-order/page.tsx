import Link from "next/link";
import { MinimumOrderForm } from "@/features/merchant/minimum-order-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { ArrowLeft, MapPin, Storefront } from "@phosphor-icons/react/dist/ssr";

export default async function MinimumOrderPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;
  return (
    <section className="seller-stack seller-minimum-order-page">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={base}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại điểm bán</span>
        </Link>
      </p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Đơn tối thiểu</p>
        <h1>Yêu cầu đơn tối thiểu</h1>
        <p>Cấu hình ngưỡng giá trị đơn hàng tối thiểu để chấp nhận giao hàng tận nơi.</p>
      </header>
      <section className="seller-info-card seller-location-badge-card" aria-label="Điểm bán áp dụng">
        <div className="seller-location-badge-card__icon" aria-hidden="true">
          <Storefront size={22} weight="duotone" />
        </div>
        <div className="seller-location-badge-card__info">
          <strong>{location.data.name ?? "Điểm bán"}</strong>
          {location.data.addressText ? (
            <p>
              <MapPin size={13} weight="bold" aria-hidden="true" />
              <span>{location.data.addressText}</span>
            </p>
          ) : null}
        </div>
      </section>
      <MinimumOrderForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
    </section>
  );
}

