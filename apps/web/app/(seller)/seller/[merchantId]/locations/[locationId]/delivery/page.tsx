import Link from "next/link";
import { CoverageForm } from "@/features/shipping/coverage-form";

export default async function DeliveryPage({ params }: { params: Promise<{ merchantId: string; locationId: string }> }) {
  const { merchantId, locationId } = await params;
  return (
    <section>
      <p><Link href={`/seller/${merchantId}/locations/${locationId}`}>← Điểm bán</Link></p>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Delivery coverage</p>
        <h1>Vùng giao hàng & phục vụ</h1>
        <p>Cấu hình vùng theo bán kính hoặc các khu vực backend đã định nghĩa.</p>
      </header>
      <CoverageForm merchantId={merchantId} locationId={locationId} />
    </section>
  );
}
