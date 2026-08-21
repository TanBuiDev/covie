import { CoverageForm } from "@/features/shipping/coverage-form";
import { ShippingPolicyForm } from "@/features/shipping/shipping-policy-form";
import { requireUser } from "@/lib/auth/require-user";

export default async function DeliveryPage({ params }: { params: Promise<{ merchantId: string; locationId: string }> }) {
  await requireUser(); const { merchantId, locationId } = await params;
  return <main className="seller-surface"><header className="surface-header"><p className="eyebrow">CoVie / Seller</p><h1>Giao hàng & phục vụ</h1></header><CoverageForm merchantId={merchantId} locationId={locationId} /><ShippingPolicyForm merchantId={merchantId} locationId={locationId} /></main>;
}
