import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";
import { MerchantContactSettings } from "@/features/seller/contact-settings";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function SellerSettingsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const client = await createServerReadClient();
  const [merchant, contactResult] = await Promise.all([
    getMerchantForServer(merchantId),
    client.GET("/api/v1/merchants/{merchantId}/contacts", { params: { path: { merchantId } } }),
  ]);
  if (!contactResult.response.ok || !contactResult.data) throw await readApiError(contactResult.response);

  return (
    <section className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Cài đặt</p>
        <h1>Cài đặt cửa hàng</h1>
        <p>Các thay đổi được lưu theo version hiện tại từ backend. Khi có xung đột, CoVie sẽ yêu cầu tải dữ liệu mới thay vì ghi đè im lặng.</p>
      </header>
      <MerchantForm key={merchant.etag || String(merchant.data.version ?? "merchant")} merchant={merchant} />
      <MerchantContactSettings merchantId={merchantId} contacts={contactResult.data} title="Kênh liên hệ chung" />
    </section>
  );
}
