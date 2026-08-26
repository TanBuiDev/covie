import Link from "next/link";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { LocationList } from "@/features/seller/location-list";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

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
    <section className="seller-stack">
      <Link className="seller-button seller-location-add" href={`${base}/new`}>Thêm điểm bán</Link>
      <LocationList
        merchantId={merchantId}
        locations={locations}
        coverageConfiguredByLocation={coverageConfiguredByLocation}
      />
    </section>
  );
}
