"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminPageHeader, AdminSurface, formatAdminMoney } from "./admin-ui";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import type { components } from "@covie/api-client";

type Plan = Required<components["schemas"]["PlanOffer"]>;
type Policy = Required<components["schemas"]["Policy"]>;

async function requireJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw await readApiError(response);
  return response.json() as Promise<T>;
}

export function AdminBillingScreen() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [policy, setPolicy] = useState<Policy>();
  const [price, setPrice] = useState("");
  const [months, setMonths] = useState("1");
  const [mode, setMode] = useState("PAY_BEFORE_SELL");
  const [trial, setTrial] = useState("0");
  const [grace, setGrace] = useState("3");
  const [effectiveAt, setEffectiveAt] = useState("");
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [saving, setSaving] = useState(false);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async () => {
    const [priceRes, policyRes] = await Promise.all([
      requestBrowserApi("GET", "/api/v1/billing/plans"),
      requestBrowserApi("GET", "/api/v1/admin/billing/policy")
    ]);
    const values = await requireJson<Plan[]>(priceRes);
    const settings = await requireJson<Policy>(policyRes);
    setPlans(values); setPolicy(settings);
    setPrice(String(values[0]?.priceVnd ?? ""));
    setMonths(String(values[0]?.cycleMonths ?? 1));
    setMode(settings.onboardingMode);
    setTrial(String(settings.trialDays));
    setGrace(String(settings.graceDays));
  }, []);

  useEffect(() => {
    let valid = true;
    Promise.resolve().then(() => {
      if (!valid) return;
      load().catch(cause => { if (valid) setError(cause instanceof Error ? cause.message : "Không tải được chính sách"); })
        .finally(() => { if (valid) setLoading(false); });
    });
    return () => { valid = false; };
  }, [load]);

  async function save(kind: "price" | "policy") {
    if (saving) return;
    setError(""); setNotice("");
    const date = new Date(effectiveAt);
    if (!effectiveAt || Number.isNaN(date.getTime()) || date <= new Date()) {
      setError("Chọn ngày giờ có hiệu lực trong tương lai."); return;
    }
    if (!window.confirm("Bạn chắc chắn muốn công bố phiên bản chính sách/giá mới? Không thể sửa phiên bản đã phát hành.")) return;
    setSaving(true);
    try {
      const url = kind === "price"
        ? `/api/v1/admin/billing/plans/${encodeURIComponent(plans[0]?.code ?? "")}/prices`
        : "/api/v1/admin/billing/policy";
      const body = kind === "price"
        ? { priceVnd: Number(price), cycleMonths: Number(months), effectiveAt: date.toISOString() }
        : { onboardingMode: mode, trialDays: Number(trial), graceDays: Number(grace), effectiveAt: date.toISOString() };
      await requireJson(await requestBrowserApi("POST", url, body));
      setNotice("Đã tạo phiên bản mới; giá và chính sách có hiệu lực theo lịch đã chọn.");
      await load();
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể lưu chính sách"); }
    finally { setSaving(false); }
  }

  return <div className="admin-page-stack">
    <AdminPageHeader title="Thuê bao Seller" subtitle="Bảng giá và chính sách kinh doanh do máy chủ quản lý theo phiên bản." />
    {error ? <p role="alert" className="admin-error">{error}</p> : null}
    {notice ? <p role="status">{notice}</p> : null}
    {loading ? <p>Đang tải Billing…</p> : (
      <>
        <AdminSurface>
          <h2>Bảng giá hiện có hiệu lực</h2>
          {plans.length === 0 ? <p>Không có gói đang bán.</p> : plans.map(plan => (
            <p key={plan.id}><strong>{plan.name}</strong>: {formatAdminMoney(plan.priceVnd)} /
              {plan.cycleMonths} tháng, phiên bản {plan.revision}</p>
          ))}
          <div className="admin-field"><label htmlFor="billing-price">Giá mới (VND)</label>
            <input id="billing-price" type="number" min="0" value={price} onChange={e => setPrice(e.target.value)} /></div>
          <div className="admin-field"><label htmlFor="billing-months">Số tháng</label>
            <input id="billing-months" type="number" min="1" max="120" value={months} onChange={e => setMonths(e.target.value)} /></div>
        </AdminSurface>
        <AdminSurface>
          <h2>Chính sách mở cửa hàng</h2>
          <p>Phiên bản {policy?.revision} — {policy?.onboardingMode === "TRIAL" ? "Dùng thử" : "Thanh toán trước"}.</p>
          <div className="admin-field"><label htmlFor="billing-policy">Chính sách</label>
            <select id="billing-policy" value={mode} onChange={e => setMode(e.target.value)}>
              <option value="PAY_BEFORE_SELL">Thanh toán trước khi mở bán</option>
              <option value="TRIAL">Cho phép dùng thử</option>
            </select></div>
          <div className="admin-field"><label htmlFor="billing-trial">Số ngày dùng thử</label>
            <input id="billing-trial" type="number" min="0" max="365" value={trial} onChange={e => setTrial(e.target.value)} /></div>
          <div className="admin-field"><label htmlFor="billing-grace">Số ngày ân hạn</label>
            <input id="billing-grace" type="number" min="0" max="60" value={grace} onChange={e => setGrace(e.target.value)} /></div>
        </AdminSurface>
        <AdminSurface>
          <h2>Công bố phiên bản mới</h2>
          <p>Chọn thời điểm áp dụng. Hóa đơn hiện hữu giữ nguyên bản giá đã xác nhận.</p>
          <div className="admin-field"><label htmlFor="billing-effective">Có hiệu lực từ</label>
            <input id="billing-effective" type="datetime-local" value={effectiveAt}
              onChange={e => setEffectiveAt(e.target.value)} /></div>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <button className="admin-button admin-button--primary" disabled={saving || plans.length === 0}
              onClick={() => void save("price")}>{saving ? "Đang lưu…" : "Công bố giá mới"}</button>
            <button className="admin-button admin-button--secondary" disabled={saving}
              onClick={() => void save("policy")}>{saving ? "Đang lưu…" : "Công bố chính sách mới"}</button>
          </div>
        </AdminSurface>
      </>
    )}
  </div>;
}
