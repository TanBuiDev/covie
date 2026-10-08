import { CheckoutSuccess } from "@/features/checkout/checkout-success";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

export default async function CheckoutSuccessPage({ params }: { params: Promise<{ orderId: string }> }) {
  const { orderId } = await params;
  await requireUser(`/checkout/success/${encodeURIComponent(orderId)}`);
  const client = await createServerReadClient();
  const { data } = await client.GET("/api/v1/orders/{orderId}", { params: { path: { orderId } } });
  return <main><CheckoutSuccess orderId={orderId} orderCode={data?.orderCode} /></main>;
}
