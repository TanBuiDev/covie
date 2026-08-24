"use client";

export type AdminSettingDefinition = {
  key: string;
  valueType: string;
  defaultValue: string;
  allowedScopes: string[];
};

export function SettingsEditor({ definitions }: { definitions: AdminSettingDefinition[] }) {
  return (
    <section aria-labelledby="settings-title" style={{ display: "grid", gap: 12 }}>
      <header>
        <p style={{ margin: 0, color: "#b45309", fontSize: 12, letterSpacing: ".12em", textTransform: "uppercase" }}>Policy control</p>
        <h1 id="settings-title" style={{ margin: "4px 0 0", fontFamily: "Georgia, serif", fontSize: 34 }}>System settings</h1>
      </header>
      <div style={{ border: "1px solid #d6d3d1", borderRadius: 12, overflow: "hidden", background: "#fffdf8" }}>
        {definitions.map((definition) => <article key={definition.key} style={{ padding: 16, borderBottom: "1px solid #e7e5e4", display: "grid", gridTemplateColumns: "1fr auto", gap: 12 }}>
          <div><strong style={{ fontFamily: "ui-monospace, monospace" }}>{definition.key}</strong><div style={{ color: "#57534e", fontSize: 14, marginTop: 4 }}>{definition.valueType} · {definition.allowedScopes.join(", ")}</div></div>
          <output aria-label={`${definition.key} default`} style={{ alignSelf: "center", padding: "4px 8px", borderRadius: 999, background: "#fef3c7", color: "#92400e" }}>{definition.defaultValue}</output>
        </article>)}
      </div>
    </section>
  );
}
