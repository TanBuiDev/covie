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

function isKnownCondition(rule: ShippingRule) {
  return rule.conditionType === "DISTANCE_MAX" || rule.conditionType === "ORDER_AMOUNT_MIN";
}

function conditionCopy(rule: ShippingRule) {
  switch (rule.conditionType) {
    case "DISTANCE_MAX": return "Khoảng cách tối đa";
    case "ORDER_AMOUNT_MIN": return "Đơn tối thiểu";
    default: return "Điều kiện đã được cập nhật";
  }
}

function conditionUnit(rule: ShippingRule) {
  switch (rule.conditionType) {
    case "DISTANCE_MAX": return "m";
    case "ORDER_AMOUNT_MIN": return "₫";
    default: return "giá trị";
  }
}

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
      setMessage("Phí giao phải là số nguyên không âm theo đồng (₫).");
      return;
    }
    if (mode === "CONDITIONAL" && rules.some((rule) => !isKnownCondition(rule))) {
      setMessage("Có điều kiện chưa được phiên bản hiện tại hỗ trợ. Không thể lưu để tránh làm thay đổi cấu hình ngoài ý muốn.");
      return;
    }
    if (mode === "CONDITIONAL" && !rules.some((rule) => rule.enabled)) {
      setMessage("Chính sách có điều kiện cần ít nhất một điều kiện đang bật.");
      return;
    }

    const input: components["schemas"]["ShippingPolicyUpsertRequest"] = {
      mode,
      status: policy?.status ?? "ACTIVE",
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
        setMessage("Phí giao hàng đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.");
      } else if (error instanceof CovieApiError && error.code === "SHIPPING_POLICY_DUPLICATE_ACTIVE") {
        setMessage("Điểm bán đã có một chính sách giao hàng đang hoạt động. Hãy tạm ngưng chính sách đó trước.");
      } else {
        setMessage(error instanceof Error && error.message === "RESOURCE_VERSION_REQUIRED"
          ? "Thông tin chính sách chưa đủ để chỉnh sửa an toàn. Hãy tải lại dữ liệu trước khi sửa."
          : "Không thể lưu phí giao hàng. Vui lòng kiểm tra dữ liệu và thử lại.");
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
    <form className="seller-config-form seller-shipping-form" onSubmit={submit} aria-busy={pending}>
      <div className="seller-section__heading seller-desktop-only">
        <h2>{editing ? "Sửa phí giao hàng" : "Thêm phí giao hàng"}</h2>
        {editing && onCancelEdit ? <button type="button" disabled={pending} onClick={onCancelEdit}>Đóng chỉnh sửa</button> : null}
      </div>

      <label>
        Loại phí giao
        <select disabled={pending} value={mode} onChange={(event) => setMode(event.target.value as ShippingMode)}>
          <option value="FREE">Freeship</option>
          <option value="FIXED">Cố định</option>
          <option value="CONDITIONAL">Theo điều kiện</option>
          <option value="NEGOTIATED">Thỏa thuận</option>
        </select>
        <small className="field-hint">Freeship · Cố định · Theo điều kiện · Thỏa thuận</small>
      </label>

      {mode === "NEGOTIATED" ? (
        <p className="seller-contract-note">Phí giao được thỏa thuận với người mua và không được biểu diễn thành 0 ₫.</p>
      ) : mode === "FIXED" || mode === "CONDITIONAL" ? (
        <label>
          {mode === "CONDITIONAL" ? "Phí mặc định" : "Phí cố định"}
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
          <small className="field-hint">Nhập số tiền theo đồng (₫), không dùng số lẻ.</small>
        </label>
      ) : null}

      {mode === "CONDITIONAL" ? (
        <section className="seller-shipping-conditions" aria-labelledby="shipping-condition-heading">
          <strong id="shipping-condition-heading">Điều kiện freeship</strong>
          {rules.map((rule, index) => {
            const knownCondition = isKnownCondition(rule);
            return (
              <div className="seller-shipping-condition" key={`${rule.conditionType ?? "rule"}-${index}`}>
                <label className="seller-shipping-condition__toggle">
                  <input
                    type="checkbox"
                    checked={rule.enabled ?? false}
                    disabled={pending || !knownCondition}
                    onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                      ...item,
                      enabled: event.target.checked,
                    } : item))}
                  />
                  <span>{knownCondition ? `Freeship khi ${conditionCopy(rule).toLocaleLowerCase("vi-VN")}` : conditionCopy(rule)}</span>
                </label>
                {knownCondition ? (
                  <div className="seller-shipping-condition__editor">
                    <select
                      aria-label={`Loại điều kiện ${index + 1}`}
                      disabled={pending}
                      value={rule.conditionType}
                      onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                        ...item,
                        conditionType: event.target.value as ShippingRule["conditionType"],
                        operator: event.target.value === "DISTANCE_MAX" ? "LTE" : "GTE",
                      } : item))}
                    >
                      <option value="DISTANCE_MAX">Khoảng cách tối đa</option>
                      <option value="ORDER_AMOUNT_MIN">Đơn tối thiểu</option>
                    </select>
                    <label>
                      Ngưỡng ({conditionUnit(rule)})
                      <input
                        aria-label={`Ngưỡng điều kiện ${index + 1}`}
                        type="number"
                        inputMode="numeric"
                        min="0"
                        step="1"
                        disabled={pending}
                        value={rule.thresholdValue ?? 0}
                        onChange={(event) => setRules((current) => current.map((item, ruleIndex) => ruleIndex === index ? {
                          ...item,
                          thresholdValue: Math.max(0, Math.trunc(Number(event.target.value))),
                        } : item))}
                      />
                    </label>
                    <button className="seller-button--secondary" type="button" disabled={rules.length === 1 || pending} onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))}>
                      Xóa điều kiện
                    </button>
                  </div>
                ) : (
                  <p className="seller-contract-note">Điều kiện này chưa được phiên bản hiện tại hỗ trợ nên được giữ ở chế độ chỉ đọc.</p>
                )}
              </div>
            );
          })}
          <button className="seller-button--secondary" type="button" disabled={pending} onClick={() => setRules((current) => [...current, { ...defaultRule, priority: current.length + 1 }])}>
            Thêm điều kiện
          </button>

          <fieldset className="seller-shipping-match" disabled={pending}>
            <legend>Logic điều kiện</legend>
            <label><input type="radio" name="shippingRuleMatch" value="ANY" checked={ruleMatchMode === "ANY"} onChange={() => setRuleMatchMode("ANY")} /> Bất kỳ điều kiện nào đúng</label>
            <label><input type="radio" name="shippingRuleMatch" value="ALL" checked={ruleMatchMode === "ALL"} onChange={() => setRuleMatchMode("ALL")} /> Tất cả điều kiện phải đúng</label>
          </fieldset>
        </section>
      ) : null}

      {editing ? (
        <details className="seller-technical-fields">
          <summary>Trạng thái & quản lý</summary>
          <p className="field-hint">Trạng thái hiện tại: {policy?.status ?? "không xác định"}. Trạng thái này được giữ nguyên khi lưu.</p>
          {policy?.status !== "INACTIVE" ? (
            <button type="button" className="button-danger" disabled={pending} onClick={() => setConfirmDelete(true)}>Tạm ngưng chính sách</button>
          ) : null}
        </details>
      ) : null}

      {message ? <p role="alert">{message}</p> : null}
      <button className="seller-shipping-save" type="submit" disabled={pending}>{pending ? "Đang lưu…" : "Lưu phí giao"}</button>

      <ConfirmDialog
        open={confirmDelete}
        title="Tạm ngưng chính sách giao hàng?"
        description="Chính sách sẽ được chuyển sang trạng thái tạm ngưng; dữ liệu cấu hình vẫn được giữ lại."
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
