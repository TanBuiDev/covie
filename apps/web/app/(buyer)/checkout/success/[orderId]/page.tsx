import Link from "next/link";

export default async function CheckoutSuccessPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  return <main className="buyer-surface success-state"><p className="eyebrow">Đặt hàng thành công</p><h1>Đơn hàng đang chờ xác nhận</h1><p>Mã theo dõi: {orderId}</p><Link href="/explore">Tiếp tục khám phá</Link></main>;
}
