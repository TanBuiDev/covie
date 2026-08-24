import type { components } from "@covie/api-client";

export type SellerMerchantSummary = components["schemas"]["MerchantSummaryResponse"];

export type SellerRouteContext = {
  merchantId: string;
  merchantName?: string;
  currentUserRole?: SellerMerchantSummary["currentUserRole"];
};

export function sellerBasePath(merchantId: string) {
  return `/seller/${encodeURIComponent(merchantId)}`;
}
