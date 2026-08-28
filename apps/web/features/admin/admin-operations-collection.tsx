"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate, presentAccountStatus, presentModerationStatus } from "./admin-ui";

export type AdminOperationsResource = "shops" | "listings" | "needs";
type PagePayload = { items?: Record<string, unknown>[]; total?: number; page?: number; size?: number };

const definitions = {
  shops: {
    title: "Cửa hàng & Điểm bán",
    subtitle: "Trạng thái vận hành theo từng điểm bán",
    statuses: [["", "Tất cả"], ["ACTIVE", "Đang hoạt động"], ["PAUSED", "Tạm dừng"], ["SUSPENDED", "Tạm ngưng"]],
    statusParam: "merchantStatus",
  },
  listings: {
    title: "Nội dung đăng bán",
    subtitle: "Catalog và liên kết kiểm duyệt",
    statuses: [["", "Tất cả"], ["DRAFT", "Bản nháp"], ["ACTIVE", "Đang hiển thị"], ["PAUSED", "Tạm dừng"]],
    statusParam: "status",
  },
  needs: {
    title: "Cần mua",
    subtitle: "Yêu cầu và trạng thái matching, không hiển thị danh tính người mua",
    statuses: [["", "Tất cả"], ["ACTIVE", "Đang hoạt động"], ["FULFILLED", "Đã hoàn tất"], ["EXPIRED", "Hết hạn"], ["CANCELLED", "Đã hủy"]],
    statusParam: "status",
  },
} as const;

function status(raw: unknown) {
  if (raw === "FULFILLED") return { label: "Đã hoàn tất", tone: "success" as const };
  if (raw === "EXPIRED" || raw === "CANCELLED") return { label: raw === "EXPIRED" ? "Hết hạn" : "Đã hủy", tone: "neutral" as const };
  if (raw === "DRAFT") return { label: "Bản nháp", tone: "neutral" as const };
  return presentAccountStatus(raw);
}

function ShopRow({ row }: { row: Record<string, unknown> }) {
  const merchant = status(row.merchantStatus);
  const location = status(row.locationStatus);
  return <tr>
    <td><strong>{String(row.merchantName ?? "Cửa hàng")}</strong><small>{String(row.merchantId ?? "—")}</small></td>
    <td>{row.locationId ? <><strong>{String(row.locationName ?? "Điểm bán")}</strong><small>{String(row.addressText ?? "—")}</small></> : "Chưa có điểm bán"}</td>
    <td><AdminStatusChip label={merchant.label} tone={merchant.tone} /></td>
    <td><AdminStatusChip label={location.label} tone={location.tone} /></td>
    <td>{[row.pickupEnabled && "Nhận tại chỗ", row.sellerDeliveryEnabled && "Giao hàng", row.serviceEnabled && "Dịch vụ"].filter(Boolean).join(" · ") || "—"}</td>
    <td>{formatAdminDate(row.updatedAt)}</td>
  </tr>;
}

function ListingRow({ row }: { row: Record<string, unknown> }) {
  const listing = status(row.listingStatus);
  const moderation = presentModerationStatus(row.moderationStatus);
  return <tr>
    <td><strong>{String(row.name ?? "Nội dung")}</strong><small>{String(row.type ?? "—")} · {String(row.listingKind ?? "—")}</small></td>
    <td>{String(row.merchantName ?? "—")}</td>
    <td><AdminStatusChip label={listing.label} tone={listing.tone} /></td>
    <td><AdminStatusChip label={moderation.label} tone={moderation.tone} /></td>
    <td>{row.moderationCaseId ? <Link href={`/admin/moderation/${encodeURIComponent(String(row.moderationCaseId))}`}>Mở hồ sơ</Link> : "—"}</td>
    <td>{formatAdminDate(row.updatedAt)}</td>
  </tr>;
}

