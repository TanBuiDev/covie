import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";

export default async function MerchantNeedsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Nhu cầu</p>
        <h1>Cần mua phù hợp</h1>
        <p>Xem các nhu cầu phù hợp với điểm bán mà không hiển thị địa chỉ hoặc tọa độ riêng của người mua.</p>
      </header>
      <MerchantNeedList merchantId={merchantId} locations={locations} />
    </section>
  );
}
