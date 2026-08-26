"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { CoVieIcon, type CoVieIconName } from "@/components/ui/covie-icon";

const items: ReadonlyArray<{ href: string; label: string; icon: CoVieIconName }> = [
  { href: "/", label: "Trang chủ", icon: "home" },
  { href: "/search", label: "Khám phá", icon: "explore" },
  { href: "/needs", label: "Cần mua", icon: "need" },
  { href: "/orders", label: "Đơn", icon: "orders" },
  { href: "/me", label: "Tôi", icon: "profile" },
];

export function ConsumerBottomNav() {
  const pathname = usePathname() ?? "/";
  if (pathname.startsWith("/items/") || pathname.startsWith("/checkout") || pathname.startsWith("/me/addresses")) return null;
  const activeHref = pathname.startsWith("/merchants/") ? "/search" : pathname.startsWith("/cart") ? "/" : undefined;
  return (
    <nav className="consumer-bottom-nav" aria-label="Điều hướng chính">
      {items.map((item) => {
        const active = activeHref ? item.href === activeHref : item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        return <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}><CoVieIcon name={item.icon} active={active} /><span>{item.label}</span></Link>;
      })}
    </nav>
  );
}
