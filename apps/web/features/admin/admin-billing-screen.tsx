"use client";

import { useCallback, useEffect, useState } from "react";
import { AdminPageHeader, AdminSurface, formatAdminMoney } from "./admin-ui";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import type { components } from "@covie/api-client";

type Plan = Required<components["schemas"]["PlanOffer"]>;
type Policy = Required<components["schemas"]["Policy"]>;
type Overview = { collectedVnd: number; paidInvoices: number; pendingInvoices: number; entitledShops: number; reconciliationAlerts: number };
type BillingInvoice = { id: string; merchantId: string; invoiceNumber: string; status: string; amountVnd: number; createdAt: string; paidAt?: string };
type FinanceCase = { id: string; invoiceId: string; merchantId: string; kind: string; status: string; reason: string; createdAt: string; resolutionNote?: string };


async function requireJson<T>(response: Response): Promise<T> {
  if (!response.ok) throw await readApiError(response);
  return response.json() as Promise<T>;
}

export function AdminBillingScreen() {
  const [plans, setPlans] = useState<Plan[]>([]);
  const [selectedCode, setSelectedCode] = useState("");
  const [newCode, setNewCode] = useState("");
  const [newName, setNewName] = useState("");
  const [newPrice, setNewPrice] = useState("10000");
  const [newCycle, setNewCycle] = useState("1");
  const [overview, setOverview] = useState<Overview>();
  const [invoices, setInvoices] = useState<BillingInvoice[]>([]);
  const [cases, setCases] = useState<FinanceCase[]>([]);
  const [grantMerchantId, setGrantMerchantId] = useState("");
  const [grantEnd, setGrantEnd] = useState("");
  const [grantReason, setGrantReason] = useState("");
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
    const [priceRes, policyRes, overviewRes, invoiceRes, caseRes] = await Promise.all([
      requestBrowserApi("GET", "/api/v1/billing/plans"),
      requestBrowserApi("GET", "/api/v1/admin/billing/policy"),
      requestBrowserApi("GET", "/api/v1/admin/billing/overview"),
      requestBrowserApi("GET", "/api/v1/admin/billing/invoices?limit=20"),
      requestBrowserApi("GET", "/api/v1/admin/billing/finance-cases?limit=30")
    ]);
    const values = await requireJson<Plan[]>(priceRes);
    const settings = await requireJson<Policy>(policyRes);
    const totals = await requireJson<Overview>(overviewRes);
    const ledger = await requireJson<BillingInvoice[]>(invoiceRes);
    const financeCases = await requireJson<FinanceCase[]>(caseRes);
    setCases(financeCases);
    setOverview(totals);
    setInvoices(ledger);
    setPlans(values); setPolicy(settings);
    setSelectedCode(previous => values.some(p => p.code === previous) ? previous : (values[0]?.code ?? ""));
    if (values.length > 0) {
      setPrice(previous => previous || String(values[0].priceVnd));
      setMonths(previous => previous || String(values[0].cycleMonths));
    }
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
        ? `/api/v1/admin/billing/plans/${encodeURIComponent(selectedCode)}/prices`
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

  async function createPlan() {
    if (saving) return;
    setError(""); setNotice("");
    const date = new Date(effectiveAt);
    if (!effectiveAt || Number.isNaN(date.getTime()) || date < new Date()) {
      setError("Chọn ngày giờ có hiệu lực."); return;
    }
    if (!/^[A-Z0-9_]{1,60}$/.test(newCode) || !newName.trim() || Number(newPrice) <= 0
      || !Number.isInteger(Number(newCycle)) || Number(newCycle) < 1 || Number(newCycle) > 120) {
      setError("Mã gói, tên, giá và chu kỳ không hợp lệ."); return;
    }
    if (!window.confirm("Tạo gói thuê bao mới theo các thông số này?")) return;
    setSaving(true);
    try {
      await requireJson(await requestBrowserApi("POST", "/api/v1/admin/billing/plans",
        { code: newCode, displayName: newName.trim(), priceVnd: Number(newPrice),
          cycleMonths: Number(newCycle), effectiveAt: date.toISOString() }));
      setNotice("Gói mới đã được tạo. Giá có hiệu lực theo lịch đã chọn.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tạo gói mới");
    } finally { setSaving(false); }
  }

  async function retirePlan() {
    if (!selectedCode || saving || !window.confirm("Ngừng bán gói này? Thuê bao/hóa đơn đã thanh toán vẫn được giữ.")) return;
    setSaving(true); setError(""); setNotice("");
    try {
      const response = await requestBrowserApi("POST",
        `/api/v1/admin/billing/plans/${encodeURIComponent(selectedCode)}/retire`);
      if (!response.ok) throw await readApiError(response);
      setNotice("Đã ngừng bán gói; dữ liệu đã thanh toán được giữ nguyên.");
      setSelectedCode(""); setPrice(""); setMonths("1");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể ngừng bán gói");
    } finally { setSaving(false); }
  }

  async function resolveCase(id: string, status: "REVIEWING" | "REJECTED" | "CONFIRMED") {
    if (saving) return;
    const note = window.prompt("Ghi rõ kết quả xác minh hoặc lý do xử lý:");
    if (!note?.trim()) return;
    let externalReference: string | null = null;
    if (status === "CONFIRMED") {
      externalReference = window.prompt("Nhập mã tham chiếu hoàn tiền đã xác minh tại ngân hàng/nhà cung cấp:");
      if (!externalReference || externalReference.length < 6) return;
      if (!window.confirm("Xác nhận đã hoàn tiền ngoài CoVie? Thao tác này sẽ thu hồi quyền từ hóa đơn hoàn phí.")) return;
    }
    setSaving(true); setError(""); setNotice("");
    try {
      await requireJson(await requestBrowserApi("POST",
        `/api/v1/admin/billing/finance-cases/${encodeURIComponent(id)}/resolve`,
        { status, note, externalReference }));
      setNotice("Đã cập nhật hồ sơ tài chính và ghi nhật ký quản trị.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không xử lý được hồ sơ");
    } finally { setSaving(false); }
  }

  async function grantFreeAccess() {
    if (saving) return;
    const until = new Date(grantEnd);
    if (!/^[0-9a-f-]{36}$/i.test(grantMerchantId) || !grantReason.trim()
        || !grantEnd || Number.isNaN(until.getTime()) || until <= new Date()) {
      setError("Nhập Merchant ID, ngày kết thúc và lý do hợp lệ."); return;
    }
    if (!window.confirm("Cấp quyền miễn phí cho cửa hàng này? Thao tác sẽ được lưu audit.")) return;
    setSaving(true); setError(""); setNotice("");
    try {
      await requireJson(await requestBrowserApi("POST",
        `/api/v1/admin/billing/merchants/${encodeURIComponent(grantMerchantId)}/grants`,
        { validUntil: until.toISOString(), reason: grantReason.trim() }));
      setNotice("Đã cấp quyền miễn phí có thời hạn và ghi nhật ký.");
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể cấp quyền");
    } finally { setSaving(false); }
  }

  return <div className="admin-page-stack">
    <AdminPageHeader title="Thuê bao Seller" subtitle="Bảng giá và chính sách kinh doanh do máy chủ quản lý theo phiên bản." />
    {error ? <p role="alert" className="admin-error">{error}</p> : null}
    {notice ? <p role="status">{notice}</p> : null}
    {loading ? <p>Đang tải Billing…</p> : (
      <>
        <AdminSurface>
          <h2>Tổng quan thu phí Seller</h2>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 24 }}>
            <p>Đã thu: <strong>{formatAdminMoney(overview?.collectedVnd ?? 0)}</strong></p>
            <p>Hóa đơn đã trả: <strong>{overview?.paidInvoices ?? 0}</strong></p>
            <p>Đang chờ: <strong>{overview?.pendingInvoices ?? 0}</strong></p>
            <p>Cửa hàng có quyền bán: <strong>{overview?.entitledShops ?? 0}</strong></p>
            <p>Cảnh báo đối soát: <strong>{overview?.reconciliationAlerts ?? 0}</strong></p>
          </div>
          <p>Số liệu thu phí dịch vụ cửa hàng, không bao gồm doanh thu đơn hàng buyer.</p>
        </AdminSurface>
        <AdminSurface>
          <h2>Giao dịch gần đây</h2>
          {invoices.length === 0 ? <p>Chưa có giao dịch thuê bao.</p> : (
            <div style={{ overflowX: "auto" }}>
              <table className="admin-table">
                <thead><tr><th>Hóa đơn</th><th>Cửa hàng</th><th>Số tiền</th><th>Trạng thái</th></tr></thead>
                <tbody>{invoices.map(invoice => (
                  <tr key={invoice.id}>
                    <td>{invoice.invoiceNumber}</td><td>{invoice.merchantId}</td>
                    <td>{formatAdminMoney(invoice.amountVnd)}</td><td>{invoice.status}</td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </AdminSurface>
        <AdminSurface>
          <h2>Yêu cầu hoàn phí và tranh chấp</h2>
          <p>Hoàn tiền thực tế được xác minh ngoài CoVie trước khi Admin xác nhận. Không có API tự chuyển tiền.</p>
          {cases.length === 0 ? <p>Không có hồ sơ cần xem.</p> : (
            <div style={{ overflowX: "auto" }}>
              <table className="admin-table">
                <thead><tr><th>Hóa đơn</th><th>Loại</th><th>Lý do</th><th>Trạng thái</th><th>Xử lý</th></tr></thead>
                <tbody>{cases.map(item => (
                  <tr key={item.id}>
                    <td>{item.invoiceId}</td><td>{item.kind}</td><td>{item.reason}</td>
                    <td>{item.status}</td>
                    <td>
                      {item.status === "REQUESTED" || item.status === "REVIEWING" ? (
                        <div style={{ display: "flex", flexWrap: "wrap", gap: 6 }}>
                          {item.status === "REQUESTED" ? <button className="admin-button admin-button--secondary"
                            disabled={saving} onClick={() => void resolveCase(item.id, "REVIEWING")}>Tiếp nhận</button> : null}
                          <button className="admin-button admin-button--secondary" disabled={saving}
                            onClick={() => void resolveCase(item.id, "REJECTED")}>Từ chối</button>
                          {item.kind === "REFUND" ? <button className="admin-button admin-button--secondary"
                            disabled={saving} onClick={() => void resolveCase(item.id, "CONFIRMED")}>Xác nhận đã hoàn</button> : null}
                        </div>
                      ) : "Đã xử lý"}
                    </td>
                  </tr>
                ))}</tbody>
              </table>
            </div>
          )}
        </AdminSurface>
        <AdminSurface>
          <h2>Cấp quyền miễn phí thủ công</h2>
          <p>Chỉ Admin được thực hiện. Backend yêu cầu phê duyệt vận hành riêng và ghi audit; không tạo giao dịch payOS.</p>
          <div className="admin-field"><label htmlFor="billing-merchant-grant">Merchant ID</label>
            <input id="billing-merchant-grant" value={grantMerchantId}
              onChange={event => setGrantMerchantId(event.target.value)} placeholder="UUID cửa hàng" /></div>
          <div className="admin-field"><label htmlFor="billing-grant-until">Hạn được cấp quyền</label>
            <input id="billing-grant-until" type="datetime-local" value={grantEnd}
              onChange={event => setGrantEnd(event.target.value)} /></div>
          <div className="admin-field"><label htmlFor="billing-grant-reason">Lý do cấp quyền</label>
            <input id="billing-grant-reason" value={grantReason}
              onChange={event => setGrantReason(event.target.value)} /></div>
          <button className="admin-button admin-button--secondary" disabled={saving}
            onClick={() => void grantFreeAccess()}>Cấp quyền có thời hạn</button>
        </AdminSurface>
        <AdminSurface>
          <h2>Bảng giá hiện có hiệu lực</h2>
          {plans.length === 0 ? <p>Không có gói đang bán.</p> : plans.map(plan => (
            <p key={plan.id}><strong>{plan.name}</strong>: {formatAdminMoney(plan.priceVnd)} /
              {plan.cycleMonths} tháng, phiên bản {plan.revision}</p>
          ))}
          <div className="admin-field"><label htmlFor="billing-plan">Chọn gói muốn cập nhật</label>
            <select id="billing-plan" value={selectedCode} onChange={event => {
              setSelectedCode(event.target.value);
              const plan = plans.find(p => p.code === event.target.value);
              if (plan) { setPrice(String(plan.priceVnd)); setMonths(String(plan.cycleMonths)); }
            }}>
              {plans.map(plan => <option key={plan.code} value={plan.code}>{plan.name} ({plan.code})</option>)}
            </select>
          </div>
          <div className="admin-field"><label htmlFor="billing-price">Giá mới (VND)</label>
            <input id="billing-price" type="number" min="0" value={price} onChange={e => setPrice(e.target.value)} /></div>
          <div className="admin-field"><label htmlFor="billing-months">Số tháng</label>
            <input id="billing-months" type="number" min="1" max="120" value={months} onChange={e => setMonths(e.target.value)} /></div>
        </AdminSurface>
        <AdminSurface>
          <h2>Tạo và quản lý nhiều gói</h2>
          <p>Có thể đồng thời bán gói tháng, 3 tháng, 12 tháng. Không cần thay đổi mã nguồn khi bổ sung giá.</p>
          <div className="admin-field"><label htmlFor="billing-new-code">Mã gói (chữ in hoa, số, gạch dưới)</label>
            <input id="billing-new-code" value={newCode}
              onChange={event => setNewCode(event.target.value.toUpperCase())} placeholder="SELLER_YEARLY" /></div>
          <div className="admin-field"><label htmlFor="billing-new-name">Tên gói</label>
            <input id="billing-new-name" value={newName}
              onChange={event => setNewName(event.target.value)} placeholder="CoVie Seller 12 tháng" /></div>
          <div className="admin-field"><label htmlFor="billing-new-price">Giá niêm yết (VND)</label>
            <input id="billing-new-price" type="number" min="1" value={newPrice}
              onChange={event => setNewPrice(event.target.value)} /></div>
          <div className="admin-field"><label htmlFor="billing-new-cycle">Chu kỳ (tháng)</label>
            <input id="billing-new-cycle" type="number" min="1" max="120" value={newCycle}
              onChange={event => setNewCycle(event.target.value)} /></div>
          <div style={{ display: "flex", gap: 12, flexWrap: "wrap" }}>
            <button className="admin-button admin-button--primary" disabled={saving}
              onClick={() => void createPlan()}>Tạo gói mới</button>
            <button className="admin-button admin-button--secondary" disabled={saving || !selectedCode}
              onClick={() => void retirePlan()}>Ngừng bán gói đã chọn</button>
          </div>
          <p>Thời điểm có hiệu lực dùng ô nhập phía cuối trang.</p>
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
            <button className="admin-button admin-button--primary" disabled={saving || !selectedCode}
              onClick={() => void save("price")}>{saving ? "Đang lưu…" : "Công bố giá mới"}</button>
            <button className="admin-button admin-button--secondary" disabled={saving}
              onClick={() => void save("policy")}>{saving ? "Đang lưu…" : "Công bố chính sách mới"}</button>
          </div>
        </AdminSurface>
      </>
    )}
  </div>;
}
