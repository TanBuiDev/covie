import { CheckoutPreview } from "@/features/checkout/checkout-preview";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

export default async function CheckoutPage() {
  await requireUser();
  const client = await createServerReadClient();
  const [{ data: carts, response: cartResponse }, { data: addresses, response: addressResponse }] = await Promise.all([
    client.GET("/api/v1/me/carts"), client.GET("/api/v1/me/addresses"),
  ]);
  if (!cartResponse.ok || !addressResponse.ok || !carts || !addresses) throw new Error("Không thể chuẩn bị checkout.");
  return <main className="buyer-surface"><header className="surface-header"><p className="eyebrow">CoVie / Buyer</p><h1>Thanh toán</h1></header><CheckoutPreview carts={carts} addresses={addresses} /></main>;
}