function NeedRow({ row }: { row: Record<string, unknown> }) {
  const presentation = status(row.status);
  return <tr>
    <td><strong>{String(row.title ?? "Nhu cầu")}</strong><small>{String(row.type ?? "—")} · {String(row.categoryName ?? "—")}</small></td>
    <td><AdminStatusChip label={presentation.label} tone={presentation.tone} /></td>
    <td>{String(row.responseCount ?? 0)}</td>
    <td>{typeof row.searchRadiusMeters === "number" ? `${new Intl.NumberFormat("vi-VN").format(row.searchRadiusMeters)} m` : "—"}</td>
    <td>{formatAdminDate(row.expiresAt)}</td>
    <td>{formatAdminDate(row.updatedAt)}</td>
  </tr>;
}

export function AdminOperationsCollection({ resource }: { resource: AdminOperationsResource }) {
  const definition = definitions[resource];
  const [items, setItems] = useState<Record<string, unknown>[]>();
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [appliedQuery, setAppliedQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [error, setError] = useState<string>();
  const size = 50;
  const path = useMemo(() => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (appliedQuery) params.set("q", appliedQuery);
    if (statusFilter) params.set(definition.statusParam, statusFilter);
    return `/api/v1/admin/${resource}?${params}`;
  }, [appliedQuery, definition.statusParam, page, resource, statusFilter]);

  useEffect(() => {
    let active = true;
    setItems(undefined);
    setError(undefined);
    void requestBrowserApi("GET", path).then(async (response) => {
      if (!response.ok) throw await readApiError(response);
      return response.json() as Promise<PagePayload>;
    }).then((payload) => {
      if (!active) return;
      setItems(payload.items ?? []);
      setTotal(payload.total ?? 0);
    }).catch((cause) => {
      if (active) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu quản trị.");
    });
    return () => { active = false; };
  }, [path]);

  function search(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setAppliedQuery(query.trim());
  }

  const headers = resource === "shops"
    ? ["Cửa hàng", "Điểm bán", "Cửa hàng", "Điểm bán", "Khả năng", "Cập nhật"]
    : resource === "listings"
      ? ["Nội dung", "Cửa hàng", "Hiển thị", "Kiểm duyệt", "Hồ sơ", "Cập nhật"]
      : ["Nhu cầu", "Trạng thái", "Phản hồi", "Bán kính", "Hết hạn", "Cập nhật"];

  return <div className="admin-page-stack">
    <AdminPageHeader title={definition.title} subtitle={definition.subtitle} action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <AdminSurface>
      <form className="admin-filter-grid" onSubmit={search}>
        <label>Tìm kiếm<input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tên hoặc nội dung" /></label>
        <label>Trạng thái<select value={statusFilter} onChange={(event) => { setStatusFilter(event.target.value); setPage(0); }}>
          {definition.statuses.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
        </select></label>
        <div className="admin-filter-actions"><button className="admin-button" type="submit">Tìm</button></div>
      </form>
    </AdminSurface>
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!items && !error ? <AdminSurface><p className="admin-muted">Đang tải dữ liệu…</p></AdminSurface> : null}
    {items ? <AdminSurface className="admin-table-surface"><div className="admin-table-scroll"><table className="admin-data-table">
      <thead><tr>{headers.map((header, index) => <th key={`${header}-${index}`}>{header}</th>)}</tr></thead>
      <tbody>{items.length ? items.map((row, index) => resource === "shops"
        ? <ShopRow key={String(row.locationId ?? row.merchantId ?? index)} row={row} />
        : resource === "listings" ? <ListingRow key={String(row.id ?? index)} row={row} />
          : <NeedRow key={String(row.id ?? index)} row={row} />)
        : <tr><td colSpan={headers.length}><span className="admin-muted">Chưa có dữ liệu.</span></td></tr>}</tbody>
    </table></div>
    <div className="admin-pagination"><span>{total} bản ghi</span><div>
      <button type="button" disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Trang trước</button>
      <button type="button" disabled={(page + 1) * size >= total} onClick={() => setPage((value) => value + 1)}>Trang sau</button>
    </div></div></AdminSurface> : null}
  </div>;
}
