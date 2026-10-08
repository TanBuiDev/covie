import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { OrderList } from "@/features/orders/order-list";
import styles from "./orders-mobile.module.css";

export default function OrdersPage() {
  return (
    <main className={`${styles.surface} orders-surface buyer-orders-surface`}>
      <header className={styles.appBar}>
        <Link className={styles.back} href="/" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Đơn hàng của tôi</h1>
      </header>
      <header className={styles.desktopHeader}>
        <div className="buyer-orders-page-header">
          <h1>Đơn hàng của tôi</h1>
          <p>Theo dõi tiến trình và lịch sử mua sắm nội khu</p>
        </div>
      </header>
      <div className={styles.body}>
        <OrderList />
      </div>
    </main>
  );
}
