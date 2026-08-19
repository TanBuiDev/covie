import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function LocationsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  await requireUser();
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);
  return <main>
    <Link href={`/seller/${merchantId}`}>← Cửa hàng</Link>
    <h1>Địa điểm</h1>
    <ul>{locations.map((location) => <li key={location.id}>{location.name} — {location.status}</li>)}</ul>
    <LocationForm merchantId={merchantId} />
  </main>;
}
