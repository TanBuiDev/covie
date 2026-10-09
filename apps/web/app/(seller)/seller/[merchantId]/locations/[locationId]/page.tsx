import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { getLocationForServer } from "@/features/merchant/merchant-server-api";
import {
  ArrowLeft,
  ArrowRight,
  ChatsCircle,
  Coins,
  Compass,
  CurrencyDollar,
  Storefront,
} from "@phosphor-icons/react/dist/ssr";

export default async function LocationDetailPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const location = await getLocationForServer(merchantId, locationId);
  const base = `/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`;

  return (
    <section className="seller-stack seller-location-detail-page">
      <div className="seller-location-detail-back">
        <Link className="seller-back-link" href={`/seller/${encodeURIComponent(merchantId)}/locations`}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại danh sách điểm bán</span>
        </Link>
      </div>

      <header className="seller-location-detail-hero">
        <div className="seller-location-detail-hero__main">
          <div className="seller-location-detail-hero__icon" aria-hidden="true">
            <Storefront size={24} weight="duotone" />
          </div>
          <div>
            <p className="eyebrow">Seller Center / Điểm bán / Chi tiết</p>
            <h1>{location.data.name ?? "Điểm bán"}</h1>
            {location.data.addressText ? <p className="seller-muted">{location.data.addressText}</p> : null}
          </div>
        </div>
      </header>

      <div className="seller-location-detail-grid">
        <div className="seller-location-detail-card">
          <LocationForm
            key={location.etag || String(location.data.version ?? locationId)}
            merchantId={merchantId}
            location={location}
          />
        </div>

        <section className="seller-location-config-links" aria-labelledby="location-config-heading">
          <div className="seller-location-config-links__head">
            <h2 id="location-config-heading">Cấu hình vận hành</h2>
            <p className="seller-muted">Quản lý phạm vi, phí giao, đơn tối thiểu và kênh liên hệ của điểm bán.</p>
          </div>
          <div className="seller-grid seller-grid--two">
            <Link className="seller-config-link" href={`${base}/delivery`}>
              <div className="seller-config-link__icon-box seller-config-link__icon-box--emerald" aria-hidden="true">
                <Compass size={22} weight="duotone" />
              </div>
              <div className="seller-config-link__content">
                <strong>Phạm vi phục vụ</strong>
                <span>Vùng giao hàng hoặc dịch vụ</span>
              </div>
              <ArrowRight size={16} weight="bold" className="seller-config-link__arrow" aria-hidden="true" />
            </Link>

            <Link className="seller-config-link" href={`${base}/shipping`}>
              <div className="seller-config-link__icon-box seller-config-link__icon-box--amber" aria-hidden="true">
                <CurrencyDollar size={22} weight="duotone" />
              </div>
              <div className="seller-config-link__content">
                <strong>Phí giao hàng</strong>
                <span>Freeship, cố định, theo điều kiện hoặc thỏa thuận</span>
              </div>
              <ArrowRight size={16} weight="bold" className="seller-config-link__arrow" aria-hidden="true" />
            </Link>

            <Link className="seller-config-link" href={`${base}/minimum-order`}>
              <div className="seller-config-link__icon-box seller-config-link__icon-box--teal" aria-hidden="true">
                <Coins size={22} weight="duotone" />
              </div>
              <div className="seller-config-link__content">
                <strong>Đơn tối thiểu</strong>
                <span>Ngưỡng chỉ áp dụng cho giao hàng</span>
              </div>
              <ArrowRight size={16} weight="bold" className="seller-config-link__arrow" aria-hidden="true" />
            </Link>

            <Link className="seller-config-link" href={`${base}/contacts`}>
              <div className="seller-config-link__icon-box seller-config-link__icon-box--purple" aria-hidden="true">
                <ChatsCircle size={22} weight="duotone" />
              </div>
              <div className="seller-config-link__content">
                <strong>Liên hệ</strong>
                <span>Kênh liên hệ của điểm bán</span>
              </div>
              <ArrowRight size={16} weight="bold" className="seller-config-link__arrow" aria-hidden="true" />
            </Link>
          </div>
        </section>
      </div>
    </section>
  );
}
