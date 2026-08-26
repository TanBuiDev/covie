import { SellerDashboard } from "@/features/seller/seller-dashboard";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function MerchantPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const [orderResult, needResult, publicMerchantResult, locations] = await Promise.all([
    client.GET("/api/v1/merchants/{merchantId}/orders", {
      params: { path: { merchantId }, query: { limit: 5 } },
    }),
    client.GET("/api/v1/merchants/{merchantId}/need-requests", {
      params: { path: { merchantId }, query: { limit: 5 } },
    }),
    client.GET("/api/v1/catalog/merchants/{merchantId}", {
      params: { path: { merchantId } },
    }),
    listLocationsForServer(merchantId),
  ]);

  if (!orderResult.response.ok || !orderResult.data) throw await readApiError(orderResult.response);
  if (!needResult.response.ok || !needResult.data) throw await readApiError(needResult.response);
  if (!publicMerchantResult.response.ok && publicMerchantResult.response.status !== 404) {
    throw await readApiError(publicMerchantResult.response);
  }

  return (
    <SellerDashboard
      merchantId={merchantId}
      orders={orderResult.data.items ?? []}
      needMatches={needResult.data.items ?? []}
      locations={locations}
      reputation={publicMerchantResult.data?.reputation}
    />
  );
}
