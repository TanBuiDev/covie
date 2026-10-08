import Image from "next/image";
import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./checkout-success.module.css";

export function CheckoutSuccess({ orderId, orderCode }: { orderId: string; orderCode?: string }) {
  const reference = orderCode ?? "Đang cập nhật";
  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/orders" aria-label="Quay lại đơn hàng"><CoVieIcon name="back" /></Link>
        <h1>Đặt hàng</h1>
      </header>
      <section className={styles.content}>
        <div className={styles.panel}>
          <Image src="/figma/checkout-success.svg" width={56} height={56} alt="" aria-hidden="true" />
          <h2>Đã tạo đơn hàng</h2>
          <strong>Mã đơn: #{reference}</strong>
          <p>Đơn hàng đã được hệ thống ghi nhận và đang chờ người bán xác nhận.</p>
          <Link className={styles.primary} href={`/orders/${encodeURIComponent(orderId)}`}>Xem đơn hàng</Link>
          <Link className={styles.secondary} href="/search">Tiếp tục mua sắm</Link>
        </div>
        <p className={styles.note}>Theo dõi trạng thái mới nhất trong trang chi tiết đơn hàng.</p>
      </section>
    </div>
  );
}
