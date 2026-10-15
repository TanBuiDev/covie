import { getMerchantForServer } from "@/features/merchant/merchant-server-api";
import { SellerBillingPanel } from "@/features/seller/seller-billing-panel";

export default async function SellerBillingPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);
  return <SellerBillingPanel merchantId={merchantId} merchantName={merchant.data.name} />;
}
