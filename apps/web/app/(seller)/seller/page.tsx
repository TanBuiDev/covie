import { MerchantForm, MerchantList } from "@/features/merchant/merchant-form";
import { listMyMerchantsForServer } from "@/features/merchant/merchant-server-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function SellerPage() {
  await requireUser();
  const merchants = await listMyMerchantsForServer();
  return <main>
    <h1>Quản lý bán hàng</h1>
    {merchants.length === 0 ? <p>Bạn chưa có cửa hàng nào.</p> : <MerchantList merchants={merchants} />}
    <MerchantForm />
  </main>;
}
