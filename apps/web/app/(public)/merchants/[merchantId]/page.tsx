import { MerchantProfile } from "@/features/merchant/merchant-profile";
import { getPublicMerchantForServer, listPublicMerchantCatalogForServer, listPublicMerchantReviewsForServer } from "@/features/merchant/merchant-server-api";

export default async function MerchantProfilePage({ params, searchParams }: {
  params: Promise<{ merchantId: string }>;
  searchParams: Promise<{ catalogCursor?: string }>;
}) {
  const { merchantId } = await params;
  const { catalogCursor } = await searchParams;
  const [merchant, catalogResult, reviews] = await Promise.all([
    getPublicMerchantForServer(merchantId),
    listPublicMerchantCatalogForServer(merchantId, catalogCursor)
      .then((catalog) => ({ catalog, catalogError: false }))
      .catch(() => ({ catalog: undefined, catalogError: true })),
    listPublicMerchantReviewsForServer(merchantId).catch(() => undefined),
  ]);
  return <MerchantProfile merchant={merchant} reviews={reviews} catalog={catalogResult.catalog}
    catalogError={catalogResult.catalogError} catalogCursor={catalogCursor} />;
}
