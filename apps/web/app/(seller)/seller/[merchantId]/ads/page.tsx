import { AdManagement } from "@/features/ads/management/ad-management";
export default async function SellerAdsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  return <AdManagement key={merchantId} scope={{ kind: "seller", merchantId }} />;
}
