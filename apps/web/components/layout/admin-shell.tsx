import Link from "next/link";
import type { ReactNode } from "react";

const sections = [
  ["", "Dashboard"],
  ["moderation", "Moderation"],
  ["reports", "Reports"],
  ["users", "Users"],
  ["merchants", "Merchants"],
  ["orders", "Orders"],
  ["settings", "Settings"],
  ["audit", "Audit"],
] as const;

export function AdminShell({ children }: { children: ReactNode }) {
  return (
    <div className="workspace-shell workspace-shell--admin">
      <aside className="workspace-nav">
        <Link className="app-brand" href="/admin">CoVie Admin</Link>
        <nav aria-label="Quản trị CoVie">
          {sections.map(([path, label]) => <Link key={path} href={path ? `/admin/${path}` : "/admin"}>{label}</Link>)}
        </nav>
      </aside>
      <main className="workspace-main">{children}</main>
    </div>
  );
}
