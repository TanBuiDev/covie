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
      <p className="seller-desktop-only"><Link href={base}>Quay lại điểm bán</Link></p>
      <header className="surface-header seller-desktop-only">
        <p className="eyebrow">Seller Center / Giao hàng</p>
        <h1>Đơn tối thiểu</h1>
        <p>Cấu hình ngưỡng tối thiểu chỉ áp dụng cho đơn giao hàng.</p>
      </header>
      <MinimumOrderForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
    </section>
  );
}
