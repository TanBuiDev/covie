import { OrderReviewPage } from "@/features/reviews/order-review-page";

export default async function ReviewPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  return <main className="orders-surface"><OrderReviewPage orderId={orderId} /></main>;
}
