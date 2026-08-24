import { OrderDetailView } from "@/features/orders/order-detail";

export default async function MerchantOrderPage({
  params,
}: {
  params: Promise<{ merchantId: string; orderId: string }>;
}) {
  const { merchantId, orderId } = await params;
  return <OrderDetailView orderId={orderId} sellerMerchantId={merchantId} />;
}
