import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";

export default async function MerchantNeedsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Need</p>
        <h1>Cần mua phù hợp</h1>
        <p>Chỉ hiển thị khu vực an toàn, khoảng cách và location backend đã resolve; không render địa chỉ hoặc tọa độ riêng của người mua.</p>
      </header>
      <MerchantNeedList merchantId={merchantId} locations={locations} />
    </section>
  );
}
