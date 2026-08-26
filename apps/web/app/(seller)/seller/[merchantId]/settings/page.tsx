import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";

export default async function SellerSettingsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Cài đặt</p>
        <h1>Cài đặt cửa hàng</h1>
        <p>Các thay đổi được lưu theo version hiện tại từ backend. Khi có xung đột, CoVie sẽ yêu cầu tải dữ liệu mới thay vì ghi đè im lặng.</p>
      </header>
      <MerchantForm key={merchant.etag || String(merchant.data.version ?? "merchant")} merchant={merchant} />
    </section>
  );
}
