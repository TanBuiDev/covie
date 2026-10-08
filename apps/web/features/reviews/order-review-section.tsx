import type { components } from "@covie/api-client";
import Link from "next/link";
import { ReviewForm } from "./review-form";
import styles from "./order-review-section.module.css";

type OrderItem = components["schemas"]["OrderItemResponse"];
type Reason = NonNullable<NonNullable<OrderItem["reviewEligibility"]>["reason"]>;

const blockedCopy: Record<Exclude<Reason, "ELIGIBLE">, { title: string; description: string }> = {
  ALREADY_REVIEWED: { title: "Bạn đã đánh giá nội dung này", description: "Bạn có thể xem lại đánh giá đã gửi từ chi tiết đơn." },
  CREATION_DISABLED: { title: "Hệ thống đánh giá đang tạm tắt", description: "CoVie hiện không nhận đánh giá mới. Trạng thái sẽ được cập nhật từ hệ thống." },
  ORDER_NOT_COMPLETED: { title: "Đánh giá sẽ mở khi đơn hoàn tất", description: "Hãy quay lại khi giao dịch đã được hệ thống ghi nhận hoàn tất." },
  ITEM_NOT_REVIEWABLE: { title: "Nội dung này không hỗ trợ đánh giá", description: "Mục đơn hàng này không thuộc phạm vi đánh giá hiện tại." },
};

export function OrderReviewSection({ item, onChanged, onStale = onChanged, orderId }: { item: OrderItem; onChanged: () => Promise<void>; onStale?: () => Promise<void>; orderId?: string }) {
  const eligibility = item.reviewEligibility;
  if (!eligibility) return null;
  if (eligibility.eligible && eligibility.reason === "ELIGIBLE" && eligibility.orderItemId) {
    if (orderId) return <section className={styles.entry} aria-label="Đánh giá đơn"><Link href={`/orders/${encodeURIComponent(orderId)}/review`}>Đánh giá đơn</Link></section>;
    return <section className={styles.section} aria-label="Đánh giá đơn"><ReviewForm orderItemId={eligibility.orderItemId} onCreated={onChanged} onStale={onStale} /></section>;
  }
  const copy = eligibility.reason && eligibility.reason !== "ELIGIBLE" ? blockedCopy[eligibility.reason] : blockedCopy.ITEM_NOT_REVIEWABLE;
  return <section className={styles.blocked} aria-label="Trạng thái đánh giá"><h3>{copy.title}</h3><p>{copy.description}</p></section>;
}
