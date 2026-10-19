"use client";

import { useEffect, useRef, useState } from "react";
import type { components } from "@covie/api-client";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminSurface, formatAdminDate, formatAdminMoney } from "./admin-ui";

type PriceHistory = Required<components["schemas"]["PriceHistory"]>;
type PolicyHistory = Required<components["schemas"]["PolicyHistory"]>;
type Query = { kind: "price" | "policy"; code: string; offset: number };
type History = { kind: "price"; items: PriceHistory[] } | { kind: "policy"; items: PolicyHistory[] };
const pageSize = 20;

export function AdminBillingHistory() {
  const [code, setCode] = useState("");
  const [query, setQuery] = useState<Query>();
  const [history, setHistory] = useState<History>();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const sequence = useRef(0);
  useEffect(() => () => { sequence.current += 1; }, []);

  async function load(nextQuery: Query) {
    if (nextQuery.kind === "price" && !/^[A-Z0-9_]{1,60}$/.test(nextQuery.code)) {
      setError("Nhập mã gói hợp lệ để tra cứu lịch sử.");
      return;
    }
    const requestSequence = ++sequence.current;
    setQuery(nextQuery); setHistory(undefined); setLoading(true); setError("");
    const route = nextQuery.kind === "price"
      ? `/api/v1/admin/billing/plans/${encodeURIComponent(nextQuery.code)}/prices/history`
      : "/api/v1/admin/billing/policy/history";
    try {
      const response = await requestBrowserApi("GET", `${route}?limit=${pageSize}&offset=${nextQuery.offset}`);
      if (!response.ok) throw await readApiError(response);
      const result: History = nextQuery.kind === "price"
        ? { kind: "price", items: await response.json() as PriceHistory[] }
        : { kind: "policy", items: await response.json() as PolicyHistory[] };
      if (sequence.current === requestSequence) setHistory(result);
    } catch (cause) {
      if (sequence.current === requestSequence) setError(cause instanceof Error ? cause.message : "Không tải được lịch sử công bố.");
    } finally {
      if (sequence.current === requestSequence) setLoading(false);
    }
  }

  function changeCode(value: string) {
    sequence.current += 1;
    setCode(value); setQuery(undefined); setHistory(undefined); setLoading(false); setError("");
  }

  return <AdminSurface>
    <h2>Lịch sử công bố Billing</h2>
    <p>Chỉ đọc các phiên bản đã công bố, gồm lịch áp dụng tương lai và gói đã ngừng bán. Không sửa hoặc khôi phục từ lịch sử.</p>
    <div className="admin-field"><label htmlFor="billing-history-code">Mã gói tra cứu lịch sử</label>
      <input id="billing-history-code" value={code} onChange={event => changeCode(event.target.value)} /></div>
    <div className="admin-filter-actions" style={{ flexWrap: "wrap" }}>
      <button className="admin-button admin-button--secondary" disabled={loading}
        onClick={() => void load({ kind: "price", code, offset: 0 })}>Xem lịch sử giá</button>
      <button className="admin-button admin-button--secondary" disabled={loading}
        onClick={() => void load({ kind: "policy", code: "", offset: 0 })}>Xem lịch sử chính sách</button>
    </div>
    {loading && <p role="status">Đang tải lịch sử…</p>}
    {error && <><p role="alert">{error}</p>{query && <button className="admin-button admin-button--secondary"
      onClick={() => void load(query)}>Thử lại lịch sử</button>}</>}
    {history && query && <>
      <h3 style={{ overflowWrap: "anywhere" }}>{query.kind === "price" ? `Lịch sử giá: ${query.code}` : "Lịch sử chính sách mở cửa hàng"}</h3>
      {history.items.length === 0 ? <p>Không có phiên bản trong trang này.</p> :
        <div className="admin-table-scroll" role="region" aria-label="Lịch sử phiên bản Billing" tabIndex={0}>
          <table className="admin-data-table">
            <thead><tr><th scope="col">Phiên bản</th><th scope="col">Thông số</th>
              <th scope="col">Công bố lúc</th><th scope="col">Có hiệu lực từ</th><th scope="col">Gói</th></tr></thead>
            <tbody>{history.kind === "price" ? history.items.map(entry => <tr key={entry.version.id}>
              <td>{entry.version.revision}</td><td>{formatAdminMoney(entry.version.priceVnd)} / {entry.version.cycleMonths} tháng</td>
              <td>{formatAdminDate(entry.publishedAt)}</td><td>{formatAdminDate(entry.version.effectiveAt)}</td>
              <td>{entry.retired ? "Đã ngừng bán" : "Chưa ngừng bán"}</td>
            </tr>) : history.items.map(entry => <tr key={entry.version.id}>
              <td>{entry.version.revision}</td><td>{entry.version.onboardingMode}; dùng thử {entry.version.trialDays} ngày; ân hạn {entry.version.graceDays} ngày</td>
              <td>{formatAdminDate(entry.publishedAt)}</td><td>{formatAdminDate(entry.version.effectiveAt)}</td><td>Toàn hệ thống</td>
            </tr>)}</tbody>
          </table>
        </div>}
      <div className="admin-filter-actions" style={{ flexWrap: "wrap" }}>
        <button className="admin-button admin-button--secondary" disabled={loading || query.offset === 0}
          onClick={() => void load({ ...query, offset: Math.max(0, query.offset - pageSize) })}>Trang lịch sử trước</button>
        <button className="admin-button admin-button--secondary" disabled={loading || history.items.length < pageSize}
          onClick={() => void load({ ...query, offset: query.offset + pageSize })}>Trang lịch sử tiếp</button>
      </div>
    </>}
  </AdminSurface>;
}
