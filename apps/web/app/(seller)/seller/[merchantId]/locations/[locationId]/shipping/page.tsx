import Link from "next/link";
import { ShippingPolicyForm } from "@/features/shipping/shipping-policy-form";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { formatMoney } from "@/lib/format/money";

export default async function ShippingPage({
  params,
}: {
  params: Promise<{ merchantId: string; locationId: string }>;
}) {
  const { merchantId, locationId } = await params;
  const client = await createServerReadClient();
  const { data, response } = await client.GET("/api/v1/merchants/{merchantId}/locations/{locationId}/shipping-policies", {
    params: { path: { merchantId, locationId } },
  });
  if (!response.ok || !data) throw await readApiError(response);

  return (
    <section>
      <p><Link href={`/seller/${merchantId}/locations/${locationId}`}>← Điểm bán</Link></p>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Shipping</p>
        <h1>Chính sách giao hàng</h1>
      </header>
      {data.length ? (
        <ul className="management-list">
          {data.map((policy) => (
            <li key={policy.id ?? `${policy.mode}-${policy.version}`}>
              <div>
                <strong>{policy.mode === "NEGOTIATED" ? "Thỏa thuận với người bán" : policy.mode ?? "Chính sách"}</strong>
                {policy.baseFee != null && policy.currencyCode ? <small>{formatMoney({ amount: policy.baseFee, currency: policy.currencyCode })}</small> : null}
                {policy.mode === "CONDITIONAL" ? <small>Khớp điều kiện: {policy.ruleMatchMode === "ALL" ? "Tất cả" : "Bất kỳ"}</small> : null}
              </div>
            </li>
          ))}
        </ul>
      ) : <p>Chưa có chính sách giao hàng.</p>}
      <ShippingPolicyForm merchantId={merchantId} locationId={locationId} />
    </section>
  );
}
