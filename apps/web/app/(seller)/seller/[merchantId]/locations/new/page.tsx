import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";
import { ArrowLeft } from "@phosphor-icons/react/dist/ssr";

export default async function NewLocationPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const base = `/seller/${encodeURIComponent(merchantId)}/locations`;
  return (
    <section className="seller-stack">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={base}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại điểm bán</span>
        </Link>
      </p>
      <LocationForm merchantId={merchantId} />
    </section>
  );
}
