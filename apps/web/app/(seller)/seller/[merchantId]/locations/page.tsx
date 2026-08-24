import { LocationForm } from "@/features/merchant/location-form";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { LocationList } from "@/features/seller/location-list";

export default async function LocationsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Locations</p>
        <h1>Điểm bán & phục vụ</h1>
        <p>Mỗi location là một ranh giới vận hành riêng cho giá, giao hàng, tồn kho và quyền nhân viên.</p>
      </header>
      <LocationList merchantId={merchantId} locations={locations} />
      <LocationForm merchantId={merchantId} />
    </section>
  );
}
