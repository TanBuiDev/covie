"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { ErrorState } from "@/components/ui/error-state";
import { LoadingState } from "@/components/ui/loading-state";
import { CovieApiError } from "@/lib/api/api-error";
import { getOrder, type OrderDetail } from "@/features/orders/order-api";
import { statusCopy } from "@/lib/format/status-copy";
import { OrderReviewSection } from "./order-review-section";
import styles from "./order-review-page.module.css";

type OrderItem = components["schemas"]["OrderItemResponse"];

function asRecord(value: unknown): Record<string, unknown> | null {
  return value && typeof value === "object" && !Array.isArray(value) ? value as Record<string, unknown> : null;
}

function firstText(value: unknown, keys: string[]): string | undefined {
  const record = asRecord(value);
  if (!record) return undefined;
  for (const key of keys) {
    const candidate = record[key];
    if (typeof candidate === "string" && candidate.trim()) return candidate.trim();
  }
  return undefined;
}

function itemName(item: OrderItem): string {
  return firstText(item.snapshot, ["itemName", "name", "title", "variantName"]) ?? "Mục đơn hàng";
}

function merchantName(order: OrderDetail): string {
  const itemMerchant = order.items?.map((item) => firstText(item.snapshot, ["merchantName", "sellerName", "shopName"])).find(Boolean);
  return itemMerchant ?? "Người bán";
}

export function OrderReviewPage({ orderId }: { orderId: string }) {
  return <OrderReviewContent key={orderId} orderId={orderId} />;
}

function OrderReviewContent({ orderId }: { orderId: string }) {
  const router = useRouter();
  const [order, setOrder] = useState<OrderDetail>();
  const [error, setError] = useState<string>();
  const [isAuthError, setIsAuthError] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const generation = useRef(0);

  const refresh = useCallback(async () => {
    const requestGeneration = ++generation.current;
    try {
      const value = await getOrder(orderId);
      if (requestGeneration !== generation.current) return;
      setOrder(value);
      setError(undefined);
      setIsAuthError(false);
    } catch (cause) {
      if (requestGeneration !== generation.current) return;
      setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
      setError("Không thể tải trạng thái đánh giá. Vui lòng thử lại.");
    }
  }, [orderId]);

  useEffect(() => {
    let active = true;
    const requestState = generation;
    queueMicrotask(() => { if (active) void refresh(); });
    return () => { active = false; requestState.current++; };
  }, [refresh]);

  if (isAuthError) return <ErrorState
    title="Vui lòng đăng nhập lại"
    description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
    action={<Link className="button-primary" href={`/login?returnTo=${encodeURIComponent(`/orders/${encodeURIComponent(orderId)}/review`)}`}>Đăng nhập lại</Link>}
  />;

  if (error && !order) {
    return <ErrorState title="Không thể tải đánh giá" description={error} action={<button type="button" onClick={() => void refresh()}>Thử lại</button>} />;
  }
  if (!order) return <LoadingState label="Đang tải đánh giá…" />;

  const item = order.items?.find((candidate) => candidate.reviewEligibility) ?? order.items?.[0];
  const orderStatus = order.status === "COMPLETED" ? "Hoàn tất" : statusCopy(order.status);

  return (
    <section className={styles.screen} data-consumer-focus>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button>
        <h1>Đánh giá đơn</h1>
      </header>
      <div className={styles.content}>
        <h1 className={styles.desktopTitle}>Đánh giá đơn</h1>
        {error ? <p className={styles.refreshWarning} role="status">Dữ liệu gần nhất vẫn đang hiển thị. Lần làm mới vừa rồi thất bại.</p> : null}
        {item ? (
          <article className={styles.reviewCard}>
            <div className={styles.itemSummary}>
              <strong>{itemName(item)}</strong>
              <span>Đơn {order.orderCode ?? order.id ?? ""} · {merchantName(order)} · {orderStatus}</span>
            </div>
            {submitted ? (
              <section className={styles.submitted} aria-label="Đánh giá đã gửi">
                <h2>Đã gửi đánh giá</h2>
                <p>Cảm ơn bạn đã chia sẻ trải nghiệm. Đánh giá đã được ghi nhận.</p>
                <Link href={`/orders/${encodeURIComponent(orderId)}`}>Xem chi tiết đơn</Link>
              </section>
            ) : (
              <OrderReviewSection item={item} onChanged={async () => { setSubmitted(true); }} onStale={refresh} />
            )}
          </article>
        ) : (
          <section className={styles.unavailable}>
            <h2>Chưa có nội dung để đánh giá</h2>
            <p>Đơn hàng hiện không trả về mục có thể đánh giá.</p>
          </section>
        )}
        <Link className={styles.backLink} href={`/orders/${encodeURIComponent(orderId)}`}>Quay lại chi tiết đơn</Link>
      </div>
    </section>
  );
}
