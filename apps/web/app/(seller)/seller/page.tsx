import { redirect } from "next/navigation";
import { MerchantForm, MerchantList } from "@/features/merchant/merchant-form";
import { listMyMerchantsForServer } from "@/features/merchant/merchant-server-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function SellerPage() {
  await requireUser();
  const merchants = await listMyMerchantsForServer();

  if (merchants.length === 1 && merchants[0]?.id) {
    redirect(`/seller/${encodeURIComponent(merchants[0].id)}`);
  }

  return (
    <main className="buyer-surface">
      <header className="surface-header">
        <p className="eyebrow">CoVie Seller</p>
        <h1>Seller Center</h1>
      </header>
      {merchants.length === 0 ? (
        <section>
          <h2>Bán hàng trên CoVie</h2>
          <p>Tạo cửa hàng đầu tiên để bắt đầu cấu hình điểm bán và catalog.</p>
          <MerchantForm />
        </section>
      ) : (
        <section>
          <h2>Chọn cửa hàng</h2>
          <MerchantList merchants={merchants} />
        </section>
      )}
    </main>
  );
}
