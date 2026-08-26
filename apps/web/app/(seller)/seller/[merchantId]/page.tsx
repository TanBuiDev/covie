import { SellerDashboard } from "@/features/seller/seller-dashboard";
import { getMerchantForServer, listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function MerchantPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const [orderResult, needResult, merchant, locations] = await Promise.all([
    client.GET("/api/v1/merchants/{merchantId}/orders", {
      params: { path: { merchantId }, query: { limit: 5 } },
    }),
    client.GET("/api/v1/merchants/{merchantId}/need-requests", {
      params: { path: { merchantId }, query: { limit: 5 } },
    }),
    getMerchantForServer(merchantId),
    listLocationsForServer(merchantId),
  ]);

  if (!orderResult.response.ok || !orderResult.data) throw await readApiError(orderResult.response);
  if (!needResult.response.ok || !needResult.data) throw await readApiError(needResult.response);

  return (
    <SellerDashboard
      merchantId={merchantId}
      merchantName={merchant.data.name}
      merchantStatus={merchant.data.status}
      orders={orderResult.data.items ?? []}
      needMatches={needResult.data.items ?? []}
      locations={locations}
    />
  );
}
