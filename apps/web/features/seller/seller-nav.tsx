"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useRef } from "react";
import { sellerBasePath } from "./seller-context";

const sections = [
  { key: "overview", segment: "", label: "Tổng quan" },
  { key: "orders", segment: "orders", label: "Đơn hàng" },
  { key: "catalog", segment: "catalog", label: "Danh mục" },
  { key: "locations", segment: "locations", label: "Điểm bán" },
  { key: "settings", segment: "settings", label: "Cài đặt" },
] as const;

type SellerSectionKey = (typeof sections)[number]["key"];

function SellerNavIcon({ section }: { section: SellerSectionKey }) {
  if (section === "overview") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4.40039H9C9.33137 4.40039 9.59961 4.66863 9.59961 5V9C9.59961 9.33137 9.33137 9.59961 9 9.59961H5C4.66863 9.59961 4.40039 9.33137 4.40039 9V5C4.40039 4.66863 4.66863 4.40039 5 4.40039Z"/><path d="M15 4.40039H19C19.3314 4.40039 19.5996 4.66863 19.5996 5V6.5C19.5996 6.83137 19.3314 7.09961 19 7.09961H15C14.6686 7.09961 14.4004 6.83137 14.4004 6.5V5C14.4004 4.66863 14.6686 4.40039 15 4.40039Z"/><path d="M15 11.4004H19C19.3314 11.4004 19.5996 11.6686 19.5996 12V19C19.5996 19.3314 19.3314 19.5996 19 19.5996H15C14.6686 19.5996 14.4004 19.3314 14.4004 19V12C14.4004 11.6686 14.6686 11.4004 15 11.4004Z"/><path d="M5 14.4004H9C9.33137 14.4004 9.59961 14.6686 9.59961 15V19C9.59961 19.3314 9.33137 19.5996 9 19.5996H5C4.66863 19.5996 4.40039 19.3314 4.40039 19V15C4.40039 14.6686 4.66863 14.4004 5 14.4004Z"/></svg>;
  }
  if (section === "orders") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4.40039H17C17.6075 4.40039 18.0996 4.89249 18.0996 5.5V18.5C18.0996 19.1075 17.6075 19.5996 17 19.5996H7C6.39249 19.5996 5.90039 19.1075 5.90039 18.5V5.5C5.90039 4.89249 6.39249 4.40039 7 4.40039Z"/><path d="M8.19995 8H15.8M8.19995 12H15.8M8.19995 16H12.4"/></svg>;
  }
  if (section === "catalog") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M19.0996 6.40039V17.5996H4.90039V6.40039H19.0996Z"/><path d="M8 5.5V18.5M4 10H20M12 10V18.5"/></svg>;
  }
  if (section === "locations") {
    return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M20 10C20 15 12 21 12 21C12 21 4 15 4 10C4 7.87827 4.84285 5.84344 6.34315 4.34315C7.84344 2.84285 9.87827 2 12 2C14.1217 2 16.1566 2.84285 17.6569 4.34315C19.1571 5.84344 20 7.87827 20 10Z"/><path d="M12 12.5C13.3807 12.5 14.5 11.3807 14.5 10C14.5 8.61929 13.3807 7.5 12 7.5C10.6193 7.5 9.5 8.61929 9.5 10C9.5 11.3807 10.6193 12.5 12 12.5Z"/></svg>;
  }
  return <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 9.90039C13.1598 9.90039 14.0996 10.8402 14.0996 12C14.0996 13.1598 13.1598 14.0996 12 14.0996C10.8402 14.0996 9.90039 13.1598 9.90039 12C9.90039 10.8402 10.8402 9.90039 12 9.90039Z"/><path d="M13.3843 4.30038C13.7018 4.73609 14.1476 5.06237 14.6626 5.23398C15.124 5.44217 15.6364 5.51227 16.1372 5.4332C16.4169 5.38899 16.6849 5.29808 16.9331 5.16855L18.8042 6.91464C18.6881 7.14915 18.6072 7.40128 18.5659 7.66269C18.4873 8.16033 18.5571 8.66935 18.7622 9.12851C18.933 9.64736 19.2614 10.0962 19.6997 10.4156V13.316C19.2459 13.5482 18.867 13.9099 18.6147 14.3609C18.5397 14.4952 18.4995 14.6466 18.4995 14.8004V14.8219C18.3432 15.2403 18.2966 15.6935 18.3667 16.1373C18.4094 16.4077 18.4951 16.6675 18.6177 16.9088L16.9087 18.6178C16.6674 18.4951 16.4076 18.4095 16.1372 18.3668C15.6365 18.2877 15.1239 18.357 14.6626 18.565C14.1475 18.7366 13.7019 19.0638 13.3843 19.4996H10.4155C10.0977 19.0636 9.65171 18.7365 9.13623 18.565C8.67512 18.3573 8.16296 18.2878 7.6626 18.3668C7.39195 18.4096 7.13164 18.4949 6.89014 18.6178L5.18115 16.9088C5.30388 16.6674 5.39034 16.4078 5.43311 16.1373C5.51234 15.6355 5.44208 15.1219 5.23291 14.6598C5.06097 14.1458 4.7354 13.7004 4.30029 13.3834V10.4156C4.73625 10.0979 5.06236 9.65168 5.23389 9.13632C5.44179 8.67512 5.51213 8.16319 5.43311 7.66269C5.3903 7.39189 5.30409 7.13184 5.18115 6.89023L6.89014 5.18124C7.13175 5.30418 7.3918 5.39039 7.6626 5.4332C8.16309 5.51222 8.67503 5.44188 9.13623 5.23398C9.65159 5.06245 10.0978 4.73634 10.4155 4.30038H13.3843Z"/></svg>;
}

function sectionIsActive(key: SellerSectionKey, pathname: string, base: string) {
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

export function SellerNavLinks({ merchantId, scrollActiveIntoView = false }: { merchantId: string; scrollActiveIntoView?: boolean }) {
  const base = sellerBasePath(merchantId);
  const pathname = usePathname() ?? base;
  const activeLinkRef = useRef<HTMLAnchorElement | null>(null);

  useEffect(() => {
    if (!scrollActiveIntoView) return;
    activeLinkRef.current?.scrollIntoView({ block: "nearest", inline: "nearest" });
  }, [pathname, scrollActiveIntoView]);

  return (
    <nav className="seller-nav-links" aria-label="Điều hướng Seller Center">
      {sections.map((section) => {
        const href = section.segment ? `${base}/${section.segment}` : base;
        const active = sectionIsActive(section.key, pathname, base);
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
