"use client";

import type { components } from "@covie/api-client";
import { useMemo, useState } from "react";
import { StatusChip } from "@/components/ui/status-chip";
import { formatMoney } from "@/lib/format/money";
import { ShippingPolicyForm } from "./shipping-policy-form";

type Policy = components["schemas"]["ShippingPolicyResponse"];

function policyTitle(mode?: Policy["mode"]) {
  switch (mode) {
    case "FREE": return "Freeship";
    case "FIXED": return "Cố định";
    case "NEGOTIATED": return "Thỏa thuận";
    case "CONDITIONAL": return "Theo điều kiện";
    default: return "Chính sách giao hàng đã được cập nhật";
  }
}

function policyStatus(status?: Policy["status"]) {
  if (status === "ACTIVE") return { label: "Đang hoạt động", tone: "success" as const };
  if (status === "INACTIVE") return { label: "Tạm ngưng", tone: "neutral" as const };
  return { label: "Trạng thái đã được cập nhật", tone: "neutral" as const };
}

function ruleMatchCopy(mode?: Policy["ruleMatchMode"]) {
  switch (mode) {
    case "ANY": return "Bất kỳ điều kiện nào khớp";
    case "ALL": return "Tất cả điều kiện phải khớp";
    default: return "Logic điều kiện đã được cập nhật";
  }
}

export function ShippingPolicySettings({
  merchantId,
  locationId,
  policies,
  locationName,
}: {
  merchantId: string;
  locationId: string;
  policies: Policy[];
  locationName?: string;
}) {
  const [editingId, setEditingId] = useState<string>();
  const editing = useMemo(() => policies.find((policy) => policy.id === editingId), [policies, editingId]);

  if (policies.length <= 1) {
    const policy = policies[0];
    return (
      <ShippingPolicyForm
        key={`${policy?.id ?? "new"}-${policy?.version ?? "version"}`}
        merchantId={merchantId}
        locationId={locationId}
        policy={policy}
        locationName={locationName}
      />
    );
  }

  return (
    <div className="seller-stack">
      <section className="seller-section seller-shipping-overview-section" aria-labelledby="shipping-list-heading">
        <div className="seller-section__heading">
          <h2 id="shipping-list-heading">Các phí giao hiện có</h2>
        </div>
        <p className="seller-contract-note">Điểm bán đang có nhiều cấu hình phí giao. Chọn đúng cấu hình cần chỉnh để giữ nguyên dữ liệu của các cấu hình khác.</p>
        <div className="seller-row-list">
          {policies.map((policy, index) => {
            const status = policyStatus(policy.status);
            return (
              <article className="seller-row seller-shipping-row-card" key={policy.id ?? `${policy.mode ?? "policy"}-${index}`}>
                <div className="seller-shipping-row-info">
                  <strong>{policyTitle(policy.mode)}</strong>
                  {policy.mode === "NEGOTIATED" ? <small>Thỏa thuận với người bán</small> : null}
                  {policy.mode !== "NEGOTIATED" && policy.baseFee != null && policy.currencyCode ? (
                    <small>Phí cơ bản: {formatMoney({ amount: policy.baseFee, currency: policy.currencyCode })}</small>
                  ) : null}
                  {policy.mode === "CONDITIONAL" ? (
                    <small>{ruleMatchCopy(policy.ruleMatchMode)} · {policy.rules?.length ?? 0} điều kiện</small>
                  ) : null}
                </div>
                <div className="seller-row__actions">
                  <StatusChip label={status.label} tone={status.tone} />
                  {policy.id ? (
                    <button type="button" className="seller-button seller-button--secondary seller-row-edit-btn" onClick={() => setEditingId(policy.id)}>
                      Sửa
                    </button>
                  ) : null}
                </div>
              </article>
            );
          })}
        </div>
      </section>

      {editing ? (
        <ShippingPolicyForm
          key={`${editing.id ?? "policy"}-${editing.version ?? "version"}`}
          merchantId={merchantId}
          locationId={locationId}
          policy={editing}
          onCancelEdit={() => setEditingId(undefined)}
          locationName={locationName}
        />
      ) : (
        <details className="seller-technical-fields">
          <summary>Thêm cấu hình phí giao</summary>
          <ShippingPolicyForm merchantId={merchantId} locationId={locationId} locationName={locationName} />
        </details>
      )}
    </div>
  );
}
