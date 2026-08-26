"use client";

import type { components } from "@covie/api-client";
import { useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { formatMoney } from "@/lib/format/money";
import { ShippingPolicyForm } from "./shipping-policy-form";

type Policy = components["schemas"]["ShippingPolicyResponse"];

function policyTitle(mode?: Policy["mode"]) {
  switch (mode) {
    case "FREE": return "Miễn phí giao hàng";
    case "FIXED": return "Phí cố định";
    case "NEGOTIATED": return "Thỏa thuận với người mua";
    case "CONDITIONAL": return "Miễn phí có điều kiện";
    default: return "Chính sách giao hàng";
  }
}

function policyStatus(status?: Policy["status"]) {
  if (status === "ACTIVE") return { label: "Đang hoạt động", tone: "success" as const };
  if (status === "INACTIVE") return { label: "Tạm ngưng", tone: "neutral" as const };
  return { label: "Trạng thái đã được cập nhật", tone: "neutral" as const };
}

export function ShippingPolicySettings({
  merchantId,
  locationId,
  policies,
}: {
  merchantId: string;
  locationId: string;
  policies: Policy[];
}) {
  const [editingId, setEditingId] = useState<string>();
  const editing = useMemo(() => policies.find((policy) => policy.id === editingId), [policies, editingId]);

  return (
    <div className="seller-stack">
      <section className="seller-section" aria-labelledby="shipping-list-heading">
        <div className="seller-section__heading"><h2 id="shipping-list-heading">Chính sách hiện có</h2></div>
        {policies.length ? (
          <div className="seller-row-list">
            {policies.map((policy, index) => {
              const status = policyStatus(policy.status);
              return (
                <article className="seller-row" key={policy.id ?? `${policy.mode ?? "policy"}-${index}`}>
                  <div>
                    <strong>{policyTitle(policy.mode)}</strong>
                    {policy.mode === "NEGOTIATED" ? <small>Thỏa thuận với người bán</small> : null}
                    {policy.mode !== "NEGOTIATED" && policy.baseFee != null && policy.currencyCode ? (
                      <small>Phí cơ bản: {formatMoney({ amount: policy.baseFee, currency: policy.currencyCode })}</small>
                    ) : null}
                    {policy.mode === "CONDITIONAL" ? (
                      <small>{policy.ruleMatchMode === "ALL" ? "Tất cả điều kiện phải khớp" : "Bất kỳ điều kiện nào khớp"} · {policy.rules?.length ?? 0} điều kiện</small>
                    ) : null}
                  </div>
                  <div className="seller-row__actions">
                    <StatusChip label={status.label} tone={status.tone} />
                    {policy.id ? <button type="button" onClick={() => setEditingId(policy.id)}>Sửa</button> : null}
                  </div>
                </article>
              );
            })}
          </div>
        ) : (
          <EmptyState title="Chưa có chính sách giao hàng" description="Thêm chính sách mới để checkout có thể tính hoặc mô tả phí theo contract backend." />
        )}
      </section>

      {editing ? (
        <ShippingPolicyForm
          key={`${editing.id ?? "policy"}-${editing.version ?? "version"}`}
          merchantId={merchantId}
          locationId={locationId}
          policy={editing}
          onCancelEdit={() => setEditingId(undefined)}
        />
      ) : (
        <ShippingPolicyForm merchantId={merchantId} locationId={locationId} />
      )}
    </div>
  );
}
