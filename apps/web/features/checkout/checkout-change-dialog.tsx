"use client";

import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./checkout-preview.module.css";

export function CheckoutChangeDialog({ onPreviewAgain }: { onPreviewAgain(): void }) {
  return (
    <section role="alertdialog" aria-label="Đơn hàng đã thay đổi" className={styles.changed}>
      <div className={styles.warning}>
        <CoVieIcon name="feedback-warning" />
        <div>
          <h2>Giá hoặc thông tin đã thay đổi</h2>
          <p>CoVie chưa đặt đơn. Hãy kiểm tra thông tin mới trước khi tiếp tục.</p>
        </div>
      </div>
      <button type="button" onClick={onPreviewAgain}>Kiểm tra giá/thông tin mới</button>
      <p>Hệ thống sẽ kiểm tra lại giá và thông tin mới trước khi bạn tiếp tục.</p>
    </section>
  );
}
