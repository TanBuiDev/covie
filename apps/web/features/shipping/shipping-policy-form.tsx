"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { SelectDropdown } from "@/components/ui/select-dropdown";
import { CovieApiError } from "@/lib/api/api-error";
import {
  createShippingPolicy,
  deleteShippingPolicy,
  updateShippingPolicy,
} from "./shipping-api";

import {
  ChatCircleDots,
  FloppyDisk,
  Gift,
  Info,
  PlusCircle,
  SlidersHorizontal,
  Sparkle,
  Tag,
  Truck,
  Calculator,
} from "@phosphor-icons/react";

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

const FIXED_FEE_PRESETS = [15000, 20000, 25000, 30000] as const;

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
  locationName,
}: {
  merchantId: string;
  locationId: string;
  policy?: Policy;
  onCancelEdit?: () => void;
  locationName?: string;
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

  // SIMULATOR STATE
  const [simDistanceKm, setSimDistanceKm] = useState(2.5);
  const [simOrderAmount, setSimOrderAmount] = useState(180000);

  // Evaluate simulator outcome
  const parsedBaseFee = Number(baseFee) || 0;
  const simDistanceMeters = simDistanceKm * 1000;

  const simResult = (() => {
    if (mode === "FREE") {
      return { fee: 0, label: "0 ₫ (Freeship toàn bộ)", isFree: true, reason: "Áp dụng miễn phí vận chuyển cho tất cả đơn." };
    }
    if (mode === "FIXED") {
      return { fee: parsedBaseFee, label: `${parsedBaseFee.toLocaleString("vi-VN")} ₫`, isFree: false, reason: "Áp dụng mức phí cố định." };
    }
    if (mode === "NEGOTIATED") {
      return { fee: 0, label: "Thỏa thuận với người bán", isFree: false, reason: "Phí giao được người mua và người bán thương lượng riêng." };
    }

    // CONDITIONAL mode
    const activeRules = rules.filter((r) => r.enabled && isKnownCondition(r));
    if (!activeRules.length) {
      return { fee: parsedBaseFee, label: `${parsedBaseFee.toLocaleString("vi-VN")} ₫`, isFree: false, reason: "Chưa có điều kiện nào kích hoạt, tính theo phí mặc định." };
    }

    const evaluations = activeRules.map((r) => {
      if (r.conditionType === "DISTANCE_MAX") {
        return simDistanceMeters <= (r.thresholdValue ?? 0);
      }
      if (r.conditionType === "ORDER_AMOUNT_MIN") {
        return simOrderAmount >= (r.thresholdValue ?? 0);
      }
      return false;
    });

    const satisfies = ruleMatchMode === "ALL"
      ? evaluations.every(Boolean)
      : evaluations.some(Boolean);

    if (satisfies) {
      return {
        fee: 0,
        label: "0 ₫ (Miễn phí vận chuyển)",
        isFree: true,
        reason: `Đạt điều kiện freeship (${ruleMatchMode === "ALL" ? "Thỏa mãn toàn bộ" : "Thỏa mãn ít nhất một"} điều kiện).`,
      };
    }

    return {
      fee: parsedBaseFee,
      label: `${parsedBaseFee.toLocaleString("vi-VN")} ₫`,
      isFree: false,
      reason: `Chưa đạt điều kiện freeship, áp dụng mức phí mặc định ${parsedBaseFee.toLocaleString("vi-VN")} ₫.`,
    };
  })();

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
    <div className="seller-shipping-studio-layout">
      <div className="seller-shipping-studio-main">
        <form className="seller-config-form seller-shipping-form" onSubmit={submit} aria-busy={pending}>
          <div className="seller-section__heading seller-desktop-only">
            <h2>{editing ? "Sửa phí giao hàng" : "Thêm phí giao hàng"}</h2>
            {editing && onCancelEdit ? (
              <button type="button" className="seller-button seller-button--secondary" disabled={pending} onClick={onCancelEdit}>
                Đóng chỉnh sửa
              </button>
            ) : null}
          </div>

          {/* POLICY MODE SELECTOR */}
          <div className="seller-shipping-mode-container">
            <label>
              Loại phí giao
              <SelectDropdown<ShippingMode>
                aria-label="Loại phí giao"
                disabled={pending}
                value={mode}
                radius="md"
                size="md"
                options={[
                  { value: "FREE", label: "Freeship" },
                  { value: "FIXED", label: "Cố định" },
                  { value: "CONDITIONAL", label: "Theo điều kiện" },
                  { value: "NEGOTIATED", label: "Thỏa thuận" },
                ]}
                onChange={(val) => setMode(val)}
              />
              <small className="field-hint">Freeship · Cố định · Theo điều kiện · Thỏa thuận</small>
            </label>

            {/* Visual Mode Selector Cards */}
            <div className="seller-shipping-mode-cards" aria-hidden="true">
              <button
                type="button"
                tabIndex={-1}
                disabled={pending}
                className={`seller-shipping-mode-card ${mode === "FREE" ? "is-selected" : ""}`}
                onClick={() => setMode("FREE")}
              >
                <div className="seller-shipping-mode-card__icon seller-shipping-mode-card__icon--emerald">
                  <Gift size={20} weight="duotone" />
                </div>
                <strong>Freeship</strong>
                <small>Miễn phí toàn bộ</small>
              </button>

              <button
                type="button"
                tabIndex={-1}
                disabled={pending}
                className={`seller-shipping-mode-card ${mode === "FIXED" ? "is-selected" : ""}`}
                onClick={() => setMode("FIXED")}
              >
                <div className="seller-shipping-mode-card__icon seller-shipping-mode-card__icon--blue">
                  <Tag size={20} weight="duotone" />
                </div>
                <strong>Cố định</strong>
                <small>Đồng giá mọi đơn</small>
              </button>

              <button
                type="button"
                tabIndex={-1}
                disabled={pending}
                className={`seller-shipping-mode-card ${mode === "CONDITIONAL" ? "is-selected" : ""}`}
                onClick={() => setMode("CONDITIONAL")}
              >
                <div className="seller-shipping-mode-card__icon seller-shipping-mode-card__icon--purple">
                  <SlidersHorizontal size={20} weight="duotone" />
                </div>
                <strong>Điều kiện</strong>
                <small>Theo cự ly / giá trị</small>
              </button>

              <button
                type="button"
                tabIndex={-1}
                disabled={pending}
                className={`seller-shipping-mode-card ${mode === "NEGOTIATED" ? "is-selected" : ""}`}
                onClick={() => setMode("NEGOTIATED")}
              >
                <div className="seller-shipping-mode-card__icon seller-shipping-mode-card__icon--slate">
                  <ChatCircleDots size={20} weight="duotone" />
                </div>
                <strong>Thỏa thuận</strong>
                <small>Trao đổi riêng</small>
              </button>
            </div>
          </div>

          {mode === "NEGOTIATED" ? (
            <p className="seller-contract-note">Phí giao được thỏa thuận với người mua và không được biểu diễn thành 0 ₫.</p>
          ) : mode === "FIXED" || mode === "CONDITIONAL" ? (
            <div className="seller-fee-input-group">
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

              {/* Quick Fee Presets */}
              <div className="seller-radius-preset-pills" aria-hidden="true">
                {FIXED_FEE_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    tabIndex={-1}
                    className={`seller-preset-pill ${Number(baseFee) === preset ? "is-selected" : ""}`}
                    disabled={pending}
                    onClick={() => setBaseFee(String(preset))}
                  >
                    {preset.toLocaleString("vi-VN")} ₫
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          {mode === "CONDITIONAL" ? (
            <section className="seller-shipping-conditions" aria-labelledby="shipping-condition-heading">
              <div className="seller-shipping-conditions__head">
                <strong id="shipping-condition-heading">Điều kiện freeship</strong>
                <span className="seller-badge seller-badge--neutral">{rules.length} điều kiện</span>
              </div>

              {rules.map((rule, index) => {
                const knownCondition = isKnownCondition(rule);
                return (
                  <div className="seller-shipping-condition seller-shipping-condition-card" key={`${rule.conditionType ?? "rule"}-${index}`}>
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
                        <SelectDropdown<NonNullable<ShippingRule["conditionType"]>>
                          aria-label={`Loại điều kiện ${index + 1}`}
                          disabled={pending}
                          value={rule.conditionType ?? "DISTANCE_MAX"}
                          radius="md"
                          size="md"
                          options={[
                            { value: "DISTANCE_MAX", label: "Khoảng cách tối đa" },
                            { value: "ORDER_AMOUNT_MIN", label: "Đơn tối thiểu" },
                          ]}
                          onChange={(val) =>
                            setRules((current) =>
                              current.map((item, ruleIndex) =>
                                ruleIndex === index
                                  ? {
                                      ...item,
                                      conditionType: val,
                                      operator: val === "DISTANCE_MAX" ? "LTE" : "GTE",
                                    }
                                  : item
                              )
                            )
                          }
                        />
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
                        <button
                          className="seller-button seller-button--secondary seller-condition-remove-btn"
                          type="button"
                          disabled={rules.length === 1 || pending}
                          onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))}
                        >
                          Xóa điều kiện
                        </button>
                      </div>
                    ) : (
                      <p className="seller-contract-note">Điều kiện này chưa được phiên bản hiện tại hỗ trợ nên được giữ ở chế độ chỉ đọc.</p>
                    )}
                  </div>
                );
              })}

              <button
                className="seller-button seller-button--secondary seller-condition-add-btn"
                type="button"
                disabled={pending}
                onClick={() => setRules((current) => [...current, { ...defaultRule, priority: current.length + 1 }])}
              >
                <PlusCircle size={16} weight="bold" aria-hidden="true" />
                <span>Thêm điều kiện</span>
              </button>

              <fieldset className="seller-shipping-match" disabled={pending}>
                <legend>Logic điều kiện</legend>
                <div className="seller-radio-group">
                  <label className="seller-radio-card">
                    <input type="radio" name="shippingRuleMatch" value="ANY" checked={ruleMatchMode === "ANY"} onChange={() => setRuleMatchMode("ANY")} />
                    <span>Bất kỳ điều kiện nào đúng</span>
                  </label>
                  <label className="seller-radio-card">
                    <input type="radio" name="shippingRuleMatch" value="ALL" checked={ruleMatchMode === "ALL"} onChange={() => setRuleMatchMode("ALL")} />
                    <span>Tất cả điều kiện phải đúng</span>
                  </label>
                </div>
              </fieldset>
            </section>
          ) : null}

          {editing ? (
            <details className="seller-technical-fields">
              <summary>Trạng thái & quản lý</summary>
              <p className="field-hint">Trạng thái hiện tại: {policy?.status ?? "không xác định"}. Trạng thái này được giữ nguyên khi lưu.</p>
              {policy?.status !== "INACTIVE" ? (
                <button type="button" className="button-danger" disabled={pending} onClick={() => setConfirmDelete(true)}>
                  Tạm ngưng chính sách
                </button>
              ) : null}
            </details>
          ) : null}

          {message ? <p role="alert">{message}</p> : null}
          <button
            className="seller-button seller-button--primary seller-shipping-save"
            type="submit"
            disabled={pending}
          >
            <FloppyDisk size={16} weight="bold" aria-hidden="true" />
            <span>{pending ? "Đang lưu…" : "Lưu phí giao"}</span>
          </button>

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
      </div>

      {/* ASIDE: LIVE SHIPPING COST SIMULATOR */}
      <aside className="seller-shipping-studio-aside">
        <div className="seller-live-preview-box">
          <div className="seller-live-preview-box__title">
            <Calculator size={18} weight="fill" className="seller-brand-accent" aria-hidden="true" />
            <span>Giả lập tính phí giao hàng</span>
          </div>

          <div className="seller-shipping-simulator-card">
            <div className="seller-simulator-sliders">
              <div className="seller-slider-item">
                <div className="seller-slider-header">
                  <span>Khoảng cách khách mua:</span>
                  <strong>{simDistanceKm} km</strong>
                </div>
                <input
                  type="range"
                  min="0.5"
                  max="10"
                  step="0.5"
                  value={simDistanceKm}
                  onChange={(e) => setSimDistanceKm(Number(e.target.value))}
                  className="seller-simulator-range"
                />
              </div>

              <div className="seller-slider-item">
                <div className="seller-slider-header">
                  <span>Giá trị đơn hàng:</span>
                  <strong>{simOrderAmount.toLocaleString("vi-VN")} ₫</strong>
                </div>
                <input
                  type="range"
                  min="30000"
                  max="500000"
                  step="10000"
                  value={simOrderAmount}
                  onChange={(e) => setSimOrderAmount(Number(e.target.value))}
                  className="seller-simulator-range"
                />
              </div>
            </div>

            {/* Quick Test Scenarios */}
            <div className="seller-simulator-scenarios" aria-hidden="true">
              <span className="seller-scenarios-title">Tình huống mẫu:</span>
              <div className="seller-scenarios-chips">
                <button
                  type="button"
                  tabIndex={-1}
                  className="seller-scenario-chip"
                  onClick={() => { setSimDistanceKm(1.5); setSimOrderAmount(60000); }}
                >
                  Đơn nhỏ gần nhà (1.5km - 60k)
                </button>
                <button
                  type="button"
                  tabIndex={-1}
                  className="seller-scenario-chip"
                  onClick={() => { setSimDistanceKm(4.0); setSimOrderAmount(250000); }}
                >
                  Đơn lớn xa nhà (4km - 250k)
                </button>
              </div>
            </div>

            {/* Simulator Live Calculation Output */}
            <div className={`seller-simulator-output ${simResult.isFree ? "is-free" : ""}`}>
              <div className="seller-simulator-output__head">
                <span>Phí giao tính cho khách:</span>
                <strong className="seller-simulator-output__fee">{simResult.label}</strong>
              </div>
              <p className="seller-simulator-output__reason">{simResult.reason}</p>
            </div>
          </div>

          <div className="seller-create-tips-card">
            <h4>
              <Info size={16} weight="bold" aria-hidden="true" />
              <span>Mẹo định giá phí vận chuyển</span>
            </h4>
            <ul className="seller-tips-list">
              <li><strong>Freeship có điều kiện:</strong> Khuyến khích khách mua thêm để đạt ngưỡng freeship giúp tăng giá trị đơn hàng trung bình (AOV).</li>
              <li><strong>Phí cố định:</strong> Phù hợp khi chi nhánh hoạt động trong bán kính hẹp (dưới 3km) với cước đồng giá 15k - 20k.</li>
              <li><strong>Thỏa thuận:</strong> Dùng cho hàng cồng kềnh hoặc giao hàng hỏa tốc cần báo giá riêng.</li>
            </ul>
          </div>
        </div>
      </aside>
    </div>
  );
}
