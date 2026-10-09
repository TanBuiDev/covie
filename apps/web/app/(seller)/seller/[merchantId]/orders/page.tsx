import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { OrderList } from "@/features/orders/order-list";
import { Receipt } from "@phosphor-icons/react/dist/ssr";

export default async function MerchantOrdersPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Đơn đến</p>
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
            <Receipt size={22} weight="duotone" />
          </div>
          <h1 style={{ margin: 0 }}>Đơn hàng</h1>
        </div>
        <p>Theo dõi các đơn mới nhất và lọc theo điểm bán được phân công.</p>
      </header>
      <OrderList merchantId={merchantId} sellerBase={`/seller/${encodeURIComponent(merchantId)}/orders`} locations={locations} />
    </section>
  );
}
