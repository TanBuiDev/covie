"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useState } from "react";
import { createShippingPolicy } from "./shipping-api";

type ShippingMode = NonNullable<components["schemas"]["ShippingPolicyUpsertRequest"]["mode"]>;
type RuleMatchMode = NonNullable<components["schemas"]["ShippingPolicyUpsertRequest"]["ruleMatchMode"]>;
type ShippingRule = components["schemas"]["ShippingRuleRequest"];

export function ShippingPolicyForm({ merchantId, locationId }: { merchantId: string; locationId: string }) {
  const [mode, setMode] = useState<ShippingMode>("FREE");
  const [ruleMatchMode, setRuleMatchMode] = useState<RuleMatchMode>("ANY");
  const [rules, setRules] = useState<ShippingRule[]>([
    { conditionType: "ORDER_AMOUNT_MIN", operator: "GTE", thresholdValue: 0, actionType: "FREE_SHIPPING", priority: 1, enabled: true },
  ]);
  const [message, setMessage] = useState<string>();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const fee = Number(form.get("baseFee"));
    try {
      await createShippingPolicy(merchantId, locationId, {
        mode,
        status: "ACTIVE",
        currencyCode: "VND",
        baseFee: mode === "FREE" || mode === "NEGOTIATED" ? undefined : Math.max(0, Math.trunc(fee)),
        ruleMatchMode: mode === "CONDITIONAL" ? ruleMatchMode : undefined,
        rules: mode === "CONDITIONAL" ? rules.map((rule, index) => ({ ...rule, priority: index + 1 })) : undefined,
      });
      window.location.reload();
    } catch {
      setMessage("Không thể lưu chính sách giao hàng.");
    }
  }

  return (
    <form onSubmit={submit}>
      <h2>Thêm chính sách giao hàng</h2>
      <label>
        Loại phí
        <select value={mode} onChange={(event) => setMode(event.target.value as ShippingMode)}>
          <option value="FREE">Miễn phí</option>
          <option value="FIXED">Phí cố định</option>
          <option value="NEGOTIATED">Thỏa thuận với người bán</option>
          <option value="CONDITIONAL">Có điều kiện</option>
        </select>
      </label>

      {mode === "NEGOTIATED" ? (
        <p>Phí giao được thỏa thuận với người mua và không được biểu diễn thành 0đ.</p>
      ) : mode !== "FREE" ? (
        <label>
          Phí cơ bản (VND)
          <input name="baseFee" type="number" inputMode="numeric" min="0" step="1" required />
        </label>
      ) : null}

      {mode === "CONDITIONAL" ? (
        <fieldset>
          <legend>Điều kiện miễn phí giao hàng</legend>
          <label>
            Cách khớp điều kiện
            <select value={ruleMatchMode} onChange={(event) => setRuleMatchMode(event.target.value as RuleMatchMode)}>
              <option value="ANY">Bất kỳ điều kiện nào</option>
              <option value="ALL">Tất cả điều kiện</option>
            </select>
          </label>
          {rules.map((rule, index) => (
            <div className="rule-row" key={index}>
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
              <button type="button" disabled={rules.length === 1} onClick={() => setRules((current) => current.filter((_, ruleIndex) => ruleIndex !== index))}>Xóa điều kiện</button>
            </div>
          ))}
          <button type="button" onClick={() => setRules((current) => [...current, {
            conditionType: "ORDER_AMOUNT_MIN",
            operator: "GTE",
            thresholdValue: 0,
            actionType: "FREE_SHIPPING",
            priority: current.length + 1,
            enabled: true,
          }])}>Thêm điều kiện</button>
        </fieldset>
      ) : null}

      {message ? <p role="alert">{message}</p> : null}
      <button type="submit">Lưu chính sách</button>
    </form>
  );
}
