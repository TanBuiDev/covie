import { LocationForm } from "@/features/merchant/location-form";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { LocationList } from "@/features/seller/location-list";

export default async function LocationsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Điểm bán</p>
        <h1>Điểm bán & phục vụ</h1>
        <p>Mỗi điểm bán giữ phạm vi vận hành riêng cho giao hàng, pickup, dịch vụ, tồn kho và phân quyền backend.</p>
      </header>
      <section className="seller-section" aria-labelledby="locations-list-heading">
        <div className="seller-section__heading"><h2 id="locations-list-heading">Điểm bán hiện có</h2></div>
        <LocationList merchantId={merchantId} locations={locations} />
      </section>
      <LocationForm merchantId={merchantId} />
    </section>
  );
}
