"use client";

import Link from "next/link";
import { useState } from "react";
import {
  Bell,
  CaretDown,
  MagnifyingGlass,
  MapPin,
  ShoppingCart,
} from "@phosphor-icons/react";
import { useCartCount } from "@/features/cart/use-cart-count";
import { LocationChooser } from "@/features/location/location-chooser";
import { useBrowseLocation } from "@/features/location/use-browse-location";

export function AppHeader() {
  const { location } = useBrowseLocation();
  const [locationOpen, setLocationOpen] = useState(false);
  const cartCount = useCartCount();

  return (
    <>
      <header className="app-header">
        <Link className="app-brand" href="/" aria-label="CoVie - Trang chủ">
          CoVie
        </Link>

        <button
          className="app-header__location"
          type="button"
          onClick={() => setLocationOpen(true)}
          aria-label="Chọn khu vực cư dân"
        >
          <MapPin size={15} weight="duotone" className="app-header__location-icon" aria-hidden="true" />
          <span className="app-header__location-text">{location?.label ?? "Chọn toà nhà"}</span>
          <CaretDown size={12} weight="bold" aria-hidden="true" />
        </button>

        <nav className="app-header__actions" aria-label="Tác vụ nhanh">
          <Link className="app-header__icon-btn" href="/search" aria-label="Tìm kiếm">
            <MagnifyingGlass size={20} weight="bold" aria-hidden="true" />
          </Link>
          <Link className="app-header__icon-btn" href="/me/notifications" aria-label="Thông báo">
            <Bell size={20} weight="duotone" aria-hidden="true" />
          </Link>
          <Link className="app-header__icon-btn app-header__cart" href="/cart" aria-label="Mở giỏ hàng">
            <ShoppingCart size={20} weight="duotone" aria-hidden="true" />
            {cartCount > 0 && (
              <span className="app-header__cart-badge" aria-label={`${cartCount} món trong giỏ hàng`}>
                {cartCount > 99 ? "99+" : cartCount}
              </span>
            )}
          </Link>
        </nav>
      </header>

      {locationOpen && (
        <LocationChooser
          onComplete={() => setLocationOpen(false)}
          onCancel={() => setLocationOpen(false)}
        />
      )}
    </>
  );
}
