import Link from "next/link";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { CoverageSettings } from "@/features/shipping/coverage-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { ArrowLeft, MapPin, Storefront } from "@phosphor-icons/react/dist/ssr";

export default async function DeliveryPage({ params }: { params: Promise<{ merchantId: string; locationId: string }> }) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const [location, coverageResult] = await Promise.all([
    getLocationForServer(merchantId, locationId),
    client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages", {
      params: { path: { merchantId, locationId } },
    }),
  ]);
  if (!coverageResult.response.ok || !coverageResult.data) throw await readApiError(coverageResult.response);

  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack seller-delivery-page">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={base}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại điểm bán</span>
        </Link>
      </p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Vùng phục vụ</p>
        <h1>Vùng giao hàng &amp; phục vụ</h1>
        <p>Cấu hình bán kính hoặc khu vực phục vụ cho điểm bán này.</p>
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
      <CoverageSettings
        merchantId={merchantId}
        locationId={locationId}
        coverages={coverageResult.data}
        locationName={location.data.name ?? "Điểm bán"}
      />
    </section>
  );
}

