import { listLocationsForServer } from "@/features/merchant/merchant-server-api";
import { OrderList } from "@/features/orders/order-list";

export default async function MerchantOrdersPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const locations = await listLocationsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Đơn đến</p>
        <h1>Đơn hàng</h1>
        <p>Danh sách cursor mới nhất từ backend. Không lọc theo status ở client vì trang chưa tải không được xem là dữ liệu đầy đủ.</p>
      </header>
      <OrderList merchantId={merchantId} sellerBase={`/seller/${encodeURIComponent(merchantId)}/orders`} locations={locations} />
    </section>
  );
}
