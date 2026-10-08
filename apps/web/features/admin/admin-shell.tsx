"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import type { ReactNode } from "react";
import { Megaphone } from "@phosphor-icons/react";

type NavItem = {
  href: string;
  label: string;
  icon: "home" | "user" | "store" | "pin" | "grid" | "package" | "plus" | "list" | "settings" | "ads";
  separatorBefore?: boolean;
};

const navItems: NavItem[] = [
  { href: "/admin", label: "Tổng quan", icon: "home" },
  { href: "/admin/users", label: "Người dùng", icon: "user" },
  { href: "/admin/merchants", label: "Người bán / Nhà cung cấp", icon: "store" },
  { href: "/admin/shops", label: "Cửa hàng & Điểm bán", icon: "pin" },
  { href: "/admin/listings", label: "Nội dung đăng bán", icon: "grid" },
  { href: "/admin/orders", label: "Đơn hàng", icon: "package", separatorBefore: true },
  { href: "/admin/needs", label: "Cần mua", icon: "plus" },
  { href: "/admin/reports", label: "Báo cáo", icon: "list" },
  { href: "/admin/ads", label: "Quảng cáo", icon: "ads" },
  { href: "/admin/zones", label: "Khu vực", icon: "pin", separatorBefore: true },
  { href: "/admin/categories", label: "Danh mục", icon: "grid" },
  { href: "/admin/settings", label: "Cài đặt", icon: "settings", separatorBefore: true },
  { href: "/admin/audit", label: "Nhật ký", icon: "list" },
];

function isActive(pathname: string, href: string) {
  if (href === "/admin") return pathname === href;
  return pathname === href || pathname.startsWith(`${href}/`);
}

function NavIcon({ kind }: { kind: NavItem["icon"] }) {
  if (kind === "ads") return <Megaphone className="admin-nav__icon" size={20} aria-hidden="true" />;
  const common = { fill: "none", stroke: "currentColor", strokeWidth: 1.6, strokeLinecap: "round" as const, strokeLinejoin: "round" as const };
  return (
    <svg aria-hidden="true" className="admin-nav__icon" viewBox="0 0 20 20" {...common}>
      {kind === "home" && <><path d="M3.5 9.2 10 3.7l6.5 5.5"/><path d="M5.2 8.5v7.2h9.6V8.5"/></>}
      {kind === "user" && <><circle cx="10" cy="6.3" r="2.5"/><path d="M4.8 16c.7-3 2.4-4.5 5.2-4.5s4.5 1.5 5.2 4.5"/></>}
      {kind === "store" && <><path d="M4 7h12l-1-3H5L4 7Z"/><path d="M5 8.5v7h10v-7M8 15.5v-4h4v4"/></>}
      {kind === "pin" && <><path d="M10 17s5-5.3 5-9a5 5 0 1 0-10 0c0 3.7 5 9 5 9Z"/><circle cx="10" cy="8" r="1.6"/></>}
      {kind === "grid" && <><rect x="3.5" y="3.5" width="5" height="5" rx="1"/><rect x="11.5" y="3.5" width="5" height="5" rx="1"/><rect x="3.5" y="11.5" width="5" height="5" rx="1"/><rect x="11.5" y="11.5" width="5" height="5" rx="1"/></>}
      {kind === "package" && <><path d="m4 6 6-3 6 3-6 3-6-3Z"/><path d="M4 6v8l6 3 6-3V6M10 9v8"/></>}
      {kind === "plus" && <><path d="M10 4v12M4 10h12"/></>}
      {kind === "list" && <><path d="M7 5h9M7 10h9M7 15h9"/><circle cx="4" cy="5" r=".7" fill="currentColor" stroke="none"/><circle cx="4" cy="10" r=".7" fill="currentColor" stroke="none"/><circle cx="4" cy="15" r=".7" fill="currentColor" stroke="none"/></>}
      {kind === "settings" && <><circle cx="10" cy="10" r="2.4"/><path d="M10 3.2v1.5M10 15.3v1.5M3.2 10h1.5M15.3 10h1.5M5.2 5.2l1.1 1.1M13.7 13.7l1.1 1.1M14.8 5.2l-1.1 1.1M6.3 13.7l-1.1 1.1"/></>}
    </svg>
  );
}

export function AdminShell({ children }: { children: ReactNode }) {
  const pathname = usePathname();
  return (
    <div className="workspace-shell workspace-shell--admin">
      <aside className="admin-sidebar" aria-label="Điều hướng quản trị">
        <div className="admin-sidebar__brand">
          <strong>CoVie Admin</strong>
          <span>Bảng điều khiển vận hành</span>
        </div>
        <nav className="admin-nav">
          {navItems.map((item) => {
            const active = isActive(pathname, item.href);
            return (
              <div className={item.separatorBefore ? "admin-nav__group-start" : undefined} key={item.href}>
                <Link className="admin-nav__link" href={item.href} aria-current={active ? "page" : undefined}>
                  <span className="admin-nav__indicator" aria-hidden="true" />
                  <NavIcon kind={item.icon} />
                  <span>{item.label}</span>
                </Link>
              </div>
            );
          })}
        </nav>
      </aside>
      <main className="workspace-main admin-main">{children}</main>
    </div>
  );
}
