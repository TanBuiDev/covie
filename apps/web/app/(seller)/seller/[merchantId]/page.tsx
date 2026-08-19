import Link from "next/link";
import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function MerchantPage({ params }: { params: Promise<{ merchantId: string }> }) {
  await requireUser();
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);
  return <main>
    <h1>{merchant.data.name}</h1>
    <p>Trạng thái: {merchant.data.status}</p>
    <nav>
      <Link href={`/seller/${merchantId}/locations`}>Địa điểm</Link>{" · "}
      <Link href={`/seller/${merchantId}/catalog`}>Catalog</Link>
    </nav>
    <MerchantForm merchant={merchant} />
  </main>;
}
