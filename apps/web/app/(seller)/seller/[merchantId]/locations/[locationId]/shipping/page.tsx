import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { ShippingPolicyForm } from "@/features/shipping/shipping-policy-form";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { formatMoney } from "@/lib/format/money";

function policyTitle(mode?: string) {
  switch (mode) {
    case "FREE": return "Miễn phí giao hàng";
    case "FIXED": return "Phí cố định";
    case "NEGOTIATED": return "Thỏa thuận với người mua";
    case "CONDITIONAL": return "Miễn phí có điều kiện";
    default: return "Chính sách giao hàng";
  }
}

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
    <section className="seller-stack">
      <p><Link href={`/seller/${encodeURIComponent(merchantId)}/locations/${encodeURIComponent(locationId)}`}>Quay lại điểm bán</Link></p>
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Giao hàng</p>
        <h1>Chính sách giao hàng</h1>
        <p>Phí và điều kiện dưới đây được render trực tiếp từ contract backend; “thỏa thuận” không được hiển thị thành 0đ.</p>
      </header>

      <section className="seller-section" aria-labelledby="shipping-list-heading">
        <div className="seller-section__heading"><h2 id="shipping-list-heading">Chính sách hiện có</h2></div>
        {data.length ? (
          <div className="seller-row-list">
            {data.map((policy, index) => (
              <div className="seller-row" key={policy.id ?? `${policy.mode ?? "policy"}-${index}`}>
                <div>
                  <strong>{policyTitle(policy.mode)}</strong>
                  {policy.mode !== "NEGOTIATED" && policy.baseFee != null && policy.currencyCode ? (
                    <small>Phí cơ bản: {formatMoney({ amount: policy.baseFee, currency: policy.currencyCode })}</small>
                  ) : null}
                  {policy.mode === "CONDITIONAL" ? (
                    <small>{policy.ruleMatchMode === "ALL" ? "Tất cả điều kiện phải khớp" : "Bất kỳ điều kiện nào khớp"} · {policy.rules?.length ?? 0} điều kiện</small>
                  ) : null}
                </div>
                <StatusChip status={policy.status} />
              </div>
            ))}
          </div>
        ) : (
          <EmptyState title="Chưa có chính sách giao hàng" description="Thêm chính sách mới để checkout có thể tính hoặc mô tả phí theo contract backend." />
        )}
      </section>

      <ShippingPolicyForm merchantId={merchantId} locationId={locationId} />
    </section>
  );
}
