"use client";

import Image from "next/image";
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
  if (href === "/") return pathname === "/";
  if (href === "/search") return pathname.startsWith("/search") || pathname.startsWith("/items/") || pathname.startsWith("/merchants/");
  return pathname.startsWith(href);
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
        <Link className="buyer-desktop-brand" href="/" aria-label="CoVie - Trang chủ">
          <div className="buyer-desktop-brand__lockup">
            <Image
              className="buyer-desktop-brand__mark"
              src="/figma/covie-market-pin.svg"
              alt=""
              width={40}
              height={45}
              priority
              unoptimized
            />
            <div className="buyer-desktop-brand__words">
              <strong>CoVie</strong>
              <small>Chợ Việt</small>
            </div>
          </div>
        </Link>

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
