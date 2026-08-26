"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { sellerBasePath } from "./seller-context";

const sections = [
  { key: "overview", segment: "", label: "Tổng quan" },
  { key: "orders", segment: "orders", label: "Đơn hàng" },
  { key: "catalog", segment: "catalog", label: "Danh mục" },
  { key: "locations", segment: "locations", label: "Điểm bán" },
  { key: "settings", segment: "settings", label: "Cài đặt" },
] as const;

function sectionIsActive(key: (typeof sections)[number]["key"], pathname: string, base: string) {
  if (key === "overview") {
    return pathname === base
      || pathname.startsWith(`${base}/needs`)
      || pathname.startsWith(`${base}/reputation`)
      || pathname.startsWith(`${base}/notices`);
  }
  if (key === "catalog") {
    return pathname.startsWith(`${base}/catalog`) || pathname.startsWith(`${base}/inventory`);
  }
  const section = sections.find((item) => item.key === key);
  const href = section?.segment ? `${base}/${section.segment}` : base;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function SellerNavLinks({ merchantId }: { merchantId: string }) {
  const pathname = usePathname();
  const base = sellerBasePath(merchantId);

  return (
    <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
      {sections.map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = sectionIsActive(section.key, pathname, base);

        return (
          <Link key={section.key} href={href} aria-current={active ? "page" : undefined}>
            {section.label}
          </Link>
        );
      })}
    </nav>
  );
}
