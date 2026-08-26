import Link from "next/link";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import { MerchantContactSettings } from "@/features/seller/contact-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function SellerLocationContactsPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const [location, contactResult] = await Promise.all([
    getLocationForServer(merchantId, locationId),
    client.GET("/api/v1/merchants/{merchantId}/contacts", { params: { path: { merchantId } } }),
  ]);
  if (!contactResult.response.ok || !contactResult.data) throw await readApiError(contactResult.response);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack">
      <p className="seller-muted">{location.data.name ?? "Điểm bán"}</p>
      <nav className="subnav" aria-label="Cấu hình điểm bán">
        <Link href={base}>Thông tin</Link>
        <Link href={`${base}/delivery`}>Vùng phục vụ</Link>
        <Link href={`${base}/shipping`}>Phí giao hàng</Link>
      </nav>
      <MerchantContactSettings
        merchantId={merchantId}
        merchantLocationId={locationId}
        contacts={contactResult.data}
        title="Kênh liên hệ"
      />
    </section>
  );
}
