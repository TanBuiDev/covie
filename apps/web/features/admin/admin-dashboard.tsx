"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import {
  ArrowRight,
  CheckCircle,
  Flag,
  LockKey,
  Package,
  Scroll,
  ShieldCheck,
  ShieldWarning,
  Storefront,
  Users,
} from "@phosphor-icons/react";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";

type Metric = {
  label: string;
  href: string;
  path: string;
  value?: number;
  unavailable?: boolean;
  icon: typeof Users;
  color: string;
  bg: string;
};

const metricSeed: Metric[] = [
  { label: "Người dùng", href: "/admin/users", path: "/api/v1/admin/users", icon: Users, color: "#047857", bg: "rgba(4, 120, 87, 0.08)" },
  { label: "Người bán", href: "/admin/merchants", path: "/api/v1/admin/merchants", icon: Storefront, color: "#2563eb", bg: "rgba(37, 99, 235, 0.08)" },
  { label: "Đơn hàng", href: "/admin/orders", path: "/api/v1/admin/orders", icon: Package, color: "#ea580c", bg: "rgba(234, 88, 12, 0.08)" },
  { label: "Hàng đợi báo cáo", href: "/admin/reports", path: "/api/v1/admin/moderation-cases", icon: ShieldWarning, color: "#e11d48", bg: "rgba(225, 29, 72, 0.08)" },
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
    void Promise.all(
      metricSeed.map(async (metric) => {
        try {
          const response = await requestBrowserApi("GET", metric.path);
          if (!response.ok) return { ...metric, unavailable: true };
          return { ...metric, value: countFromPayload(await response.json()) };
        } catch {
          return { ...metric, unavailable: true };
        }
      })
    ).then((next) => {
      if (active) setMetrics(next);
    });
    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title="Tổng quan"
        subtitle="CoVie Admin · Bảng điều khiển vận hành & kiểm soát tuân thủ"
      />

      <div className="admin-metric-grid">
        {metrics.map((metric) => {
          const Icon = metric.icon;
          return (
            <Link className="admin-metric-card" href={metric.href} key={metric.href}>
              <div className="admin-metric-card__header">
                <span className="admin-metric-card__label">{metric.label}</span>
                <div
                  className="admin-metric-card__icon"
                  style={{ color: metric.color, background: metric.bg }}
                >
                  <Icon size={22} weight="duotone" />
                </div>
              </div>
              <strong className="admin-metric-card__value">
                {metric.unavailable ? "—" : metric.value ?? "…"}
              </strong>
              <div className="admin-metric-card__footer">
                <small>
                  {metric.unavailable
                    ? "API hiện không khả dụng"
                    : "Dữ liệu trực tiếp từ API quản trị"}
                </small>
                <ArrowRight size={14} weight="bold" className="admin-metric-card__arrow" />
              </div>
            </Link>
          );
        })}
      </div>

      <div className="admin-dashboard-section">
        <h2 className="admin-section-title">Lối tắt vận hành ưu tiên</h2>
        <div className="admin-launchpad-grid">
          <Link href="/admin/reports" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#e11d48", background: "rgba(225, 29, 72, 0.08)" }}>
              <Flag size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Hàng đợi kiểm duyệt</strong>
              <p>Tiếp nhận và xử lý báo cáo vi phạm nội dung từ cư dân CoVie.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/merchants" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#2563eb", background: "rgba(37, 99, 235, 0.08)" }}>
              <Storefront size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Người bán & Nhà cung cấp</strong>
              <p>Quản lý hồ sơ đối tác, kiểm duyệt và điều chỉnh trạng thái hoạt động.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>

          <Link href="/admin/audit" className="admin-launchpad-card">
            <div className="admin-launchpad-card__icon" style={{ color: "#047857", background: "rgba(4, 120, 87, 0.08)" }}>
              <Scroll size={24} weight="duotone" />
            </div>
            <div className="admin-launchpad-card__info">
              <strong>Nhật ký kiểm toán</strong>
              <p>Truy vết mọi can thiệp hệ thống và thao tác quản trị viên.</p>
            </div>
            <ArrowRight size={16} weight="bold" className="admin-launchpad-card__arrow" />
          </Link>
        </div>
      </div>

      <div className="admin-dashboard-grid">
        <AdminSurface>
          <div className="admin-card-heading">
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <ShieldCheck size={20} weight="duotone" style={{ color: "#047857" }} />
              <h2 style={{ margin: 0 }}>Trạng thái vận hành</h2>
            </div>
            <AdminStatusChip label="An toàn theo contract" tone="success" />
          </div>
          <p>
            Dashboard chỉ tổng hợp từ endpoint đã có. Không sử dụng endpoint <code>/admin/stats</code> không tồn tại trong OpenAPI hiện tại và không dựng số liệu giả từ Figma.
          </p>
          <div className="admin-governance-pills" style={{ display: "flex", flexWrap: "wrap", gap: "8px", marginTop: "12px" }}>
            <span className="admin-governance-pill">
              <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
              <span>Chống gửi lặp Idempotency-Key</span>
            </span>
            <span className="admin-governance-pill">
              <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
              <span>Phân quyền Spring Security ADMIN</span>
            </span>
            <span className="admin-governance-pill">
              <CheckCircle size={14} weight="fill" style={{ color: "#047857" }} />
              <span>Rate limit tiền tố /api/v1/admin/**</span>
            </span>
          </div>
        </AdminSurface>

        <AdminSurface>
          <div style={{ display: "flex", alignItems: "center", gap: "8px", marginBottom: "8px" }}>
            <LockKey size={20} weight="duotone" style={{ color: "#2563eb" }} />
            <h2 style={{ margin: 0 }}>Nguyên tắc thao tác</h2>
          </div>
          <p>
            Đơn hàng luôn chỉ đọc. Các thao tác tạm ngưng, kiểm duyệt và cấu hình phải đi qua contract máy chủ, xác nhận và cơ chế chống gửi lặp.
          </p>
          <div className="admin-governance-pills" style={{ display: "flex", flexWrap: "wrap", gap: "8px", marginTop: "12px" }}>
            <span className="admin-governance-pill">
              <LockKey size={14} weight="fill" style={{ color: "#2563eb" }} />
              <span>Đơn hàng Read-only</span>
            </span>
            <span className="admin-governance-pill">
              <LockKey size={14} weight="fill" style={{ color: "#2563eb" }} />
              <span>Bảo vệ quyền riêng tư cư dân</span>
            </span>
          </div>
        </AdminSurface>
      </div>
    </div>
  );
}
