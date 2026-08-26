import { OrderList } from "@/features/orders/order-list";

export default function OrdersPage() {
  return (
    <main className="orders-surface buyer-orders-surface">
      <header><h1>Đơn hàng của tôi</h1></header>
      <h2 className="buyer-orders-section-title">Đơn gần đây</h2>
      <OrderList />
    </main>
  );
}
