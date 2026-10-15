"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import {
  Gear,
  CreditCard,
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
  { key: "billing", segment: "billing", label: "Thuê bao" },
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
    case "billing":
      return <CreditCard size={22} weight="duotone" aria-hidden="true" />;
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

export function SellerNavLinks({
  merchantId,
  scrollActiveIntoView = false,
  includeAds = false,
  collapsed = false,
}: {
  merchantId: string;
  scrollActiveIntoView?: boolean;
  includeAds?: boolean;
  collapsed?: boolean;
}) {
  const base = sellerBasePath(merchantId);
  const pathname = usePathname() ?? base;
  const activeLinkRef = useRef<HTMLAnchorElement | null>(null);

  useEffect(() => {
    if (!scrollActiveIntoView) return;
    const activeLink = activeLinkRef.current;
    if (!activeLink || typeof activeLink.scrollIntoView !== "function") return;
    activeLink.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [pathname, scrollActiveIntoView]);

  const items = sections.filter((section) => includeAds || section.key !== "ads");

  if (scrollActiveIntoView) {
    return (
      <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
        {items.map((section) => {
          const href = section.segment ? `${base}/${section.segment}` : base;
          const active = sectionIsActive(section.key, pathname, base, true);
          return (
            <Link key={section.key} href={href} aria-current={active ? "page" : undefined} ref={active ? activeLinkRef : undefined}>
              <span className="seller-nav-indicator" aria-hidden="true" />
              <span className="seller-nav-icon"><SellerNavIcon section={section.key} /></span>
              <span className="seller-nav-label">{section.label}</span>
            </Link>
          );
        })}
      </nav>
    );
  }

  const opItems = items.filter((s) => s.key === "overview" || s.key === "orders" || s.key === "catalog");
  const configItems = items.filter((s) => s.key === "locations" || s.key === "ads" || s.key === "settings" || s.key === "billing");

  return (
    <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
      {!collapsed ? <div className="seller-nav-group-header">VẬN HÀNH</div> : null}
      {opItems.map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = sectionIsActive(section.key, pathname, base, false);
        return (
          <Link
            key={section.key}
            href={href}
            aria-current={active ? "page" : undefined}
            title={collapsed ? section.label : undefined}
          >
            <span className="seller-nav-indicator" aria-hidden="true" />
            <span className="seller-nav-icon"><SellerNavIcon section={section.key} /></span>
            <span className="seller-nav-label">{section.label}</span>
          </Link>
        );
      })}

      {!collapsed ? <div className="seller-nav-group-header">KÊNH & CÀI ĐẶT</div> : null}
      {configItems.map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = sectionIsActive(section.key, pathname, base, false);
        return (
          <Link
            key={section.key}
            href={href}
            aria-current={active ? "page" : undefined}
            title={collapsed ? section.label : undefined}
          >
            <span className="seller-nav-indicator" aria-hidden="true" />
            <span className="seller-nav-icon"><SellerNavIcon section={section.key} /></span>
            <span className="seller-nav-label">{section.label}</span>
            {section.key === "ads" && !collapsed ? (
              <span className="seller-nav-badge" aria-hidden="true" />
            ) : null}
          </Link>
        );
      })}
    </nav>
  );
}
