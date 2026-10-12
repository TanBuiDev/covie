"use client";

import { useSyncExternalStore, type ReactNode } from "react";
import Link from "next/link";
import {
  ArrowLeft,
  CaretLeft,
  CaretRight,
  Storefront,
} from "@phosphor-icons/react/dist/ssr";
import { SellerNavLinks } from "@/features/seller/seller-nav";
import { SellerPageTitle } from "@/features/seller/seller-page-title";
import { sellerBasePath } from "@/features/seller/seller-context";

let sidebarFallback = false;
function readSidebar() {
  try { return localStorage.getItem("covie_seller_sidebar_collapsed") === "true"; } catch { return sidebarFallback; }
}
function subscribeSidebar(listener: () => void) {
  window.addEventListener("storage", listener);
  window.addEventListener("covie-sidebar-change", listener);
  return () => {
    window.removeEventListener("storage", listener);
    window.removeEventListener("covie-sidebar-change", listener);
  };
}

function statusLabel(status?: string) {
  switch (status) {
    case "ACTIVE": return "đang hoạt động";
    case "PAUSED": return "tạm dừng";
    case "SUSPENDED": return "bị hạn chế";
    case "DELETED": return "đã đóng";
    default: return "đang hoạt động";
  }
}

export function SellerShell({
  merchantId,
  merchantStatus,
  merchantName,
  logoUrl,
  coverUrl,
  switcher,
  children,
}: {
  merchantId: string;
  merchantStatus?: "ACTIVE" | "PAUSED" | "SUSPENDED" | "DELETED";
  merchantName?: string;
  logoUrl?: string | null;
  coverUrl?: string | null;
  switcher?: ReactNode;
  children: ReactNode;
}) {
  const base = sellerBasePath(merchantId);
  const restricted = merchantStatus && merchantStatus !== "ACTIVE";
  const collapsed = useSyncExternalStore(subscribeSidebar, readSidebar, () => false);

  function toggleCollapsed() {
    sidebarFallback = !collapsed;
    try { localStorage.setItem("covie_seller_sidebar_collapsed", String(sidebarFallback)); } catch { /* storage may be unavailable */ }
    window.dispatchEvent(new Event("covie-sidebar-change"));
  }

  return (
    <div className={`seller-shell ${collapsed ? "seller-shell--collapsed" : "seller-shell--expanded"}`}>
      <header className="seller-mobile-header" role="banner">
        <div className="seller-mobile-header-bar">
          <Link href="/" className="seller-mobile-back-btn" aria-label="Quay lại Chợ CoVie">
            <ArrowLeft size={16} weight="bold" aria-hidden="true" />
            <span>Chợ CoVie</span>
          </Link>
          <Link className="button-link" href={`${base}/ads`}>Quảng cáo</Link>
        </div>
        <SellerPageTitle merchantId={merchantId} />
        {switcher ? <div className="seller-mobile-switcher">{switcher}</div> : null}
      </header>

      <div className="seller-mobile-tabs">
        <SellerNavLinks merchantId={merchantId} scrollActiveIntoView />
      </div>

      <aside
        className={`seller-desktop-navigation ${collapsed ? "seller-desktop-navigation--collapsed" : "seller-desktop-navigation--expanded"}`}
        aria-label="Menu quản lý người bán"
      >
        <div className={`seller-desktop-header ${collapsed ? "seller-desktop-header--collapsed" : ""}`}>
          {collapsed ? (
            <div className="seller-brand-row-collapsed">
              <Link className="seller-desktop-brand-icon" href={base} title="TRUNG TÂM BÁN HÀNG">
                <Storefront size={22} weight="duotone" aria-hidden="true" />
              </Link>
              <button
                type="button"
                className="seller-collapse-btn seller-collapse-btn--collapsed"
                onClick={toggleCollapsed}
                aria-label="Mở rộng thanh điều hướng"
                title="Mở rộng thanh điều hướng"
              >
                <CaretRight size={14} weight="bold" />
              </button>
            </div>
          ) : (
            <div className="seller-brand-row">
              <Link className="seller-desktop-brand" href={base} title="TRUNG TÂM BÁN HÀNG">
                <div className="seller-desktop-brand-icon" aria-hidden="true">
                  <Storefront size={22} weight="duotone" />
                </div>
                <div className="seller-desktop-brand-text">
                  <span className="seller-desktop-brand-title">CoVie Seller</span>
                  <span className="seller-desktop-brand-subtitle">TRUNG TÂM BÁN HÀNG</span>
                </div>
              </Link>
              <button
                type="button"
                className="seller-collapse-btn"
                onClick={toggleCollapsed}
                aria-label="Thu gọn thanh điều hướng"
                title="Thu gọn thanh điều hướng"
              >
                <CaretLeft size={14} weight="bold" />
              </button>
            </div>
          )}
        </div>

        <div
          className="seller-desktop-store-card"
          style={coverUrl && !collapsed ? {
            backgroundImage: `linear-gradient(rgba(248, 250, 252, 0.88), rgba(248, 250, 252, 0.96)), url(${coverUrl})`,
            backgroundSize: "cover",
            backgroundPosition: "center",
          } : undefined}
        >
          <div className="seller-store-identity" title={merchantName ?? "Cửa hàng của tôi"}>
            <div className="seller-store-avatar" aria-hidden="true">
              {logoUrl ? (
                // eslint-disable-next-line @next/next/no-img-element
                <img
                  src={logoUrl}
                  alt={merchantName ?? "Logo"}
                  style={{ width: "100%", height: "100%", objectFit: "cover", borderRadius: "7px" }}
                />
              ) : (
                <Storefront size={16} weight="fill" />
              )}
              <span className={`seller-status-dot seller-status-dot--${merchantStatus?.toLowerCase() ?? "active"}`} />
            </div>
            {!collapsed && (
              <div className="seller-store-info">
                <strong className="seller-store-name">{merchantName ?? "Cửa hàng của tôi"}</strong>
                <span className="seller-store-status-badge">
                  <span className={`seller-pulse-dot seller-pulse-dot--${merchantStatus?.toLowerCase() ?? "active"}`} />
                  <span>{statusLabel(merchantStatus)}</span>
                </span>
              </div>
            )}
          </div>
          {switcher ? <div className="seller-desktop-switcher">{switcher}</div> : null}
        </div>

        <div className="seller-desktop-nav-body">
          <SellerNavLinks merchantId={merchantId} includeAds collapsed={collapsed} />
        </div>

        <div className="seller-desktop-footer">
          <Link href="/" className="seller-desktop-back-btn" title="Về Chợ CoVie">
            <ArrowLeft size={16} weight="bold" aria-hidden="true" />
            {!collapsed && <span>Về Chợ CoVie</span>}
          </Link>
          {!collapsed && (
            <div className="seller-desktop-footer-meta">
              <span className="seller-live-indicator">
                <span className="seller-live-dot" />
                <span>Nội khu cư dân 24/7</span>
              </span>
              <p className="seller-desktop-role">Người bán / Nhà cung cấp</p>
            </div>
          )}
        </div>
      </aside>

      <main className="seller-shell__main" id="seller-main-content">
        {restricted ? (
          <section className="warning-banner seller-restriction-banner" role="status" aria-label="Trạng thái cửa hàng">
            <strong>Cửa hàng hiện {statusLabel(merchantStatus)}.</strong>
            <p>Một số dữ liệu hoặc thao tác có thể bị giới hạn. CoVie chỉ hiển thị những thao tác hiện còn khả dụng.</p>
          </section>
        ) : null}
        {children}
      </main>
    </div>
  );
}
