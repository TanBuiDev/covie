import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";

export default async function SellerSettingsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);

  return (
    <section>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Settings</p>
        <h1>Cài đặt cửa hàng</h1>
        <p>Thay đổi thông tin cửa hàng theo version hiện tại của backend.</p>
      </header>
      <MerchantForm merchant={merchant} />
    </section>
  );
}
