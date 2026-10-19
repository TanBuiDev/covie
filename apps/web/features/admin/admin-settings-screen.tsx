"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Clock,
  CreditCard,
  HandHeart,
  LockKey,
  ShieldCheck,
  SlidersHorizontal,
} from "@phosphor-icons/react";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";
import { loadAdminSettingDefinitions, loadAdminSettingDetail, updateGlobalAdminSetting } from "./admin-api";
import { SettingsEditor, type AdminSettingDefinition } from "./settings-editor";

export type AdminSettingsSection = "policy" | "orders" | "needs" | "advanced" | "billing";

const sectionKeys: Record<AdminSettingsSection, Set<string>> = {
  policy: new Set(["moderation.mode", "review.enabled", "reputation.warning_threshold_basis_points"]),
  orders: new Set(["order.confirmation_timeout_minutes", "order.auto_complete_hours", "order.long_issue_timeout_hours", "pickup.no_show_grace_minutes"]),
  needs: new Set(["need_request.max_response_items_per_location"]),
  advanced: new Set(["moderation.hybrid_adverse_lookback_days", "reputation.minimum_eligible_orders_for_warning", "bulk.max_items"]),
  billing: new Set([
    "billing.enforcement_enabled", "billing.seller_registration_enabled",
    "billing.reminders_enabled", "billing.checkout_hourly_limit",
    "billing.finance_roles_enabled"
  ]),
};

const titles: Record<AdminSettingsSection, [string, string]> = {
  policy: ["Kiểm duyệt · Đánh giá · Uy tín", "Cấu hình chính sách hệ thống"],
  orders: ["Thời hạn đơn hàng", "Cấu hình timeout và vòng đời do máy chủ định nghĩa"],
  needs: ["Ghép nối Cần mua", "Giới hạn phản hồi và matching do máy chủ định nghĩa"],
  advanced: ["Chính sách nâng cao", "Các tham số vận hành bổ sung trong registry hệ thống"],
  billing: ["Cấu hình Seller Billing", "Cờ bật/tắt an toàn, nhắc gia hạn và giới hạn thanh toán thuê bao"],
};

function normalizeSection(value: string | undefined): AdminSettingsSection {
  return value === "orders" || value === "needs" || value === "advanced" || value === "billing"
    ? value : "policy";
}

export function formatSettingEtag(version: number): string {
  return `"v${version}"`;
}

export function AdminSettingsScreen({ section: requestedSection }: { section?: string }) {
  const section = normalizeSection(requestedSection);
  const [definitions, setDefinitions] = useState<AdminSettingDefinition[]>([]);
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [etags, setEtags] = useState<Record<string, string>>({});

  const load = useCallback(async (isActive: () => boolean = () => true) => {
    try {
      const values = await loadAdminSettingDefinitions();
      const details = await Promise.all(values.map(value => loadAdminSettingDetail(value.key)));
      if (!isActive()) return;
      setDefinitions(values.map((value, index) => ({ ...value, effectiveValue: details[index]?.effectiveValue })));
      setEtags(Object.fromEntries(values.map((value, index) => {
        const global = details[index]?.persistedOverrides.find(override => override.scopeType === "GLOBAL");
        return [value.key, global ? formatSettingEtag(global.version) : ""];
      })));
    } catch (cause) {
      if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải cấu hình hệ thống");
    } finally {
      if (isActive()) setLoading(false);
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(() => active); });
    return () => { active = false; };
  }, [load]);

  function reload() {
    setLoading(true);
    setError(undefined);
    void load();
  }

  const filtered = useMemo(() => definitions.filter(definition => sectionKeys[section].has(definition.key)), [definitions, section]);

  return (
    <div className="admin-page-stack">
      <AdminPageHeader
        title={titles[section][0]}
        subtitle={titles[section][1]}
        action={<AdminStatusChip label="If-Match / ETag" tone="info" />}
      />

      <nav className="admin-settings-tabs" aria-label="Nhóm cài đặt">
        <Link
          href="/admin/settings"
          aria-current={section === "policy" ? "page" : undefined}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <ShieldCheck size={16} weight="duotone" />
          <span>Chính sách</span>
        </Link>
        <Link
          href="/admin/settings?section=orders"
          aria-current={section === "orders" ? "page" : undefined}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <Clock size={16} weight="duotone" />
          <span>Đơn hàng</span>
        </Link>
        <Link
          href="/admin/settings?section=needs"
          aria-current={section === "needs" ? "page" : undefined}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <HandHeart size={16} weight="duotone" />
          <span>Cần mua</span>
        </Link>
        <Link
          href="/admin/settings?section=billing"
          aria-current={section === "billing" ? "page" : undefined}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <CreditCard size={16} weight="duotone" />
          <span>Seller Billing</span>
        </Link>
        <Link
          href="/admin/settings?section=advanced"
          aria-current={section === "advanced" ? "page" : undefined}
          style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}
        >
          <SlidersHorizontal size={16} weight="duotone" />
          <span>Nâng cao</span>
        </Link>
      </nav>

      <AdminSurface className="admin-contract-note">
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <LockKey size={18} weight="duotone" style={{ color: "#2563eb" }} />
          <strong>Validation và concurrency</strong>
        </div>
        <p>
          Giá trị, phạm vi và version lấy từ API. Mọi thay đổi toàn hệ thống cần xác nhận; PATCH gửi If-Match khi có version và không hard-code range từ Figma.
        </p>
        <div className="admin-governance-pills" style={{ marginTop: "4px" }}>
          <span className="admin-governance-pill">
            <ShieldCheck size={14} weight="fill" style={{ color: "#047857" }} />
            <span>If-Match Optimistic Concurrency Control</span>
          </span>
          <span className="admin-governance-pill">
            <ShieldCheck size={14} weight="fill" style={{ color: "#047857" }} />
            <span>Xác thực schema định nghĩa máy chủ</span>
          </span>
        </div>
      </AdminSurface>

      {section === "billing" ? (
        <AdminSurface className="admin-contract-note">
          <h2>Cảnh báo triển khai thu phí</h2>
          <p>Chỉ bật kiểm soát quyền bán khi đã bảo vệ cửa hàng cũ, nghiệm thu payOS, xác minh bảo mật GitHub và hoàn tất đợt pilot.</p>
          <p>Chính sách thu phí trên trang này khác với cấu hình payOS phía backend. Bật enforcement không tự bật tạo link thanh toán; payOS vẫn cần phê duyệt phát hành riêng.</p>
          <p>Chỉ bật phân quyền tài chính sau khi ít nhất một Admin APPROVER và OPERATOR đã được cấp quyền đúng cách.</p>
          <Link href="/admin/billing">Quay lại quản lý bảng giá và giao dịch Seller</Link>
        </AdminSurface>
      ) : null}

      {loading ? (
        <AdminSurface>
          <p className="admin-muted">Đang tải cấu hình…</p>
        </AdminSurface>
      ) : null}

      {error ? (
        <AdminSurface>
          <p className="admin-error" role="alert">{error}</p>
          <button className="admin-button admin-button--secondary" onClick={reload}>
            Thử lại
          </button>
        </AdminSurface>
      ) : null}

      {!loading && !error ? (
        <SettingsEditor
          definitions={filtered}
          onReload={async () => { reload(); }}
          onSave={async (key, value) => {
            const etag = await updateGlobalAdminSetting(key, value, etags[key]);
            if (etag) setEtags(current => ({ ...current, [key]: etag }));
            setLoading(true);
            setError(undefined);
            await load();
          }}
        />
      ) : null}
    </div>
  );
}
