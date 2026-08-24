import { MerchantNeedList } from "@/features/needs/merchant-need-list";

export default async function MerchantNeedsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  return (
    <main style={{ padding: 24 }}>
      <h1>Nhu cầu phù hợp</h1>
      <MerchantNeedList merchantId={merchantId} />
    </main>
  );
}
