"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";

type Metric = { label: string; href: string; path: string; value?: number; unavailable?: boolean };

const metricSeed: Metric[] = [
  { label: "Người dùng", href: "/admin/users", path: "/api/v1/admin/users" },
  { label: "Người bán", href: "/admin/merchants", path: "/api/v1/admin/merchants" },
  { label: "Đơn hàng", href: "/admin/orders", path: "/api/v1/admin/orders" },
  { label: "Hàng đợi báo cáo", href: "/admin/reports", path: "/api/v1/admin/moderation-cases" },
];

function countFromPayload(payload: unknown) {
  if (Array.isArray(payload)) return payload.length;
  if (!payload || typeof payload !== "object") return undefined;
  const record = payload as Record<string, unknown>;
  if (typeof record.total === "number") return record.total;
  if (typeof record.totalElements === "number") return record.totalElements;
  if (Array.isArray(record.items)) return record.items.length;
  if (Array.isArray(record.content)) return record.content.length;
  return undefined;
}

export function AdminDashboard() {
  const [metrics, setMetrics] = useState(metricSeed);

  useEffect(() => {
    let active = true;
    void Promise.all(metricSeed.map(async metric => {
      try {
        const response = await requestBrowserApi("GET", metric.path);
        if (!response.ok) return { ...metric, unavailable: true };
        return { ...metric, value: countFromPayload(await response.json()) };
      } catch {
        return { ...metric, unavailable: true };
      }
    })).then(next => { if (active) setMetrics(next); });
    return () => { active = false; };
  }, []);

  return <div className="admin-page-stack">
    <AdminPageHeader title="Tổng quan" subtitle="CoVie Admin · Bảng điều khiển vận hành" />
    <div className="admin-metric-grid">{metrics.map(metric => <Link className="admin-metric-card" href={metric.href} key={metric.href}>
      <span>{metric.label}</span><strong>{metric.unavailable ? "—" : metric.value ?? "…"}</strong><small>{metric.unavailable ? "API hiện không khả dụng" : "Dữ liệu trực tiếp từ API quản trị"}</small>
    </Link>)}</div>
    <div className="admin-dashboard-grid">
      <AdminSurface><div className="admin-card-heading"><h2>Trạng thái vận hành</h2><AdminStatusChip label="An toàn theo contract" tone="success" /></div><p>Dashboard chỉ tổng hợp từ endpoint đã có. Không sử dụng endpoint <code>/admin/stats</code> không tồn tại trong OpenAPI hiện tại và không dựng số liệu giả từ Figma.</p></AdminSurface>
      <AdminSurface><h2>Nguyên tắc thao tác</h2><p>Đơn hàng luôn chỉ đọc. Các thao tác tạm ngưng, kiểm duyệt và cấu hình phải đi qua contract máy chủ, xác nhận và cơ chế chống gửi lặp.</p></AdminSurface>
    </div>
  </div>;
}
