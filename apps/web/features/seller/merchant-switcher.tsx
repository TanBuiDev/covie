"use client";

import { useRouter } from "next/navigation";
import type { SellerMerchantSummary } from "./seller-context";
import { sellerBasePath } from "./seller-context";
import { SelectDropdown } from "@/components/ui/select-dropdown";

export function MerchantSwitcher({ merchants, currentMerchantId }: { merchants: SellerMerchantSummary[]; currentMerchantId: string }) {
  const router = useRouter();
  const available = merchants.filter((merchant) => Boolean(merchant.id));
  const hasAlternative = available.some((merchant) => merchant.id !== currentMerchantId);

  return (
    <div className="merchant-switcher">
      <SelectDropdown<string>
        label="Cửa hàng / Nhà cung cấp"
        aria-label="Cửa hàng / Nhà cung cấp"
        value={currentMerchantId}
        disabled={!hasAlternative}
        radius="md"
        size="sm"
        options={available
          .filter((merchant) => Boolean(merchant.id))
          .map((merchant) => ({
            value: merchant.id!,
            label: merchant.name ?? "Cửa hàng",
          }))}
        onChange={(merchantId) => {
          if (merchantId && merchantId !== currentMerchantId) {
            router.push(sellerBasePath(merchantId));
          }
        }}
      />
    </div>
  );
}
