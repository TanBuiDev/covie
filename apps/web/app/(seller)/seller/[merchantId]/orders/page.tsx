import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { OrderList } from "@/features/orders/order-list";

export default async function MerchantOrdersPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Đơn đến</p>
        <h1>Đơn hàng</h1>
        <p>Theo dõi các đơn mới nhất và lọc theo điểm bán được phân công.</p>
      </header>
      <OrderList merchantId={merchantId} sellerBase={`/seller/${encodeURIComponent(merchantId)}/orders`} locations={locations} />
    </section>
  );
}
