import Link from "next/link";
import { LocationForm } from "@/features/merchant/location-form";

export default async function NewLocationPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const base = `/seller/${encodeURIComponent(merchantId)}/locations`;
  return (
    <section className="seller-stack">
      <p><Link href={base}>Quay lại điểm bán</Link></p>
      <LocationForm merchantId={merchantId} />
    </section>
  );
}
