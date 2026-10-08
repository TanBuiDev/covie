"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useSearchParams } from "next/navigation";
import { Suspense, useEffect, useState } from "react";
import {
  Bell,
  CaretDown,
  Compass,
  Handshake,
  House,
  MagnifyingGlass,
  MapPin,
  Package,
  ShoppingCart,
  Storefront,
  User,
  X,
} from "@phosphor-icons/react";
import { useCartCount } from "@/features/cart/use-cart-count";
import { LocationChooser } from "@/features/location/location-chooser";
import { useBrowseLocation } from "@/features/location/use-browse-location";

type NavItem = {
  href: string;
  label: string;
  icon: typeof House;
};

const NAV_ITEMS: ReadonlyArray<NavItem> = [
  { href: "/", label: "Trang chủ", icon: House },
  { href: "/search", label: "Khám phá", icon: Compass },
  { href: "/needs", label: "Cần mua", icon: Handshake },
  { href: "/orders", label: "Đơn", icon: Package },
];

function isActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  if (href === "/search") return pathname.startsWith("/search") || pathname.startsWith("/items/") || pathname.startsWith("/merchants/");
  return pathname.startsWith(href);
}

const SEARCH_FILTER_KEYS = ["type", "categoryId", "sort", "listingKind", "radiusMeters", "openNow", "minPrice", "maxPrice", "pickup", "canServe", "shipping"] as const;

function HeaderSearchForm({ query = "", filters = [] }: { query?: string; filters?: Array<[string, string]> }) {
  const [draftQuery, setDraftQuery] = useState(query);

  useEffect(() => {
    const restoreQuery = () => setDraftQuery(query);
    restoreQuery();
    window.addEventListener("pageshow", restoreQuery);
    window.addEventListener("popstate", restoreQuery);
    return () => {
      window.removeEventListener("pageshow", restoreQuery);
      window.removeEventListener("popstate", restoreQuery);
    };
  }, [query]);

  return (
    <form className="buyer-desktop-search" action="/search" role="search">
      <MagnifyingGlass size={18} weight="bold" className="buyer-desktop-search__icon" aria-hidden="true" />
      <input
        name="q"
        type="search"
        aria-label="Tìm món đồ, dịch vụ"
        placeholder="Tìm món đồ, dịch vụ quanh toà..."
        value={draftQuery}
        onChange={(event) => setDraftQuery(event.target.value)}
      />
      {draftQuery && (
        <button
          type="button"
          className="buyer-desktop-search__clear"
          aria-label="Xóa từ khóa"
          onClick={() => setDraftQuery("")}
        >
          <X size={13} weight="bold" />
        </button>
      )}
      {filters.map(([name, value]) => (
        <input key={name} type="hidden" name={name} value={value} />
      ))}
    </form>
  );
}

function HeaderSearch() {
  const params = useSearchParams();
  const pathname = usePathname();
  const filters: Array<[string, string]> = pathname === "/search" ? SEARCH_FILTER_KEYS.flatMap((name) => {
    const value = params?.get(name);
    return value ? [[name, value] as [string, string]] : [];
  }) : [];
  return <HeaderSearchForm query={pathname === "/search" ? params?.get("q") ?? "" : ""} filters={filters} />;
}

export function BuyerDesktopHeader() {
  const pathname = usePathname() ?? "/";
  const { location } = useBrowseLocation();
  const [locationOpen, setLocationOpen] = useState(false);
  const cartCount = useCartCount();

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
        {/* Left: Brand Logo & Location Pill */}
        <div className="buyer-desktop-header__left">
          <Link className="buyer-desktop-brand" href="/" aria-label="CoVie - Trang chủ">
            <div className="buyer-desktop-brand__lockup">
              <Image
                className="buyer-desktop-brand__mark"
                src="/figma/covie-market-pin.svg"
                alt=""
                width={36}
                height={40}
                priority
                unoptimized
              />
              <div className="buyer-desktop-brand__words">
                <strong>CoVie</strong>
                <small>Chợ cư dân</small>
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
              <div className="buyer-desktop-location__icon" aria-hidden="true">
                <MapPin size={17} weight="duotone" />
              </div>
              <div className="buyer-desktop-location__text">
                <small>{location ? "Khu vực" : "Chọn khu vực"}</small>
                <strong>{location?.label ?? "Toà nhà & Cụm cư dân"}</strong>
              </div>
              <CaretDown
                size={13}
                weight="bold"
                className={`buyer-desktop-location__caret ${locationOpen ? "open" : ""}`}
                aria-hidden="true"
              />
            </button>
            {locationOpen && (
              <div className="buyer-desktop-location-popover" id="buyer-desktop-location-popover">
                <LocationChooser
                  onComplete={() => setLocationOpen(false)}
                  onCancel={() => setLocationOpen(false)}
                />
              </div>
            )}
          </div>
        </div>

        {/* Center: Omni Search Bar */}
        <div className="buyer-desktop-header__center">
          <Suspense fallback={<HeaderSearchForm />}>
            <HeaderSearch />
          </Suspense>
        </div>

        {/* Right: Nav Links, Seller CTA & Action Icons */}
        <div className="buyer-desktop-header__right">
          <nav className="buyer-desktop-nav" aria-label="Điều hướng Buyer">
            {NAV_ITEMS.map((item) => {
              const active = isActive(pathname, item.href);
              const Icon = item.icon;
              return (
                <Link
                  key={item.href}
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                >
                  <Icon size={18} weight={active ? "fill" : "duotone"} aria-hidden="true" />
                  <span>{item.label}</span>
                </Link>
              );
            })}
          </nav>

          <div className="header-divider" aria-hidden="true" />

          <div className="buyer-desktop-actions">
            <Link href="/seller" className="buyer-desktop-seller-btn">
              <Storefront size={16} weight="duotone" aria-hidden="true" />
              <span>Mở quầy / Pass đồ</span>
            </Link>

            <Link href="/me/notifications" className="buyer-desktop-icon-btn" aria-label="Thông báo">
              <Bell size={19} weight="duotone" aria-hidden="true" />
            </Link>

            <Link className="buyer-desktop-icon-btn buyer-desktop-cart" href="/cart" aria-label="Mở giỏ hàng">
              <ShoppingCart size={19} weight="duotone" aria-hidden="true" />
              {cartCount > 0 && (
                <span className="buyer-desktop-cart__badge" aria-label={`${cartCount} món trong giỏ hàng`}>
                  {cartCount > 99 ? "99+" : cartCount}
                </span>
              )}
            </Link>

            <Link href="/me" className="buyer-desktop-icon-btn" aria-label="Tài khoản cá nhân">
              <User size={19} weight="duotone" aria-hidden="true" />
            </Link>
          </div>
        </div>
      </div>
    </header>
  );
}
