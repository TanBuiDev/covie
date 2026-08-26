import Link from "next/link";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { CoverageSettings } from "@/features/shipping/coverage-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

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

  return (
    <section className="seller-stack">
      <p className="seller-desktop-only"><Link href={`/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`}>Quay lại điểm bán</Link></p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Vùng phục vụ</p>
        <h1>Vùng giao hàng & phục vụ</h1>
        <p>Bán kính và khu vực được lưu đúng contract backend. Mọi chỉnh sửa dùng version hiện tại làm If-Match.</p>
      </header>
      <section className="seller-info-card" aria-label="Điểm bán áp dụng">
        <strong>{location.data.name ?? "Điểm bán"}</strong>
        {location.data.addressText ? <p>{location.data.addressText}</p> : null}
      </section>
      <CoverageSettings merchantId={merchantId} locationId={locationId} coverages={coverageResult.data} />
    </section>
  );
}
