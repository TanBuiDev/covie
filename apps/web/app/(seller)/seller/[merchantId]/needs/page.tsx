import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";
import { Target } from "@phosphor-icons/react/dist/ssr";

export default async function MerchantNeedsPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Nhu cầu</p>
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
            <Target size={22} weight="duotone" />
          </div>
          <h1 style={{ margin: 0 }}>Cần mua phù hợp</h1>
        </div>
        <p>Xem các nhu cầu phù hợp với điểm bán mà không hiển thị địa chỉ hoặc tọa độ riêng của người mua.</p>
      </header>
      <MerchantNeedList merchantId={merchantId} locations={locations} />
    </section>
  );
}
