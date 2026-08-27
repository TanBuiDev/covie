import { MerchantProfile } from "@/features/merchant/merchant-profile";
import { getPublicMerchantForServer, listPublicMerchantReviewsForServer } from "@/features/merchant/merchant-server-api";

export default async function MerchantProfilePage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const [merchant, reviews] = await Promise.all([
    getPublicMerchantForServer(merchantId),
    listPublicMerchantReviewsForServer(merchantId).catch(() => undefined),
  ]);
  return <MerchantProfile merchant={merchant} reviews={reviews} />;
}
