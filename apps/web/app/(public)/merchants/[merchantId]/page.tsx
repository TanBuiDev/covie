import { MerchantProfile } from "@/features/merchant/merchant-profile";
import {
  getPublicMerchantForServer,
  listPublicMerchantCatalogForServer,
  listPublicMerchantReviewsForServer,
} from "@/features/merchant/merchant-server-api";

export default async function MerchantProfilePage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const [merchant, reviews, catalog] = await Promise.all([
    getPublicMerchantForServer(merchantId),
    listPublicMerchantReviewsForServer(merchantId).catch(() => undefined),
    listPublicMerchantCatalogForServer(merchantId).catch(() => undefined),
  ]);
  return <MerchantProfile merchant={merchant} reviews={reviews} catalog={catalog} />;
}
