import Link from "next/link";
import { ShippingPolicySettings } from "@/features/shipping/shipping-policy-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function ShippingPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies", {
    params: { path: { merchantId, locationId } },
  });
  if (!response.ok || !data) throw await readApiError(response);

  return (
    <section className="seller-stack">
      <p className="seller-desktop-only"><Link href={`/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`}>Quay lại điểm bán</Link></p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Giao hàng</p>
        <h1>Chính sách giao hàng</h1>
        <p>Cấu hình freeship, phí cố định, phí theo điều kiện hoặc thỏa thuận.</p>
      </header>
      <ShippingPolicySettings merchantId={merchantId} locationId={locationId} policies={data} />
    </section>
  );
}
