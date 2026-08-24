import Link from "next/link";
import type { ReactNode } from "react";

const sections = [
  ["", "Dashboard"],
  ["orders", "Orders"],
  ["catalog", "Catalog"],
  ["needs", "Needs"],
  ["inventory", "Inventory"],
  ["locations", "Locations"],
  ["settings", "Settings"],
] as const;

export function SellerShell({
  merchantId,
  merchantName,
  switcher,
  children,
}: {
  merchantId: string;
  merchantName?: string;
  switcher?: ReactNode;
  children: ReactNode;
}) {
  const base = `/seller/${encodeURIComponent(merchantId)}`;
  return (
    <div className="workspace-shell">
      <aside className="workspace-nav">
        <Link className="app-brand" href="/seller">CoVie Seller</Link>
        <p className="workspace-nav__context">{merchantName ?? "Cửa hàng hiện tại"}</p>
        {switcher}
        <nav aria-label="Seller Center">
          {sections.map(([path, label]) => <Link key={path} href={path ? `${base}/${path}` : base}>{label}</Link>)}
        </nav>
      </aside>
      <main className="workspace-main">{children}</main>
    </div>
  );
}
