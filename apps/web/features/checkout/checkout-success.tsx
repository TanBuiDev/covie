"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import {
  Check,
  Copy,
  ArrowRight,
  ShoppingBag,
  Bell,
  Clock,
  ShieldCheck,
} from "@phosphor-icons/react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./checkout-success.module.css";

export function CheckoutSuccess({
  orderId,
  orderCode,
}: {
  orderId: string;
  orderCode?: string;
}) {
  const reference = orderCode ?? "Đang cập nhật";
  const [copied, setCopied] = useState(false);

  function copyCode() {
    if (orderCode && typeof navigator !== "undefined" && navigator.clipboard) {
      navigator.clipboard.writeText(orderCode);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  }

  return (
    <div className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/orders" aria-label="Quay lại đơn hàng">
          <CoVieIcon name="back" />
        </Link>
        <h1>Đặt hàng</h1>
      </header>

      <section className={styles.content}>
        <div className={styles.panel}>
          {/* Celebration Icon Header */}
          <div className={styles.heroGlow}>
            <Image
              src="/figma/checkout-success.svg"
              width={64}
              height={64}
              alt=""
              aria-hidden="true"
              priority
              unoptimized
            />
          </div>

          <h2>Đã tạo đơn hàng</h2>

          {/* Order Code Badge with Copy button */}
          <div className={styles.codeRow}>
            <strong>Mã đơn: #{reference}</strong>
            {orderCode && (
              <button
                type="button"
                className={styles.copyButton}
                onClick={copyCode}
                title="Sao chép mã đơn hàng"
                aria-label="Sao chép mã đơn hàng"
              >
                {copied ? <Check size={14} weight="bold" /> : <Copy size={14} weight="regular" />}
                <span>{copied ? "Đã chép" : "Sao chép"}</span>
              </button>
            )}
          </div>

          <p className={styles.description}>
            Cảm ơn bạn đã ủng hộ hàng xóm! Đơn hàng đã được hệ thống ghi nhận và đang chờ người bán xác nhận.
          </p>

          {/* 4-Step Order Journey Timeline */}
          <div className={styles.timeline} aria-label="Tiến trình xử lý đơn hàng">
            <div className={`${styles.timelineStep} ${styles.timelineStepCompleted}`}>
              <div className={styles.timelineDot}>✓</div>
              <span className={styles.timelineLabel}>Đã tạo đơn</span>
            </div>
            <div className={styles.timelineLine} />
            <div className={`${styles.timelineStep} ${styles.timelineStepActive}`}>
              <div className={styles.timelineDot}>
                <Clock size={12} weight="bold" />
              </div>
              <span className={styles.timelineLabel}>Chờ xác nhận</span>
            </div>
            <div className={styles.timelineLine} />
            <div className={styles.timelineStep}>
              <div className={styles.timelineDot}>3</div>
              <span className={styles.timelineLabel}>Đang giao</span>
            </div>
            <div className={styles.timelineLine} />
            <div className={styles.timelineStep}>
              <div className={styles.timelineDot}>4</div>
              <span className={styles.timelineLabel}>Hoàn tất</span>
            </div>
          </div>

          {/* Resident Delivery & Payment Tips */}
          <div className={styles.tipsCard}>
            <div className={styles.tipItem}>
              <Bell size={18} weight="duotone" className={styles.tipIcon} />
              <span>Người bán sẽ liên hệ qua điện thoại hoặc chuông cửa để giao hàng tận sảnh/căn hộ.</span>
            </div>
            <div className={styles.tipItem}>
              <ShieldCheck size={18} weight="duotone" className={styles.tipIcon} />
              <span>Thanh toán COD khi nhận hàng: kiểm tra sản phẩm trước khi chuyển khoản hoặc trả tiền mặt.</span>
            </div>
          </div>

          {/* Action CTAs */}
          <div className={styles.actions}>
            <Link className={styles.primary} href={`/orders/${encodeURIComponent(orderId)}`}>
              <span>Xem đơn hàng</span>
              <ArrowRight size={16} weight="bold" />
            </Link>
            <Link className={styles.secondary} href="/search">
              <ShoppingBag size={18} weight="duotone" />
              <span>Tiếp tục mua sắm</span>
            </Link>
          </div>
        </div>

        <p className={styles.note}>
          Theo dõi trạng thái mới nhất trong trang chi tiết đơn hàng.
        </p>
      </section>
    </div>
  );
}
