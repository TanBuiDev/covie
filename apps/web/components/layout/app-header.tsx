"use client";

import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { useBrowseLocation } from "@/features/location/use-browse-location";

export function AppHeader() {
  const { location } = useBrowseLocation();
  return (
    <header className="app-header">
      <Link className="app-brand" href="/" aria-label="CoVie - Trang chủ">CoVie</Link>
      <nav className="app-header__actions" aria-label="Tác vụ nhanh">
        <Link href="/search" aria-label="Chọn khu vực khám phá">
          <CoVieIcon name="location" /><span>{location?.label ?? "Chọn vị trí"}</span>
        </Link>
        <Link className="app-header__cart" href="/cart" aria-label="Mở giỏ hàng"><CoVieIcon name="cart" /></Link>
      </nav>
    </header>
  );
}
