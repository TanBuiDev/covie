"use client";

import { useRouter } from "next/navigation";
import type { SellerMerchantSummary } from "./seller-context";
import { sellerBasePath } from "./seller-context";

export function MerchantSwitcher({ merchants, currentMerchantId }: { merchants: SellerMerchantSummary[]; currentMerchantId: string }) {
  const router = useRouter();
  const available = merchants.filter((merchant) => Boolean(merchant.id));

  if (available.length <= 1) return null;

  return (
    <label className="merchant-switcher">
      Cửa hàng
      <select
        value={currentMerchantId}
        onChange={(event) => {
          const merchantId = event.target.value;
          if (merchantId && merchantId !== currentMerchantId) router.push(sellerBasePath(merchantId));
        }}
      >
        {available.map((merchant) => (
          <option key={merchant.id} value={merchant.id}>{merchant.name ?? "Cửa hàng"}</option>
        ))}
      </select>
    </label>
  );
}
