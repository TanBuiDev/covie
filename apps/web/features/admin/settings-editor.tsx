"use client";

import { useState } from "react";

export type AdminSettingDefinition = {
  key: string;
  valueType: string;
  defaultValue: string;
  allowedScopes: string[];
  effectiveValue?: unknown;
};

export function SettingsEditor({ definitions, onSave }: { definitions: AdminSettingDefinition[]; onSave?: (key: string, value: string) => Promise<void> }) {
  return (
    <section aria-labelledby="settings-title" style={{ display: "grid", gap: 12 }}>
      <header>
        <p style={{ margin: 0, color: "#b45309", fontSize: 12, letterSpacing: ".12em", textTransform: "uppercase" }}>Policy control</p>
        <h1 id="settings-title" style={{ margin: "4px 0 0", fontFamily: "Georgia, serif", fontSize: 34 }}>System settings</h1>
      </header>
      <div style={{ border: "1px solid #d6d3d1", borderRadius: 12, overflow: "hidden", background: "#fffdf8" }}>
        {definitions.map((definition) => <SettingRow key={definition.key} definition={definition} onSave={onSave} />)}
      </div>
    </section>
  );
}

function SettingRow({ definition, onSave }: { definition: AdminSettingDefinition; onSave?: (key: string, value: string) => Promise<void> }) {
  const [value, setValue] = useState(String(definition.effectiveValue ?? definition.defaultValue));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string>();
  const canEditGlobal = definition.allowedScopes.includes("GLOBAL") && onSave;
  async function save() {
    if (!onSave) return;
    setSaving(true); setError(undefined);
    try { await onSave(definition.key, value); }
    catch (cause) { setError(cause instanceof Error ? cause.message : "Unable to save setting"); }
    finally { setSaving(false); }
  }
  return <article style={{ padding: 16, borderBottom: "1px solid #e7e5e4", display: "grid", gridTemplateColumns: "1fr auto", gap: 12 }}>
    <div><strong style={{ fontFamily: "ui-monospace, monospace" }}>{definition.key}</strong><div style={{ color: "#57534e", fontSize: 14, marginTop: 4 }}>{definition.valueType} · {definition.allowedScopes.join(", ")}</div>{error && <p role="alert" style={{ color: "#b91c1c", margin: "8px 0 0" }}>{error}</p>}</div>
    {canEditGlobal ? <form onSubmit={(event) => { event.preventDefault(); void save(); }} style={{ display: "flex", gap: 8, alignSelf: "center" }}><input aria-label={`${definition.key} value`} value={value} onChange={(event) => setValue(event.target.value)} /><button type="submit" disabled={saving}>{saving ? "Saving…" : "Save global"}</button></form> : <output aria-label={`${definition.key} default`} style={{ alignSelf: "center", padding: "4px 8px", borderRadius: 999, background: "#fef3c7", color: "#92400e" }}>{definition.defaultValue}</output>}
  </article>;
}
