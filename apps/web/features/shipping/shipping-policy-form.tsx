"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { CovieApiError } from "@/lib/api/api-error";
import {
  createShippingPolicy,
  deleteShippingPolicy,
  updateShippingPolicy,
} from "./shipping-api";

type Policy = components["schemas"]["ShippingPolicyResponse"];
type ShippingMode = NonNullable<components["schemas"]["ShippingPolicyUpsertRequest"]["mode"]>;
type ShippingStatus = NonNullable<components["schemas"]["ShippingPolicyUpsertRequest"]["status"]>;
type RuleMatchMode = NonNullable<components["schemas"]["ShippingPolicyUpsertRequest"]["ruleMatchMode"]>;
type ShippingRule = components["schemas"]["ShippingRuleRequest"];

const defaultRule: ShippingRule = {
  conditionType: "ORDER_AMOUNT_MIN",
  operator: "GTE",
  thresholdValue: 0,
  actionType: "FREE_SHIPPING",
  priority: 1,
  enabled: true,
};

export function ShippingPolicyForm({
  merchantId,
  locationId,
  policy,
  onCancelEdit,
}: {
  merchantId: string;
  locationId: string;
  policy?: Policy;
  onCancelEdit?: () => void;
}) {
  const router = useRouter();
  const editing = Boolean(policy?.id);
  const [mode, setMode] = useState<ShippingMode>(policy?.mode ?? "FREE");
  const [status, setStatus] = useState<ShippingStatus>(policy?.status ?? "ACTIVE");
  const [baseFee, setBaseFee] = useState(String(policy?.baseFee ?? 0));
  const [ruleMatchMode, setRuleMatchMode] = useState<RuleMatchMode>(policy?.ruleMatchMode ?? "ANY");
  const [rules, setRules] = useState<ShippingRule[]>(policy?.rules?.length ? policy.rules : [defaultRule]);
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    setMessage(undefined);

    const parsedFee = Number(baseFee);
    if ((mode === "FIXED" || mode === "CONDITIONAL") && (!Number.isSafeInteger(parsedFee) || parsedFee < 0)) {
      setMessage("Phí cơ bản phải là số nguyên không âm.");
      return;
    }
    if (mode === "CONDITIONAL" && !rules.some((rule) => rule.enabled)) {
      setMessage("Chính sách có điều kiện cần ít nhất một điều kiện đang bật.");
      return;
    }

    const input: components["schemas"]["ShippingPolicyUpsertRequest"] = {
      mode,
      status,
      currencyCode: "VND",
      baseFee: mode === "FIXED" || mode === "CONDITIONAL" ? parsedFee : undefined,
      ruleMatchMode: mode === "CONDITIONAL" ? ruleMatchMode : undefined,
      rules: mode === "CONDITIONAL" ? rules.map((rule, index) => ({ ...rule, priority: index + 1 })) : [],
    };

    setPending(true);
    try {
      if (policy?.id) {
        if (policy.version == null) throw new Error("RESOURCE_VERSION_REQUIRED");
        await updateShippingPolicy(merchantId, locationId, policy.id, policy.version, input);
      } else {
        await createShippingPolicy(merchantId, locationId, input);
      }
      router.refresh();
      onCancelEdit?.();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Chính sách đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.");
      } else if (error instanceof CovieApiError && error.code === "SHIPPING_POLICY_DUPLICATE_ACTIVE") {
        setMessage("Điểm bán đã có một chính sách giao hàng đang hoạt động. Hãy tạm ngưng chính sách đó trước.");
      } else {
        setMessage(error instanceof Error && error.message === "RESOURCE_VERSION_REQUIRED"
          ? "Thiếu version của chính sách. Hãy tải lại dữ liệu trước khi sửa."
          : "Không thể lưu chính sách giao hàng. Vui lòng kiểm tra dữ liệu và thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  async function deactivate() {
    if (!policy?.id || policy.version == null || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await deleteShippingPolicy(merchantId, locationId, policy.id, policy.version);
      setConfirmDelete(false);
      onCancelEdit?.();
      router.refresh();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Chính sách đã thay đổi. Tải dữ liệu mới trước khi tạm ngưng.");
      } else {
        setMessage("Không thể tạm ngưng chính sách. Vui lòng thử lại.");
      }
      setConfirmDelete(false);
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="seller-config-form" onSubmit={submit} aria-busy={pending}>
      <div className="seller-section__heading">
        <h2>{editing ? "Sửa chính sách giao hàng" : "Thêm chính sách giao hàng"}</h2>
        {editing && onCancelEdit ? <button type="button" disabled={pending} onClick={onCancelEdit}>Đóng chỉnh sửa</button> : null}
      </div>

      <label>
        Loại phí
        <select disabled={pending} value={mode} onChange={(event) => setMode(event.target.value as ShippingMode)}>
          <option value="FREE">Miễn phí</option>
          <option value="FIXED">Phí cố định</option>
          <option value="NEGOTIATED">Thỏa thuận với người bán</option>
          <option value="CONDITIONAL">Có điều kiện</option>
        </select>
      </label>

      {mode === "NEGOTIATED" ? (
        <p className="seller-muted">Phí giao được thỏa thuận với người mua và không được biểu diễn thành 0 ₫.</p>
      ) : mode === "FIXED" || mode === "CONDITIONAL" ? (
        <label>
          Phí cơ bản (VND)
          <input
            type="number"
            inputMode="numeric"
            min="0"
            step="1"
            value={baseFee}
            onChange={(event) => setBaseFee(event.target.value)}
            required
            disabled={pending}
          />
        </label>
      ) : null}

      {mode === "CONDITIONAL" ? (
        <fieldset disabled={pending}>
          <legend>Điều kiện miễn phí giao hàng</legend>
          <label>
            Cách khớp điều kiện
            <select value={ruleMatchMode} onChange={(event) => setRuleMatchMode(event.target.value as RuleMatchMode)}>
              <option value="ANY">Bất kỳ điều kiện nào</option>
              <option value="ALL">Tất cả điều kiện</option>
            </select>
          </label>
          {rules.map((rule, index) => (
            <div className="rule-row" key={`${rule.conditionType ?? "rule"}-${index}`}>
              <select
                aria-label={`Loại điều kiện ${index + 1}`}
                value={rule.conditionType}
                onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                  ...item,
                  conditionType: event.target.value as ShippingRule["conditionType"],
                  operator: event.target.value === "DISTANCE_MAX" ? "LTE" : "GTE",
                } : item))}
              >
                <option value="ORDER_AMOUNT_MIN">Đơn tối thiểu</option>
                <option value="DISTANCE_MAX">Khoảng cách tối đa</option>
              </select>
              <input
                aria-label={`Ngưỡng điều kiện ${index + 1}`}
                type="number"
                inputMode="numeric"
                min="0"
                step="1"
                value={rule.thresholdValue ?? 0}
                onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                  ...item,
                  thresholdValue: Math.max(0, Math.trunc(Number(event.target.value))),
                } : item))}
              />
              <label className="seller-rule-enabled">
                <input
                  type="checkbox"
                  checked={rule.enabled ?? false}
                  onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                    ...item,
                    enabled: event.target.checked,
                  } : item))}
                />
                Bật
              </label>
              <button type="button" disabled={rules.length === 1 || pending} onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))}>
                Xóa điều kiện
              </button>
            </div>
          ))}
          <button type="button" disabled={pending} onClick={() => setRules((current) => [...current, { ...defaultRule, priority: current.length + 1 }])}>
            Thêm điều kiện
          </button>
        </fieldset>
      ) : null}

      <label>
        Trạng thái
        <select disabled={pending} value={status} onChange={(event) => setStatus(event.target.value as ShippingStatus)}>
          <option value="ACTIVE">Đang hoạt động</option>
          <option value="INACTIVE">Tạm ngưng</option>
        </select>
      </label>

      {message ? <p role="alert">{message}</p> : null}
      <div className="seller-form-actions">
        <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : editing ? "Lưu chính sách" : "Thêm chính sách"}</button>
        {editing && policy?.status !== "INACTIVE" ? (
          <button type="button" className="button-danger" disabled={pending} onClick={() => setConfirmDelete(true)}>
            Tạm ngưng chính sách
          </button>
        ) : null}
      </div>

      <ConfirmDialog
        open={confirmDelete}
        title="Tạm ngưng chính sách giao hàng?"
        description="Backend sẽ chuyển chính sách này sang INACTIVE; dữ liệu lịch sử vẫn được giữ lại."
        confirmLabel={pending ? "Đang xử lý…" : "Tạm ngưng"}
        dangerous
        confirmDisabled={pending}
        cancelDisabled={pending}
        onCancel={() => { if (!pending) setConfirmDelete(false); }}
        onConfirm={() => { void deactivate(); }}
      />
    </form>
  );
}
