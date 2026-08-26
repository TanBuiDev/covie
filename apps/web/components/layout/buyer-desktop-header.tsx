"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { CoVieIcon, type CoVieIconName } from "@/components/ui/covie-icon";
import { LocationChooser } from "@/features/location/location-chooser";
import { useBrowseLocation } from "@/features/location/use-browse-location";

const NAV_ITEMS: ReadonlyArray<{ href: string; label: string; icon: CoVieIconName }> = [
  { href: "/", label: "Trang chủ", icon: "home" },
  { href: "/search", label: "Khám phá", icon: "explore" },
  { href: "/needs", label: "Cần mua", icon: "need" },
  { href: "/orders", label: "Đơn", icon: "orders" },
  { href: "/me", label: "Tôi", icon: "profile" },
];

function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/" || pathname.startsWith("/cart");
  if (href === "/search") return pathname.startsWith("/search") || pathname.startsWith("/items/") || pathname.startsWith("/merchants/");
  return pathname.startsWith(href);
}

function CoVieDesktopBrand() {
  return (
    <span className="buyer-desktop-brand__lockup" aria-hidden="true">
      <svg className="buyer-desktop-brand__mark" viewBox="0 0 48 54" role="presentation">
        <path d="M24 5C12.7 5 3.5 13.9 3.5 25C3.5 37.45 16.1 46.35 24 50C31.9 46.35 44.5 37.45 44.5 25C44.5 13.9 35.3 5 24 5Z" fill="#059669" />
        <path d="M10 24.25C15.65 22.8 20.25 19.1 24 14C27.75 19.1 32.35 22.8 38 24.25L35.2 28.75C30.9 27.3 27.2 24.95 24 21.75C20.8 24.95 17.1 27.3 12.8 28.75L10 24.25Z" fill="white" />
        <path d="M15 29.5H33V37.5C33 38.163 32.7366 38.7989 32.2678 39.2678C31.7989 39.7366 31.163 40 30.5 40H17.5C16.837 40 16.2011 39.7366 15.7322 39.2678C15.2634 38.7989 15 38.163 15 37.5V29.5Z" fill="white" />
        <path d="M21 40V32H27V40H21Z" fill="#059669" />
      </svg>
      <span className="buyer-desktop-brand__words"><strong>CoVie</strong><small>Chợ Việt</small></span>
    </span>
  );
}

export function BuyerDesktopHeader() {
  const pathname = usePathname() ?? "/";
  const { location } = useBrowseLocation();
  const [locationOpen, setLocationOpen] = useState(false);

  useEffect(() => {
    if (!locationOpen) return;
    const close = (event: KeyboardEvent) => {
      if (event.key === "Escape") setLocationOpen(false);
    };
    window.addEventListener("keydown", close);
    return () => window.removeEventListener("keydown", close);
  }, [locationOpen]);

  return (
    <header className="buyer-desktop-header">
      <div className="buyer-desktop-header__inner">
        <Link className="buyer-desktop-brand" href="/" aria-label="CoVie - Trang chủ"><CoVieDesktopBrand /></Link>

        <div className="buyer-desktop-location-wrap">
          <button
            className="buyer-desktop-location"
            type="button"
            aria-expanded={locationOpen}
            aria-controls="buyer-desktop-location-popover"
            onClick={() => setLocationOpen((open) => !open)}
          >
            <CoVieIcon name="location" />
            <span><small>{location ? "Gần bạn" : "Khu vực"}</small><strong>{location?.label ?? "Chọn vị trí"}</strong></span>
          </button>
          {locationOpen && (
            <div className="buyer-desktop-location-popover" id="buyer-desktop-location-popover">
              <LocationChooser />
            </div>
          )}
        </div>

        <form className="buyer-desktop-search" action="/search" role="search">
          <CoVieIcon name="search" />
          <input name="q" type="search" aria-label="Tìm món đồ, dịch vụ" placeholder="Tìm món đồ, dịch vụ..." />
        </form>

        <nav className="buyer-desktop-nav" aria-label="Điều hướng Buyer">
          {NAV_ITEMS.map((item) => {
            const active = isActive(pathname, item.href);
            return (
              <Link key={item.href} href={item.href} aria-current={active ? "page" : undefined}>
                <CoVieIcon name={item.icon} active={active} />
                <span>{item.label}</span>
              </Link>
            );
          })}
        </nav>

        <Link className="buyer-desktop-cart" href="/cart" aria-label="Mở giỏ hàng"><CoVieIcon name="cart" /></Link>
      </div>
    </header>
  );
}
