import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";

export default async function LocationDetailPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack">
      <p className="seller-desktop-only"><Link href={`/seller/${encodeURIComponent(merchantId)}/locations`}>Quay lại danh sách điểm bán</Link></p>
      <LocationForm
        key={location.etag || String(location.data.version ?? locationId)}
        merchantId={merchantId}
        location={location}
      />
      <section className="seller-location-config-links" aria-labelledby="location-config-heading">
        <div>
          <h2 id="location-config-heading">Cấu hình vận hành</h2>
          <p className="seller-muted">Quản lý phạm vi, phí giao, đơn tối thiểu và kênh liên hệ của điểm bán.</p>
        </div>
        <div className="seller-grid seller-grid--two">
          <Link className="seller-config-link" href={`${base}/delivery`}><strong>Phạm vi phục vụ</strong><span>Vùng giao hàng hoặc dịch vụ</span></Link>
          <Link className="seller-config-link" href={`${base}/shipping`}><strong>Phí giao hàng</strong><span>Freeship, cố định, theo điều kiện hoặc thỏa thuận</span></Link>
          <Link className="seller-config-link" href={`${base}/minimum-order`}><strong>Đơn tối thiểu</strong><span>Ngưỡng chỉ áp dụng cho giao hàng</span></Link>
          <Link className="seller-config-link" href={`${base}/contacts`}><strong>Liên hệ</strong><span>Kênh liên hệ của điểm bán</span></Link>
        </div>
      </section>
    </section>
  );
}
