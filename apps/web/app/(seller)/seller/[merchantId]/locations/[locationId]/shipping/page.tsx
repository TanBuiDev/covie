import Link from "next/link";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { ShippingPolicySettings } from "@/features/shipping/shipping-policy-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { ArrowLeft, MapPin, Storefront } from "@phosphor-icons/react/dist/ssr";

export default async function ShippingPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const [location, shippingResult] = await Promise.all([
    getLocationForServer(merchantId, locationId),
    client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies", {
      params: { path: { merchantId, locationId } },
    }),
  ]);
  if (!shippingResult.response.ok || !shippingResult.data) throw await readApiError(shippingResult.response);

  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack seller-shipping-page">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={base}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại điểm bán</span>
        </Link>
      </p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Giao hàng</p>
        <h1>Chính sách giao hàng</h1>
        <p>Cấu hình freeship, phí cố định, phí theo điều kiện hoặc thỏa thuận.</p>
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
      <ShippingPolicySettings
        merchantId={merchantId}
        locationId={locationId}
        policies={shippingResult.data}
        locationName={location.data.name ?? "Điểm bán"}
      />
    </section>
  );
}

