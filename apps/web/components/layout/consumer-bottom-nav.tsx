"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Compass,
  Handshake,
  House,
  Package,
  User,
} from "@phosphor-icons/react";

const items = [
  { href: "/", label: "Trang chủ", icon: House },
  { href: "/search", label: "Khám phá", icon: Compass },
  { href: "/needs", label: "Cần mua", icon: Handshake },
  { href: "/orders", label: "Đơn", icon: Package },
  { href: "/me", label: "Tôi", icon: User },
] as const;

export function ConsumerBottomNav() {
  const pathname = usePathname() ?? "/";
  if (pathname.startsWith("/items/") || pathname.startsWith("/checkout") || pathname.startsWith("/me/addresses")) return null;
  const activeHref = pathname.startsWith("/merchants/") ? "/search" : pathname.startsWith("/cart") ? "/" : undefined;
  return (
    <nav className="consumer-bottom-nav" aria-label="Điều hướng chính">
      {items.map((item) => {
        const active = activeHref ? item.href === activeHref : item.href === "/" ? pathname === "/" : pathname.startsWith(item.href);
        const Icon = item.icon;
        return (
          <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}>
            <Icon size={22} weight={active ? "fill" : "duotone"} aria-hidden="true" />
            <span>{item.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
