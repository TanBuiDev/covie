import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";

export default async function LocationDetailPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Location</p>
        <h1>{location.data.name ?? "Điểm bán"}</h1>
        <p>{location.data.addressText}</p>
      </header>
      <nav className="subnav" aria-label="Cấu hình điểm bán">
        <Link href={`${base}/delivery`}>Vùng giao</Link>
        <Link href={`${base}/shipping`}>Phí giao</Link>
      </nav>
      <LocationForm merchantId={merchantId} location={location} />
    </section>
  );
}
