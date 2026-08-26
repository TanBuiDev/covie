import { CartList } from "@/features/cart/cart-list";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";
import { CartAppBar } from "@/features/cart/cart-app-bar";
import styles from "@/features/cart/cart-list.module.css";

export default async function CartPage() {
  await requireUser();
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/me/carts");

  if (!response.ok || !data) {
    throw new Error("Không thể tải giỏ hàng.");
  }

  return (
    <div className={styles.screen}>
      <CartAppBar />
      <CartList initialCarts={data} />
    </div>
  );
}
