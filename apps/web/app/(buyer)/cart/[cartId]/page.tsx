import Link from "next/link";
import { CartDetail } from "@/features/cart/cart-detail";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

export default async function CartDetailPage({ params }: { params: Promise<{ cartId: string }> }) {
  const { cartId } = await params;
  await requireUser(`/cart/${encodeURIComponent(cartId)}`);
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/carts/{cartId}", {
    params: { path: { cartId } },
  });

  if (!response.ok || !data) {
    return (
      <main className="buyer-surface">
        <section className="error-state" role="alert">
          <h1>Không thể mở giỏ hàng này</h1>
          <p>Giỏ có thể đã thay đổi hoặc không còn khả dụng.</p>
          <Link href="/cart">Quay lại danh sách giỏ</Link>
        </section>
      </main>
    );
  }

  return <main><CartDetail initialCart={data} /></main>;
}
