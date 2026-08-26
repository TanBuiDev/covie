import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";

export default async function SellerSettingsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);

  return (
    <section className="seller-stack seller-settings-page">
      <header className="seller-workspace-header">
        <h1>Cài đặt shop</h1>
        <p>Hồ sơ shop &amp; phương thức liên hệ</p>
      </header>
      <MerchantForm key={merchant.etag || String(merchant.data.version ?? "merchant")} merchant={merchant} />
    </section>
  );
}
