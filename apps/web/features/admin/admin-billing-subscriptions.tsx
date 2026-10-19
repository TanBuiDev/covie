"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import type { components } from "@covie/api-client";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminSurface, formatAdminDate, formatAdminMoney } from "./admin-ui";

type Subscription = Required<components["schemas"]["Subscription"]>;
type Invoice = Required<components["schemas"]["Invoice"]>;
type Query = { merchantId: string; offset: number };
const pageSize = 20;

export function AdminBillingSubscriptions() {
  const [merchantId, setMerchantId] = useState("");
  const [query, setQuery] = useState<Query>();
  const [subscriptions, setSubscriptions] = useState<Subscription[]>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [invoiceQuery, setInvoiceQuery] = useState<Query>();
  const [invoices, setInvoices] = useState<Invoice[]>();
  const [invoiceLoading, setInvoiceLoading] = useState(false);
  const [invoiceError, setInvoiceError] = useState("");
  const sequence = useRef(0);
  const invoiceSequence = useRef(0);
  useEffect(() => () => { sequence.current += 1; invoiceSequence.current += 1; }, []);

  function clearInvoices() {
    invoiceSequence.current += 1;
    setInvoiceQuery(undefined); setInvoices(undefined); setInvoiceLoading(false); setInvoiceError("");
  }

  function changeMerchant(value: string) {
    sequence.current += 1;
    setMerchantId(value); setQuery(undefined); setSubscriptions(undefined); setLoading(false); setError("");
    clearInvoices();
  }

  async function load(nextQuery: Query) {
    if (nextQuery.merchantId && !/^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(nextQuery.merchantId)) {
      setError("Merchant ID phải là UUID hợp lệ hoặc để trống để xem danh sách.");
      return;
    }
    const requestSequence = ++sequence.current;
    setQuery(nextQuery); setSubscriptions(undefined); setLoading(true); setError(""); clearInvoices();
    const scope = nextQuery.merchantId ? `&merchantId=${encodeURIComponent(nextQuery.merchantId)}` : "";
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/billing/subscriptions?limit=${pageSize}&offset=${nextQuery.offset}${scope}`);
      if (!response.ok) throw await readApiError(response);
      const rows = await response.json() as Subscription[];
      if (sequence.current === requestSequence) setSubscriptions(rows);
    } catch (cause) {
      if (sequence.current === requestSequence) setError(cause instanceof Error ? cause.message : "Không tải được thuê bao.");
    } finally { if (sequence.current === requestSequence) setLoading(false); }
  }

  async function loadInvoices(nextQuery: Query) {
    const requestSequence = ++invoiceSequence.current;
    setInvoiceQuery(nextQuery); setInvoices(undefined); setInvoiceLoading(true); setInvoiceError("");
    try {
      const response = await requestBrowserApi("GET", `/api/v1/admin/billing/invoices?merchantId=${encodeURIComponent(nextQuery.merchantId)}&limit=${pageSize}&offset=${nextQuery.offset}`);
      if (!response.ok) throw await readApiError(response);
      const rows = await response.json() as Invoice[];
      if (invoiceSequence.current === requestSequence) setInvoices(rows);
    } catch (cause) {
      if (invoiceSequence.current === requestSequence) setInvoiceError(cause instanceof Error ? cause.message : "Không tải được hóa đơn liên quan.");
    } finally { if (invoiceSequence.current === requestSequence) setInvoiceLoading(false); }
  }

  return <AdminSurface>
    <h2>Tra cứu thuê bao Seller</h2>
    <p>Trạng thái lưu hiện tại, không phải quyết định quyền bán hoặc lịch sử đầy đủ các lần chuyển trạng thái.
      OWNER là membership đang ACTIVE, không khẳng định trạng thái tài khoản hay chủ sở hữu lịch sử.</p>
    <div className="admin-field"><label htmlFor="billing-subscription-merchant">Merchant ID tra cứu thuê bao</label>
      <input id="billing-subscription-merchant" value={merchantId} onChange={event => changeMerchant(event.target.value)} /></div>
    <p>Để trống Merchant ID để xem danh sách phân trang; nhập UUID để tra cứu chính xác trên máy chủ.</p>
    <p><Link href="/admin/audit">Tra cứu nhật ký quản trị</Link>: dùng loại BILLING_SUBSCRIPTION và Merchant ID
      để tìm sự kiện vòng đời đã ghi; dùng loại BILLING_INVOICE và ID hóa đơn cho sự kiện thanh toán.</p>
    <button className="admin-button admin-button--secondary" disabled={loading}
      onClick={() => void load({ merchantId: merchantId.trim(), offset: 0 })}>Tra cứu thuê bao</button>
    {loading && <p role="status">Đang tải thuê bao…</p>}
    {error && <><p role="alert">{error}</p>{query && <button className="admin-button admin-button--secondary"
      onClick={() => void load(query)}>Thử lại thuê bao</button>}</>}
    {subscriptions && query && <>
      {subscriptions.length === 0 ? <p>Không có thuê bao trong trang này.</p> :
        <div className="admin-table-scroll" role="region" aria-label="Danh sách thuê bao Seller" tabIndex={0}>
          <table className="admin-data-table" style={{ whiteSpace: "nowrap" }}>
            <thead><tr>{["Cửa hàng", "Gói", "Trạng thái lưu", "Hết kỳ", "Hết ân hạn", "Ngừng gia hạn", "Membership OWNER đang ACTIVE", "Chứng từ"].map(title => <th key={title} scope="col">{title}</th>)}</tr></thead>
            <tbody>{subscriptions.map(entry => <tr key={entry.merchantId}>
              <td>{entry.merchantName}<br />{entry.merchantId}</td><td>{entry.planCode}</td><td>{entry.status}</td>
              <td>{formatAdminDate(entry.periodEnd)}</td><td>{formatAdminDate(entry.graceUntil)}</td>
              <td>{entry.cancelAtPeriodEnd ? "Có" : "Không"}</td>
              <td>{entry.currentOwnerUserIds ? entry.currentOwnerUserIds.length ? entry.currentOwnerUserIds.join(", ")
                : "Không có membership OWNER đang ACTIVE" : "Chưa có dữ liệu OWNER"}</td>
              <td><button className="admin-button admin-button--secondary" aria-label={`Xem hóa đơn của ${entry.merchantId}`}
                onClick={() => void loadInvoices({ merchantId: entry.merchantId, offset: 0 })}>Xem hóa đơn</button></td>
            </tr>)}</tbody>
          </table>
        </div>}
      <div className="admin-filter-actions" style={{ flexWrap: "wrap" }}>
        <button className="admin-button admin-button--secondary" disabled={loading || query.offset === 0}
          onClick={() => void load({ ...query, offset: Math.max(0, query.offset - pageSize) })}>Trang thuê bao trước</button>
        <button className="admin-button admin-button--secondary" disabled={loading || subscriptions.length < pageSize}
          onClick={() => void load({ ...query, offset: query.offset + pageSize })}>Trang thuê bao tiếp</button>
      </div>
    </>}
    {invoiceQuery && <>
      <h3 style={{ overflowWrap: "anywhere" }}>Hóa đơn liên quan: {invoiceQuery.merchantId}</h3>
      <p>Chỉ hiển thị chứng từ đã lưu; không phải dòng thời gian đầy đủ của thuê bao. Không có thao tác tiền tại đây.</p>
      {invoiceLoading && <p role="status">Đang tải hóa đơn liên quan…</p>}
      {invoiceError && <><p role="alert">{invoiceError}</p><button className="admin-button admin-button--secondary"
        onClick={() => void loadInvoices(invoiceQuery)}>Thử lại hóa đơn liên quan</button></>}
      {invoices && <>
        {invoices.length === 0 ? <p>Không có hóa đơn liên quan trong trang này.</p> :
          <div className="admin-table-scroll" role="region" aria-label="Hóa đơn liên quan của thuê bao" tabIndex={0}>
            <table className="admin-data-table">
              <thead><tr>{["Hóa đơn", "Trạng thái", "Số tiền", "Tạo lúc", "Thanh toán lúc"].map(title => <th key={title} scope="col">{title}</th>)}</tr></thead>
              <tbody>{invoices.map(entry => <tr key={entry.id}><td>{entry.invoiceNumber}<br />{entry.id}</td><td>{entry.status}</td>
                <td>{formatAdminMoney(entry.amountVnd)}</td><td>{formatAdminDate(entry.createdAt)}</td><td>{formatAdminDate(entry.paidAt)}</td></tr>)}</tbody>
            </table>
          </div>}
        <div className="admin-filter-actions" style={{ flexWrap: "wrap" }}>
          <button className="admin-button admin-button--secondary" disabled={invoiceLoading || invoiceQuery.offset === 0}
            onClick={() => void loadInvoices({ ...invoiceQuery, offset: Math.max(0, invoiceQuery.offset - pageSize) })}>Trang hóa đơn trước</button>
          <button className="admin-button admin-button--secondary" disabled={invoiceLoading || invoices.length < pageSize}
            onClick={() => void loadInvoices({ ...invoiceQuery, offset: invoiceQuery.offset + pageSize })}>Trang hóa đơn tiếp</button>
        </div>
      </>}
    </>}
  </AdminSurface>;
}
