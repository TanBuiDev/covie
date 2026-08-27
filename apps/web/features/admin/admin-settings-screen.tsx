"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { AdminPageHeader, AdminStatusChip, AdminSurface } from "./admin-ui";
import { loadAdminSettingDefinitions, loadAdminSettingDetail, updateGlobalAdminSetting } from "./admin-api";
import { SettingsEditor, type AdminSettingDefinition } from "./settings-editor";

type Section = "policy" | "orders" | "needs";

function matchesSection(key: string, section: Section) {
  const value = key.toLowerCase();
  if (section === "orders") return value.includes("order") || value.includes("timeout") || value.includes("delivery");
  if (section === "needs") return value.includes("need") || value.includes("match") || value.includes("response");
  return !matchesSection(key, "orders") && !matchesSection(key, "needs");
}

export function AdminSettingsScreen() {
  const searchParams = useSearchParams();
  const requested = searchParams.get("section");
  const section: Section = requested === "orders" || requested === "needs" ? requested : "policy";
  const [definitions, setDefinitions] = useState<AdminSettingDefinition[]>([]);
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState(true);
  const [etags, setEtags] = useState<Record<string, string>>({});

  const load = useCallback(async () => {
    setLoading(true); setError(undefined);
    try {
      const values = await loadAdminSettingDefinitions();
      const details = await Promise.all(values.map(value => loadAdminSettingDetail(value.key)));
      setDefinitions(values.map((value, index) => ({ ...value, effectiveValue: details[index]?.effectiveValue })));
      setEtags(Object.fromEntries(values.map((value, index) => {
        const global = details[index]?.persistedOverrides.find(override => override.scopeType === "GLOBAL");
        return [value.key, global ? `"${global.version}"` : ""];
      })));
    } catch (cause) { setError(cause instanceof Error ? cause.message : "Không thể tải cấu hình hệ thống"); }
    finally { setLoading(false); }
  }, []);

  useEffect(() => { void load(); }, [load]);
  const filtered = useMemo(() => definitions.filter(definition => matchesSection(definition.key, section)), [definitions, section]);
  const titles: Record<Section, [string,string]> = {
    policy: ["Kiểm duyệt · Đánh giá · Uy tín", "Cấu hình hệ thống"],
    orders: ["Thời hạn đơn hàng", "Cấu hình timeout và vòng đời do máy chủ định nghĩa"],
    needs: ["Ghép nối Cần mua", "Giới hạn phản hồi và matching do máy chủ định nghĩa"],
  };

  return <div className="admin-page-stack">
    <AdminPageHeader title={titles[section][0]} subtitle={titles[section][1]} action={<AdminStatusChip label="If-Match / ETag" tone="info" />} />
    <nav className="admin-settings-tabs" aria-label="Nhóm cài đặt"><Link href="/admin/settings" aria-current={section === "policy" ? "page" : undefined}>Chính sách</Link><Link href="/admin/settings?section=orders" aria-current={section === "orders" ? "page" : undefined}>Đơn hàng</Link><Link href="/admin/settings?section=needs" aria-current={section === "needs" ? "page" : undefined}>Cần mua</Link></nav>
    <AdminSurface className="admin-contract-note"><strong>Validation và concurrency</strong><p>Giá trị, phạm vi và version lấy từ API. Mọi thay đổi toàn hệ thống cần xác nhận; PATCH gửi If-Match khi có version và không hard-code range từ Figma.</p></AdminSurface>
    {loading ? <AdminSurface><p className="admin-muted">Đang tải cấu hình…</p></AdminSurface> : null}
    {error ? <AdminSurface><p className="admin-error" role="alert">{error}</p><button className="admin-button admin-button--secondary" onClick={() => void load()}>Thử lại</button></AdminSurface> : null}
    {!loading && !error ? <SettingsEditor definitions={filtered} onReload={load} onSave={async (key, value) => { const etag = await updateGlobalAdminSetting(key, value, etags[key]); if (etag) setEtags(current => ({ ...current, [key]: etag })); await load(); }} /> : null}
  </div>;
}
