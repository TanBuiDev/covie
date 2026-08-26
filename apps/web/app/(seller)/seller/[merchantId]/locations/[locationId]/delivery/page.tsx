import Link from "next/link";
import { CoverageSettings } from "@/features/shipping/coverage-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function DeliveryPage({ params }: { params: Promise<{ merchantId: string; locationId: string }> }) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/delivery-coverages", {
    params: { path: { merchantId, locationId } },
  });
  if (!response.ok || !data) throw await readApiError(response);

  return (
    <section className="seller-stack">
      <p><Link href={`/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`}>Quay lại điểm bán</Link></p>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Vùng phục vụ</p>
        <h1>Vùng giao hàng & phục vụ</h1>
        <p>Bán kính và khu vực có thể được kết hợp khi backend cho phép. Mọi chỉnh sửa dùng version hiện tại làm If-Match.</p>
      </header>
      <CoverageSettings merchantId={merchantId} locationId={locationId} coverages={data} />
    </section>
  );
}
