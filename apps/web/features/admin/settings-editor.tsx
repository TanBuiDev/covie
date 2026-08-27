"use client";

import { useEffect, useState } from "react";
import { AdminStatusChip } from "./admin-ui";

export type AdminSettingDefinition = {
  key: string;
  valueType: string;
  defaultValue: unknown;
  allowedScopes: string[];
  effectiveValue?: unknown;
};

export function serializeSettingValue(value: unknown) {
  if (typeof value === "string") return value;
  if (value === undefined) return "";
  try { return JSON.stringify(value); }
  catch { return String(value ?? ""); }
}

function SettingRow({ definition, onSave, onReload }: { definition: AdminSettingDefinition; onSave?: (key: string, value: string) => Promise<void>; onReload?: () => Promise<void> }) {
  const effective = serializeSettingValue(definition.effectiveValue ?? definition.defaultValue);
  const [value, setValue] = useState(effective);
  const [saving, setSaving] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [error, setError] = useState<string>();
  const canEditGlobal = definition.allowedScopes.includes("GLOBAL") && Boolean(onSave);

  useEffect(() => { setValue(effective); }, [effective]);

  async function save() {
    if (!onSave) return;
    setSaving(true); setError(undefined);
    try {
      await onSave(definition.key, value);
      setConfirming(false);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể lưu cấu hình. Dữ liệu có thể đã thay đổi; hãy tải lại trước khi thử lại.");
      setConfirming(false);
    } finally { setSaving(false); }
  }

  return <article className="admin-setting-row">
    <div className="admin-setting-row__description"><strong>{definition.key}</strong><p>Kiểu {definition.valueType}. Phạm vi được máy chủ cho phép: {definition.allowedScopes.join(", ") || "không có"}.</p>{canEditGlobal ? <AdminStatusChip label="Cần xác nhận" tone="warning" /> : <AdminStatusChip label="Chỉ đọc" tone="neutral" />}</div>
    <div className="admin-setting-row__editor"><label className="admin-field"><span>Giá trị</span><input value={value} onChange={event => setValue(event.target.value)} disabled={!canEditGlobal || saving} /><small>Hiệu lực: {effective || "—"} · Mặc định: {serializeSettingValue(definition.defaultValue) || "—"} · Nguồn: API hệ thống</small></label>{error ? <div><p className="admin-error" role="alert">{error}</p>{onReload ? <button className="admin-inline-link admin-link-button" type="button" onClick={() => void onReload()}>Tải lại cấu hình mới nhất</button> : null}</div> : null}<div className="admin-setting-row__actions"><button className="admin-button admin-button--secondary" disabled={!canEditGlobal || saving || value === effective} onClick={() => setConfirming(true)}>Lưu</button></div></div>
    {confirming ? <div className="dialog-backdrop" role="presentation"><div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby={`setting-confirm-${definition.key}`}><h2 id={`setting-confirm-${definition.key}`}>Xác nhận thay đổi cấu hình</h2><p>Giá trị <strong>{definition.key}</strong> sẽ được gửi với ETag/version hiện tại khi có. Máy chủ vẫn là nguồn xác thực cuối cùng cho validation và concurrency.</p><div className="dialog-actions"><button className="admin-button admin-button--secondary" disabled={saving} onClick={() => setConfirming(false)}>Huỷ</button><button className="admin-button admin-button--primary" disabled={saving} onClick={() => void save()}>{saving ? "Đang lưu…" : "Xác nhận lưu"}</button></div></div></div> : null}
  </article>;
}

export function SettingsEditor({ definitions, onSave, onReload }: { definitions: AdminSettingDefinition[]; onSave?: (key: string, value: string) => Promise<void>; onReload?: () => Promise<void> }) {
  if (!definitions.length) return <div className="admin-surface"><p className="admin-muted">Không có cấu hình phù hợp section hiện tại hoặc API chưa trả định nghĩa.</p></div>;
  return <div className="admin-settings-list">{definitions.map(definition => <SettingRow key={definition.key} definition={definition} onSave={onSave} onReload={onReload} />)}</div>;
}
