"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CreditCard, ArrowSquareOut, ArrowClockwise, ShieldCheck } from "@phosphor-icons/react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import type { components } from "@covie/api-client";

type Plan = Required<components["schemas"]["PlanOffer"]>;
type Eligibility = components["schemas"]["Eligibility"];
type Invoice = Required<components["schemas"]["InvoiceSummary"]>;
type Checkout = Required<components["schemas"]["CheckoutResult"]>;
type Subscription = { status: string; planCode: string | null; cancelAtPeriodEnd: boolean;
  periodStart: string | null; periodEnd: string | null };
type VoucherQuote = { code: string | null; discountVnd: number; amountDueVnd: number };


const money = (amount: number) => new Intl.NumberFormat("vi-VN").format(amount) + "đ";
const date = (value: string) => new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(new Date(value));

export function SellerBillingPanel({ merchantId, merchantName }: { merchantId: string; merchantName: string }) {
  const base = `/api/v1/merchants/${encodeURIComponent(merchantId)}/billing`;
  const [plans, setPlans] = useState<Plan[]>([]);
  const [eligibility, setEligibility] = useState<Eligibility>();
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [subscription, setSubscription] = useState<Subscription>();
  const [selected, setSelected] = useState("");
  const [voucherCode, setVoucherCode] = useState("");
  const [voucherQuote, setVoucherQuote] = useState<(VoucherQuote & { planCode: string; enteredCode: string })>();
  const [checkout, setCheckout] = useState<Checkout>();
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState("");
  const idempotency = useRef<string | null>(null);
  const active = useRef(true);

  const load = useCallback(async () => {
    const [planRes, entitlementRes, invoiceRes, subscriptionRes] = await Promise.all([
      requestBrowserApi("GET", "/api/v1/billing/plans"),
      requestBrowserApi("GET", base + "/summary"),
      requestBrowserApi("GET", base + "/invoices"),
      requestBrowserApi("GET", base + "/subscription"),
    ]);
    for (const response of [planRes, entitlementRes, invoiceRes, subscriptionRes]) {
      if (!response.ok) throw await readApiError(response);
    }
    const values = (await planRes.json()) as Plan[];
    const ent = (await entitlementRes.json()) as Eligibility;
    const bills = (await invoiceRes.json()) as Invoice[];
    const currentSubscription = (await subscriptionRes.json()) as Subscription;
    if (!active.current) return;
    setPlans(values); setSelected(previous => previous || values[0]?.code || "");
    setEligibility(ent); setInvoices(bills); setSubscription(currentSubscription);
  }, [base]);

  useEffect(() => {
    active.current = true;
    load().catch(cause => { if (active.current) setError(cause instanceof Error ? cause.message : "Không thể tải hóa đơn"); })
      .finally(() => { if (active.current) setLoading(false); });
    return () => { active.current = false; };
  }, [load]);

  useEffect(() => {
    if (!checkout?.invoiceId || checkout.invoiceStatus === "PAID") return;
    const interval = setInterval(async () => {
      try {
        const response = await requestBrowserApi("GET", base + "/invoices/" + checkout.invoiceId);
        if (!response.ok) return;
        const state = await response.json() as { invoiceStatus: string };
        if (active.current && state.invoiceStatus === "PAID") {
          setCheckout(current => current ? { ...current, invoiceStatus: "PAID" } : current);
          idempotency.current = null;
          void load();
        }
      } catch { /* temporary network errors do not authorize payment */ }
    }, 5000);
    return () => clearInterval(interval);
  }, [base, checkout?.invoiceId, checkout?.invoiceStatus, load]);

  async function cancelRenewal() {
    if (paying || !window.confirm("Đánh dấu kết thúc thuê bao sau kỳ hiện tại? Bạn sẽ không bị thu tiền tự động; quyền đã trả phí vẫn được giữ.")) return;
    setPaying(true); setError("");
    try {
      const response = await requestBrowserApi("POST", base + "/cancel-renewal");
      if (!response.ok) throw await readApiError(response);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể cập nhật thuê bao");
    } finally { setPaying(false); }
  }

  async function resumeRenewal() {
    if (paying || !window.confirm("Bỏ yêu cầu dừng thuê bao cuối kỳ? Không có khoản phí nào được tự động thu.")) return;
    setPaying(true); setError("");
    try {
      const response = await requestBrowserApi("POST", base + "/resume-renewal");
      if (!response.ok) throw await readApiError(response);
      await load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể bỏ yêu cầu dừng thuê bao");
    } finally { setPaying(false); }
  }

  async function requestFinanceCase(invoice: Invoice, kind: "REFUND" | "DISPUTE") {
    if (paying || invoice.status !== "PAID") return;
    const reason = window.prompt(kind === "REFUND"
      ? "Lý do yêu cầu hoàn phí cho hóa đơn này:" : "Nội dung tranh chấp thanh toán:");
    if (!reason?.trim()) return;
    setPaying(true); setError("");
    try {
      const response = await requestBrowserApi("POST",
        base + "/invoices/" + encodeURIComponent(invoice.invoiceId) + "/finance-cases",
        { kind, reason: reason.trim() });
      if (!response.ok) throw await readApiError(response);
      window.alert("Yêu cầu đã được ghi nhận. CoVie sẽ kiểm tra và phản hồi qua kênh hỗ trợ.");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể gửi yêu cầu tài chính");
    } finally { setPaying(false); }
  }

  async function previewVoucher() {
    if (paying || !selected) return;
    setPaying(true); setError(""); setVoucherQuote(undefined);
    const code = voucherCode.trim().toUpperCase();
    try {
      const query = new URLSearchParams({ planCode: selected });
      if (code) query.set("voucherCode", code);
      const response = await requestBrowserApi("GET", base + "/quote?" + query.toString());
      if (!response.ok) throw await readApiError(response);
      const data = await response.json() as VoucherQuote;
      setVoucherQuote({ ...data, planCode: selected, enteredCode: code });
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Mã ưu đãi không khả dụng");
    } finally { setPaying(false); }
  }

  async function createCheckout() {
    if (paying || !selected) return;
    const code = voucherCode.trim().toUpperCase();
    if (code && (voucherQuote?.enteredCode !== code || voucherQuote?.planCode !== selected)) {
      setError("Vui lòng kiểm tra và xác nhận số tiền sau giảm trước khi tạo link thanh toán.");
      return;
    }
    setPaying(true); setError("");
    if (!idempotency.current) idempotency.current = crypto.randomUUID();
    try {
      const plan = plans.find(value => value.code === selected);
      if (!plan) throw new Error("Gói không còn được mở bán. Tải lại bảng giá.");
      const expectedAmountVnd = code ? voucherQuote?.amountDueVnd : plan.priceVnd;
      const response = await requestBrowserApi("POST", base + "/checkout",
        { planCode: selected, voucherCode: code || null,
          expectedAmountVnd, expectedPlanRevision: plan.revision },
        { "Idempotency-Key": idempotency.current });
      if (!response.ok) throw await readApiError(response);
      const link = await response.json() as Checkout;
      if (!link.checkoutUrl.startsWith("https://pay.payos.vn/")) {
        throw new Error("Đường dẫn thanh toán payOS không hợp lệ.");
      }
      setCheckout(link);
      void load();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tạo link thanh toán");
    } finally { setPaying(false); }
  }

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Thuê bao</p>
        <h1><CreditCard size={27} weight="duotone" aria-hidden="true" /> Thuê bao cửa hàng</h1>
        <p>{merchantName} — quản lý phí mở bán, kỳ hạn và lịch sử thanh toán.</p>
      </header>
      {error ? <p role="alert" className="admin-error">{error}</p> : null}
      {loading ? <p role="status">Đang tải thuê bao…</p> : (
        <>
          <section className="admin-surface">
            <h2>Quyền mở bán</h2>
            <p>{eligibility?.enforced
              ? (eligibility.canSell ? "Cửa hàng đang có quyền mở bán." : "Chưa có thuê bao hợp lệ để mở bán.")
              : "Chế độ thu phí chưa được kích hoạt cho các cửa hàng hiện tại."}</p>
            {eligibility?.expiry ? <p>Quyền thuê bao có hiệu lực tới: {date(eligibility.expiry)}.</p> : null}
            {subscription && subscription.status !== "NONE" ? (
              <>
                <p>Gói hiện tại: {subscription.planCode} — trạng thái: {subscription.status}.</p>
                {subscription.periodEnd ? <p>Kết thúc kỳ: {date(subscription.periodEnd)}.</p> : null}
                {subscription.cancelAtPeriodEnd ? (
                  <>
                    <p role="status">Đã đánh dấu không tiếp tục kỳ mới; quyền đã trả phí vẫn có hiệu lực đến cuối kỳ.</p>
                    {(subscription.status === "ACTIVE" || subscription.status === "TRIALING") ? (
                      <button className="admin-button admin-button--secondary" type="button" disabled={paying}
                        onClick={() => void resumeRenewal()}>Bỏ yêu cầu dừng kỳ sau</button>
                    ) : null}
                  </>
                ) : subscription.status === "ACTIVE" || subscription.status === "TRIALING" ? (
                  <button className="admin-button admin-button--secondary" type="button" disabled={paying}
                    onClick={() => void cancelRenewal()}>Không gia hạn sau kỳ hiện tại</button>
                ) : null}
              </>
            ) : null}
            <p>CoVie chỉ xác nhận thanh toán sau khi nhận được webhook hợp lệ từ payOS.</p>
            <button className="admin-button admin-button--secondary" type="button" onClick={() => void load()}
              aria-label="Tải lại trạng thái thanh toán"><ArrowClockwise size={17} /> Làm mới</button>
          </section>
          <section className="admin-surface">
            <h2>Gói thuê bao đang được công bố</h2>
            {plans.length === 0 ? <p>Chưa có gói đang bán.</p> : (
              <>
                <label className="admin-field">
                  <span>Chọn gói</span>
                  <select value={selected} onChange={event => {
                    setSelected(event.target.value); setVoucherQuote(undefined); idempotency.current = null;
                  }}>
                    {plans.map(plan => <option key={plan.id} value={plan.code}>
                      {plan.name} — {money(plan.priceVnd)} / {plan.cycleMonths} tháng
                    </option>)}
                  </select>
                </label>
                <label className="admin-field">
                  <span>Mã ưu đãi (không bắt buộc)</span>
                  <input type="text" autoComplete="off" value={voucherCode}
                    maxLength={32} placeholder="Nhập mã voucher thuê bao"
                    onChange={event => {
                      setVoucherCode(event.target.value.toUpperCase());
                      setVoucherQuote(undefined); idempotency.current = null;
                    }} />
                </label>
                <button type="button" className="admin-button admin-button--secondary"
                  disabled={paying} onClick={() => void previewVoucher()}>
                  {paying ? "Đang kiểm tra…" : "Kiểm tra số tiền thanh toán"}
                </button>
                {voucherQuote && voucherQuote.planCode === selected &&
                  voucherQuote.enteredCode === voucherCode.trim().toUpperCase() ? (
                  <p role="status">
                    {voucherQuote.code ? `Giảm ${money(voucherQuote.discountVnd)}. ` : ""}
                    <strong>Phải thanh toán: {money(voucherQuote.amountDueVnd)}</strong>
                  </p>
                ) : null}
                <p>Thanh toán từng lần bằng chuyển khoản VietQR qua payOS. CoVie chưa tự động trừ tiền gia hạn; chỉ chủ cửa hàng được tạo giao dịch.</p>
                <button className="admin-button admin-button--primary" disabled={paying} onClick={() => void createCheckout()}>
                  {paying ? "Đang tạo link…" : "Tạo link thanh toán payOS"}
                </button>
              </>
            )}
            {checkout ? (
              <div role="status" style={{ paddingTop: 16 }}>
                <h3>{checkout.invoiceStatus === "PAID" ? "Đã xác nhận thanh toán" : "Đang chờ xác nhận"}</h3>
                <p>Mã giao dịch #{checkout.orderCode} — {money(checkout.amountVnd)}. Hết hạn link: {date(checkout.expiresAt)}.</p>
                {checkout.invoiceStatus !== "PAID" ? (
                  <a className="admin-button admin-button--secondary" href={checkout.checkoutUrl}
                    target="_blank" rel="noopener noreferrer">
                    Mở payOS để thanh toán <ArrowSquareOut size={18} aria-hidden="true" />
                  </a>
                ) : <p><ShieldCheck size={20} aria-hidden="true" /> CoVie đã nhận xác nhận thanh toán từ máy chủ.</p>}
              </div>
            ) : null}
          </section>
          <section className="admin-surface">
            <h2>Lịch sử hóa đơn</h2>
            {invoices.length === 0 ? <p>Chưa có hóa đơn thuê bao.</p> : (
              <div style={{ overflowX: "auto" }}>
                <table className="admin-table">
                  <thead><tr><th>Mã hóa đơn</th><th>Số tiền</th><th>Kỳ hạn</th><th>Trạng thái</th><th>Hỗ trợ</th></tr></thead>
                  <tbody>{invoices.map(invoice => (
                    <tr key={invoice.invoiceId}>
                      <td>{invoice.invoiceNumber}</td><td>{money(invoice.amountVnd)}</td>
                      <td>{date(invoice.periodStart)} – {date(invoice.periodEnd)}</td><td>{invoice.status}</td>
                      <td>{invoice.status === "PAID" ? (
                        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                          <button type="button" className="admin-button admin-button--secondary"
                            disabled={paying} onClick={() => void requestFinanceCase(invoice, "REFUND")}>Yêu cầu hoàn phí</button>
                          <button type="button" className="admin-button admin-button--secondary"
                            disabled={paying} onClick={() => void requestFinanceCase(invoice, "DISPUTE")}>Tranh chấp</button>
                        </div>
                      ) : "—"}</td>
                    </tr>
                  ))}</tbody>
                </table>
              </div>
            )}
          </section>
        </>
      )}
    </section>
  );
}
