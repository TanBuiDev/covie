import Link from "next/link";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { LocationList } from "@/features/seller/location-list";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { PlusCircle } from "@phosphor-icons/react/dist/ssr";

export default async function LocationsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);
  const client = await createServerReadClient();
  const coverageEntries = await Promise.all(locations.map(async (location) => {
    if (!location.id) return ["", false] as const;
    const { data, response } = await client.GET(
      "/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages",
      { params: { path: { merchantId, locationId: location.id } } },
    );
    if (!response.ok || !data) throw await readApiError(response);
    return [location.id, data.some((coverage) => coverage.status === "ACTIVE")] as const;
  }));
  const coverageConfiguredByLocation = Object.fromEntries(coverageEntries.filter(([id]) => Boolean(id)));
  const base = `/seller/${encodeURIComponent(merchantId)}/locations`;

  return (
    <section className="seller-stack seller-locations-page">
      <header className="seller-workspace-header seller-workspace-header--action">
        <div className="seller-desktop-only">
          <p className="eyebrow">Seller Center / Điểm bán</p>
          <h1>Điểm bán</h1>
          <p>Nơi phục vụ, giao hàng &amp; pickup</p>
        </div>
        <Link className="seller-button seller-location-add" href={`${base}/new`}>
          <PlusCircle size={18} weight="bold" aria-hidden="true" />
          <span>Thêm điểm bán</span>
        </Link>
      </header>
      <p className="seller-location-count">{locations.length.toLocaleString("vi-VN")} điểm bán</p>
      <LocationList
        merchantId={merchantId}
        locations={locations}
        coverageConfiguredByLocation={coverageConfiguredByLocation}
      />
    </section>
  );
}
