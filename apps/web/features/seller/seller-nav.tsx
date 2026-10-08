"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import {
  Gear,
  MapPinLine,
  Megaphone,
  Package,
  Receipt,
  SquaresFour,
} from "@phosphor-icons/react";
import { sellerBasePath } from "./seller-context";

const sections = [
  { key: "overview", segment: "", label: "Tổng quan" },
  { key: "orders", segment: "orders", label: "Đơn hàng" },
  { key: "catalog", segment: "catalog", label: "Danh mục" },
  { key: "locations", segment: "locations", label: "Điểm bán" },
  { key: "ads", segment: "ads", label: "Quảng cáo" },
  { key: "settings", segment: "settings", label: "Cài đặt" },
] as const;

type SellerSectionKey = (typeof sections)[number]["key"];

function SellerNavIcon({ section }: { section: SellerSectionKey }) {
  switch (section) {
    case "overview":
      return <SquaresFour size={22} weight="duotone" aria-hidden="true" />;
    case "orders":
      return <Receipt size={22} weight="duotone" aria-hidden="true" />;
    case "catalog":
      return <Package size={22} weight="duotone" aria-hidden="true" />;
    case "locations":
      return <MapPinLine size={22} weight="duotone" aria-hidden="true" />;
    case "ads":
      return <Megaphone size={22} weight="duotone" aria-hidden="true" />;
    case "settings":
      return <Gear size={22} weight="duotone" aria-hidden="true" />;
  }
}

function sectionIsActive(key: SellerSectionKey, pathname: string, base: string, mobileNavigation: boolean) {
  const isTrustOrNotice = pathname.startsWith(`${base}/reputation`) || pathname.startsWith(`${base}/notices`);
  if (key === "overview") {
    return pathname === base
      || pathname.startsWith(`${base}/needs`)
      || (mobileNavigation && isTrustOrNotice);
  }
  if (key === "settings" && !mobileNavigation && isTrustOrNotice) return true;
  if (key === "catalog") {
    return pathname.startsWith(`${base}/catalog`) || pathname.startsWith(`${base}/inventory`);
  }
  const section = sections.find((item) => item.key === key);
  const href = section?.segment ? `${base}/${section.segment}` : base;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export function SellerNavLinks({ merchantId, scrollActiveIntoView = false, includeAds = false }: { merchantId: string; scrollActiveIntoView?: boolean; includeAds?: boolean }) {
  const base = sellerBasePath(merchantId);
  const pathname = usePathname() ?? base;
  const activeLinkRef = useRef<HTMLAnchorElement | null>(null);

  useEffect(() => {
    if (!scrollActiveIntoView) return;
    const activeLink = activeLinkRef.current;
    if (!activeLink || typeof activeLink.scrollIntoView !== "function") return;
    activeLink.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [pathname, scrollActiveIntoView]);

  return (
    <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
      {sections.filter(section => includeAds || section.key !== "ads").map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = sectionIsActive(section.key, pathname, base, scrollActiveIntoView);
        return (
          <Link key={section.key} href={href} aria-current={active ? "page" : undefined} ref={active && scrollActiveIntoView ? activeLinkRef : undefined}>
            <span className="seller-nav-indicator" aria-hidden="true" />
            <span className="seller-nav-icon"><SellerNavIcon section={section.key} /></span>
            <span className="seller-nav-label">{section.label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
