"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CreditCard, ArrowSquareOut, ArrowClockwise, ShieldCheck } from "@phosphor-icons/react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

type Plan = { id: string; code: string; name: string; revision: number; priceVnd: number; cycleMonths: number };
type Eligibility = { canSell: boolean; enforced: boolean; reasonCode: string; expiry: string | null };
type Invoice = { invoiceId: string; invoiceNumber: string; status: string; amountVnd: number; periodStart: string; periodEnd: string; createdAt: string };
type Checkout = { invoiceId: string; orderCode: number; amountVnd: number; checkoutUrl: string; qrPayload: string; expiresAt: string; invoiceStatus: string };

const money = (amount: number) => new Intl.NumberFormat("vi-VN").format(amount) + "đ";
const date = (value: string) => new Intl.DateTimeFormat("vi-VN", { dateStyle: "short" }).format(new Date(value));

export function SellerBillingPanel({ merchantId, merchantName }: { merchantId: string; merchantName: string }) {
  const base = `/api/v1/merchants/${encodeURIComponent(merchantId)}/billing`;
  const [plans, setPlans] = useState<Plan[]>([]);
  const [eligibility, setEligibility] = useState<Eligibility>();
  const [invoices, setInvoices] = useState<Invoice[]>([]);
  const [selected, setSelected] = useState("");
  const [checkout, setCheckout] = useState<Checkout>();
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState("");
  const idempotency = useRef<string | null>(null);
  const active = useRef(true);

  const load = useCallback(async () => {
    const [planRes, entitlementRes, invoiceRes] = await Promise.all([
      requestBrowserApi("GET", "/api/v1/billing/plans"),
      requestBrowserApi("GET", base + "/summary"),
      requestBrowserApi("GET", base + "/invoices"),
    ]);
    for (const response of [planRes, entitlementRes, invoiceRes]) {
      if (!response.ok) throw await readApiError(response);
    }
    const values = (await planRes.json()) as Plan[];
    const ent = (await entitlementRes.json()) as Eligibility;
    const bills = (await invoiceRes.json()) as Invoice[];
    if (!active.current) return;
    setPlans(values); setSelected(previous => previous || values[0]?.code || "");
    setEligibility(ent); setInvoices(bills);
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

  async function createCheckout() {
    if (paying || !selected) return;
    setPaying(true); setError("");
    if (!idempotency.current) idempotency.current = crypto.randomUUID();
    try {
      const response = await requestBrowserApi("POST", base + "/checkout",
        { planCode: selected }, { "Idempotency-Key": idempotency.current });
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
                  <select value={selected} onChange={event => { setSelected(event.target.value); idempotency.current = null; }}>
                    {plans.map(plan => <option key={plan.id} value={plan.code}>
                      {plan.name} — {money(plan.priceVnd)} / {plan.cycleMonths} tháng
                    </option>)}
                  </select>
                </label>
                <p>Thanh toán bằng chuyển khoản VietQR. Chỉ chủ cửa hàng có thể tạo giao dịch.</p>
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
                  <thead><tr><th>Mã hóa đơn</th><th>Số tiền</th><th>Kỳ hạn</th><th>Trạng thái</th></tr></thead>
                  <tbody>{invoices.map(invoice => (
                    <tr key={invoice.invoiceId}>
                      <td>{invoice.invoiceNumber}</td><td>{money(invoice.amountVnd)}</td>
                      <td>{date(invoice.periodStart)} – {date(invoice.periodEnd)}</td><td>{invoice.status}</td>
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
