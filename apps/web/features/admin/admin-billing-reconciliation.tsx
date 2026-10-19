"use client";

import { useRef, useState, type FormEvent } from "react";
import type { components } from "@covie/api-client";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

type Invoice = components["schemas"]["Invoice"];
type ReconciliationRequest = components["schemas"]["AdminBillingReconciliationRequest"];
type ReconciliationResult = components["schemas"]["AdminBillingReconciliationResponse"];

export function AdminBillingReconciliation({ invoices, onComplete }: {
  invoices: readonly Invoice[];
  onComplete: () => void | Promise<void>;
}) {
  const [invoiceId, setInvoiceId] = useState("");
  const [reason, setReason] = useState("");
  const [pending, setPending] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState<ReconciliationResult>();
  const inFlight = useRef(false);
  const retry = useRef<{ identity: string; key: string } | null>(null);
  const eligible = invoices.filter(invoice => invoice.id && invoice.allowedActions?.includes("RECONCILE"));

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (inFlight.current || !reason.trim() || !eligible.some(invoice => invoice.id === invoiceId)) return;
    const body: ReconciliationRequest = { reason: reason.trim() };
    const identity = JSON.stringify({ invoiceId, body });
    if (retry.current?.identity !== identity) retry.current = { identity, key: `admin-reconcile-${crypto.randomUUID()}` };
    const key = retry.current.key;
    inFlight.current = true;
    setPending(true);
    setError("");
    setResult(undefined);
    try {
      const response = await requestBrowserApi("POST", `/api/v1/admin/billing/invoices/${invoiceId}/reconciliation`,
        body, { "Idempotency-Key": key });
      if (!response.ok) throw await readApiError(response);
      const checked: ReconciliationResult = await response.json();
      setResult(checked);
      retry.current = null;
      await onComplete();
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Không thể đối soát. Thử lại cùng yêu cầu để kiểm tra kết quả.");
    } finally {
      inFlight.current = false;
      setPending(false);
    }
  }

  if (eligible.length === 0) return null;

  return (
    <section aria-labelledby="billing-reconciliation-heading">
      <h3 id="billing-reconciliation-heading">Đối soát thanh toán</h3>
      <p>Kiểm tra trạng thái đã xác minh từ payOS, không tạo khoản thu mới hoặc hoàn tiền. Không nhập khóa bí mật hay thông tin ngân hàng vào lý do.</p>
      <form onSubmit={event => void submit(event)} aria-busy={pending}>
        <div className="admin-field">
          <label htmlFor="billing-reconciliation-invoice">Hóa đơn cần đối soát</label>
          <select id="billing-reconciliation-invoice" required disabled={pending} value={invoiceId}
            onChange={event => { setInvoiceId(event.target.value); setResult(undefined); }}>
            <option value="">Chọn hóa đơn</option>
            {eligible.map(invoice => <option key={invoice.id} value={invoice.id}>{invoice.invoiceNumber}</option>)}
          </select>
        </div>
        <div className="admin-field">
          <label htmlFor="billing-reconciliation-reason">Lý do đối soát</label>
          <textarea id="billing-reconciliation-reason" required maxLength={400} rows={3} disabled={pending}
            value={reason} onChange={event => { setReason(event.target.value); setResult(undefined); }} />
        </div>
        <button className="admin-button admin-button--secondary" type="submit"
          disabled={pending || !reason.trim() || !eligible.some(invoice => invoice.id === invoiceId)}>
          {pending ? "Đang đối soát…" : "Đối soát với payOS"}
        </button>
      </form>
      {error ? <p role="alert">{error}</p> : null}
      {result ? <div role="status">
        <p>Hóa đơn: {result.invoiceStatus}; payOS: {result.providerStatus}; kết quả: {result.discrepancy}.</p>
        <p>Kiểm tra lúc: {result.checkedAt ? new Intl.DateTimeFormat("vi-VN", {
          dateStyle: "short", timeStyle: "medium", timeZone: "Asia/Ho_Chi_Minh",
        }).format(new Date(result.checkedAt)) : "Không rõ"} (Asia/Ho_Chi_Minh).</p>
        <p>Kết quả của lần kiểm tra này, không phải trạng thái cập nhật liên tục.</p>
      </div> : null}
    </section>
  );
}
