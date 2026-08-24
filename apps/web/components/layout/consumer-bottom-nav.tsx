"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";

const items = [
  { href: "/", label: "Trang chủ" },
  { href: "/search", label: "Khám phá" },
  { href: "/needs", label: "Cần mua" },
  { href: "/orders", label: "Đơn" },
  { href: "/me", label: "Tôi" },
] as const;

export function ConsumerBottomNav() {
  const pathname = usePathname() ?? "/";
  return (
    <nav className="consumer-bottom-nav" aria-label="Điều hướng chính">
      {items.map((item) => {
        const active = item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        return <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}>{item.label}</Link>;
      })}
    </nav>
  );
}
