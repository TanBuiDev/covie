"use client";

import { useRouter } from "next/navigation";
import type { SellerMerchantSummary } from "./seller-context";
import { sellerBasePath } from "./seller-context";

export function MerchantSwitcher({ merchants, currentMerchantId }: { merchants: SellerMerchantSummary[]; currentMerchantId: string }) {
  const router = useRouter();
  const available = merchants.filter((merchant) => Boolean(merchant.id));
  const hasAlternative = available.some((merchant) => merchant.id !== currentMerchantId);

  return (
    <label className="merchant-switcher">
      <span>Cửa hàng / Nhà cung cấp</span>
      <select
        aria-label="Cửa hàng / Nhà cung cấp"
        value={currentMerchantId}
        disabled={!hasAlternative}
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
