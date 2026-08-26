"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { sellerBasePath } from "./seller-context";

const sections = [
  { segment: "", label: "Tổng quan" },
  { segment: "orders", label: "Đơn hàng" },
  { segment: "catalog", label: "Catalog" },
  { segment: "needs", label: "Nhu cầu" },
  { segment: "inventory", label: "Tồn kho" },
  { segment: "locations", label: "Điểm bán" },
  { segment: "settings", label: "Cài đặt" },
] as const;

export function SellerNavLinks({ merchantId }: { merchantId: string }) {
  const pathname = usePathname();
  const base = sellerBasePath(merchantId);

  return (
    <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
      {sections.map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = section.segment
          ? pathname === href || pathname.startsWith(`${href}/`)
          : pathname === base;

        return (
          <Link key={section.segment || "dashboard"} href={href} aria-current={active ? "page" : undefined}>
            {section.label}
          </Link>
        );
      })}
    </nav>
  );
}
