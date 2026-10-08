"use client";

import Link from "next/link";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";
import { loadAdminSettingDefinitions, loadAdminSettingDetail, updateGlobalAdminSetting } from "./admin-api";
import { SettingsEditor, type AdminSettingDefinition } from "./settings-editor";

export type AdminSettingsSection = "policy" | "orders" | "needs" | "advanced";

const sectionKeys: Record<AdminSettingsSection, Set<string>> = {
  policy: new Set(["moderation.mode", "review.enabled", "reputation.warning_threshold_basis_points"]),
  orders: new Set(["order.confirmation_timeout_minutes", "order.auto_complete_hours", "order.long_issue_timeout_hours", "pickup.no_show_grace_minutes"]),
  needs: new Set(["need_request.max_response_items_per_location"]),
  advanced: new Set(["moderation.hybrid_adverse_lookback_days", "reputation.minimum_eligible_orders_for_warning", "bulk.max_items"]),
};

const titles: Record<AdminSettingsSection, [string, string]> = {
  policy: ["Kiểm duyệt · Đánh giá · Uy tín", "Cấu hình hệ thống"],
  orders: ["Thời hạn đơn hàng", "Cấu hình timeout và vòng đời do máy chủ định nghĩa"],
  needs: ["Ghép nối Cần mua", "Giới hạn phản hồi và matching do máy chủ định nghĩa"],
  advanced: ["Chính sách nâng cao", "Các tham số vận hành bổ sung trong registry hệ thống"],
};

function normalizeSection(value: string | undefined): AdminSettingsSection {
  return value === "orders" || value === "needs" || value === "advanced" ? value : "policy";
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
    } catch (cause) { if (isActive()) setError(cause instanceof Error ? cause.message : "Không thể tải cấu hình hệ thống"); }
    finally { if (isActive()) setLoading(false); }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.resolve().then(() => { if (active) void load(() => active); });
    return () => { active = false; };
  }, [load]);
  function reload() { setLoading(true); setError(undefined); void load(); }
  const filtered = useMemo(() => definitions.filter(definition => sectionKeys[section].has(definition.key)), [definitions, section]);

  return <div className="admin-page-stack">
    <AdminPageHeader title={titles[section][0]} subtitle={titles[section][1]} action={<AdminStatusChip label="If-Match / ETag" tone="info" />} />
    <nav className="admin-settings-tabs" aria-label="Nhóm cài đặt"><Link href="/admin/settings" aria-current={section === "policy" ? "page" : undefined}>Chính sách</Link><Link href="/admin/settings?section=orders" aria-current={section === "orders" ? "page" : undefined}>Đơn hàng</Link><Link href="/admin/settings?section=needs" aria-current={section === "needs" ? "page" : undefined}>Cần mua</Link><Link href="/admin/settings?section=advanced" aria-current={section === "advanced" ? "page" : undefined}>Nâng cao</Link></nav>
    <AdminSurface className="admin-contract-note"><strong>Validation và concurrency</strong><p>Giá trị, phạm vi và version lấy từ API. Mọi thay đổi toàn hệ thống cần xác nhận; PATCH gửi If-Match khi có version và không hard-code range từ Figma.</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải cấu hình…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={reload}>Thử lại</button></AdminSurface> : null}
    {!loading && !error ? <SettingsEditor definitions={filtered} onReload={async () => { reload(); }} onSave={async (key, value) => { const etag = await updateGlobalAdminSetting(key, value, etags[key]); if (etag) setEtags(current => ({ ...current, [key]: etag })); setLoading(true); setError(undefined); await load(); }} /> : null}
  </div>;
}
