import { SellerDashboard } from "@/features/seller/seller-dashboard";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function MerchantPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const [orderResult, locations] = await Promise.all([
    client.GET("/api/v1/merchants/{merchantId}/orders", { params: { path: { merchantId } } }),
    listLocationsForServer(merchantId),
  ]);

  if (!orderResult.response.ok || !orderResult.data) throw await readApiError(orderResult.response);

  return (
    <SellerDashboard
      merchantId={merchantId}
      orders={orderResult.data.items ?? []}
      locations={locations}
    />
  );
}
