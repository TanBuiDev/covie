import Link from "next/link";
import { CreditCard } from "@phosphor-icons/react/dist/ssr";
import { MerchantForm } from "@/features/merchant/merchant-form";
import { getMerchantForServer } from "@/features/merchant/merchant-server-api";
import { GearSix } from "@phosphor-icons/react/dist/ssr";

export default async function SellerSettingsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const merchant = await getMerchantForServer(merchantId);

  return (
    <section className="seller-stack seller-settings-page">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Thiết lập</p>
        <div style={{ display: "flex", alignItems: "center", gap: "12px", marginTop: "4px" }}>
          <div
            style={{
              width: "38px",
              height: "38px",
              borderRadius: "10px",
              background: "rgba(4, 120, 87, 0.08)",
              color: "#047857",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              flexShrink: 0,
            }}
          >
            <GearSix size={22} weight="duotone" />
          </div>
          <h1 style={{ margin: 0 }}>Cài đặt shop</h1>
        </div>
        <p>Hồ sơ shop công khai &amp; nhận diện thương hiệu với cư dân CoVie.</p>
      </header>
      <section className="admin-surface">
        <h2><CreditCard size={22} weight="duotone" aria-hidden="true" /> Thuê bao cửa hàng</h2>
        <p>Quản lý gói thuê bao, thời hạn, thanh toán và lịch sử hóa đơn của cửa hàng.</p>
        <Link href={`/seller/${encodeURIComponent(merchantId)}/billing`}>
          Xem và quản lý thuê bao
        </Link>
      </section>
      <MerchantForm key={merchant.etag || String(merchant.data.version ?? "merchant")} merchant={merchant} />
    </section>
  );
}
