"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";
import { AdminPageHeader, AdminStatusChip, AdminSurface, formatAdminDate } from "./admin-ui";

type EventRow = Record<string, unknown> & { id?: string; createdAt?: string; occurredAt?: string; eventType?: string; type?: string };

function normalize(payload: unknown): EventRow[] {
  if (Array.isArray(payload)) return payload as EventRow[];
  if (!payload || typeof payload !== "object") return [];
  const record = payload as Record<string, unknown>;
  for (const key of ["items", "content", "events"]) if (Array.isArray(record[key])) return record[key] as EventRow[];
  return [];
}

export function AdminOrderHistory({ orderId }: { orderId: string }) {
  const [events, setEvents] = useState<EventRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string>();
  useEffect(() => {
    let active = true;
    requestBrowserApi("GET", `/api/v1/admin/orders/${encodeURIComponent(orderId)}/events`)
      .then(async response => { if (!response.ok) throw await readApiError(response); return response.json(); })
      .then(payload => { if (active) setEvents(normalize(payload)); })
      .catch(cause => { if (active) setError(cause instanceof Error ? cause.message : "Không thể tải lịch sử đơn hàng"); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [orderId]);
  return <div className="admin-page-stack">
    <AdminPageHeader title="Lịch sử đơn hàng" subtitle={`Đơn ${orderId} · Dữ liệu chỉ đọc`} action={<AdminStatusChip label="Chỉ đọc" tone="neutral" />} />
    <Link className="admin-inline-link" href="/admin/orders">← Quay lại tra cứu đơn hàng</Link>
    <AdminSurface className="admin-contract-note"><strong>Ranh giới an toàn</strong><p>Lịch sử phản ánh event/snapshot do máy chủ trả về. Admin không có thao tác ép trạng thái và trường không có trong contract sẽ không được suy diễn.</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải lịch sử…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p></AdminSurface> : null}
    {!loading && !error ? <div className="admin-timeline">{events.length ? events.map((event, index) => <AdminSurface className="admin-timeline__item" key={String(event.id ?? index)}><div><strong>{String(event.eventType ?? event.type ?? "Sự kiện đơn hàng")}</strong><span>{formatAdminDate(event.occurredAt ?? event.createdAt)}</span></div><pre>{JSON.stringify(event, null, 2)}</pre></AdminSurface>) : <AdminSurface><p className="admin-muted">Chưa có sự kiện lịch sử được API trả về.</p></AdminSurface>}</div> : null}
  </div>;
}
