import Link from "next/link";
import { MinimumOrderForm } from "@/features/merchant/minimum-order-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";

export default async function MinimumOrderPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;
  return (
    <section className="seller-stack">
      <p><Link href={base}>Quay lại điểm bán</Link></p>
      <MinimumOrderForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
    </section>
  );
}
