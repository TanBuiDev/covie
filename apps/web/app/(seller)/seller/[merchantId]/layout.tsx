import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { SellerShell } from "@/components/layout/seller-shell";
import { MerchantSwitcher } from "@/features/seller/merchant-switcher";
import { getMerchantForServer, listMyMerchantsForServer } from "@/features/merchant/merchant-server-api";
import { CovieApiError } from "@/lib/api/api-error";
import { requireUser } from "@/lib/auth/require-user";

export default async function MerchantSellerLayout({
  children,
  params,
}: {
  children: ReactNode;
  params: Promise<{ merchantId: string }>;
}) {
  await requireUser();
  const { merchantId } = await params;

  const merchantAndMerchants = await Promise.all([
    getMerchantForServer(merchantId),
    listMyMerchantsForServer(),
  ]).catch((error: unknown) => {
    if (error instanceof CovieApiError && error.status === 404) redirect("/seller");
    throw error;
  });
  const [merchant, merchants] = merchantAndMerchants;
  return (
    <SellerShell
      merchantId={merchantId}
      merchantStatus={merchant.data.status}
      merchantName={merchant.data.name}
      logoUrl={merchant.data.logoUrl}
      coverUrl={merchant.data.coverUrl}
      switcher={<MerchantSwitcher merchants={merchants} currentMerchantId={merchantId} />}
    >
      {children}
    </SellerShell>
  );
}
