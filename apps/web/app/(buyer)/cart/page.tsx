import { CartList } from "@/features/cart/cart-list";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

export default async function CartPage() {
  await requireUser();
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/carts");

  if (!response.ok || !data) {
    throw new Error("Không thể tải giỏ hàng.");
  }

  return (
    <main className="buyer-surface">
      <header className="surface-header">
        <p className="eyebrow">CoVie / Buyer</p>
        <h1>Giỏ hàng</h1>
        <p>Mỗi điểm bán và loại sản phẩm/dịch vụ được đặt trong một giỏ riêng.</p>
      </header>
      <CartList initialCarts={data} />
    </main>
  );
}
