"use client";

import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useState, useRef, type ReactNode } from "react";
import {
  House,
  ClockCounterClockwise,
  Package,
  PlusCircle,
  Megaphone,
  CreditCard,
  Users,
  Storefront,
  MapPin,
  Tag,
  ShieldWarning,
  MapTrifold,
  Folders,
  Gear,
  MagnifyingGlass,
  ArrowSquareOut,
  CaretLeft,
  CaretRight,
  SignOut,
  ShieldCheck,
  Storefront as MarketIcon,
} from "@phosphor-icons/react/dist/ssr";
import { logout } from "@/features/auth/auth-api";
import styles from "./admin-shell.module.css";

export type NavItem = {
  href: string;
  label: string;
  icon: typeof House;
  badge?: string;
  badgeType?: "warning" | "danger" | "info";
};

export type NavSection = {
  id: string;
  title: string;
  items: NavItem[];
};

export const adminSections: NavSection[] = [
  {
    id: "overview",
    title: "Tổng quan & Dữ liệu",
    items: [
      { href: "/admin", label: "Tổng quan sàn", icon: House },
      { href: "/admin/audit", label: "Nhật ký kiểm toán", icon: ClockCounterClockwise },
    ],
  },
  {
    id: "commerce",
    title: "Vận hành thương mại",
    items: [
      { href: "/admin/orders", label: "Đơn hàng toàn sàn", icon: Package, badge: "12", badgeType: "warning" },
      { href: "/admin/needs", label: "Nhu cầu cư dân", icon: PlusCircle },
      { href: "/admin/ads", label: "Chiến dịch quảng cáo", icon: Megaphone, badge: "3", badgeType: "info" },
      { href: "/admin/billing", label: "Thuê bao Seller", icon: CreditCard },
    ],
  },
  {
    id: "entities",
    title: "Quản lý thực thể & An ninh",
    items: [
      { href: "/admin/users", label: "Người dùng & Tài khoản", icon: Users },
      { href: "/admin/merchants", label: "Người bán & Gian hàng", icon: Storefront },
      { href: "/admin/shops", label: "Cửa hàng & Điểm bán", icon: MapPin },
      { href: "/admin/listings", label: "Nội dung đăng bán", icon: Tag },
      { href: "/admin/reports", label: "Hàng đợi báo cáo vi phạm", icon: ShieldWarning, badge: "5", badgeType: "danger" },
    ],
  },
  {
    id: "system",
    title: "Cấu hình hệ thống",
    items: [
      { href: "/admin/zones", label: "Khu vực & Phân vùng", icon: MapTrifold },
      { href: "/admin/categories", label: "Danh mục ngành hàng", icon: Folders },
      { href: "/admin/settings", label: "Cài đặt hệ thống", icon: Gear },
    ],
  },
];

function isActive(pathname: string, href: string) {
  if (href === "/admin") return pathname === href;
  return pathname === href || pathname.startsWith(`${href}/`);
}

export type AdminShellProps = {
  children: ReactNode;
  user?: {
    displayName?: string | null;
    email?: string | null;
  };
};

export function AdminShell({ children, user }: AdminShellProps) {
  const pathname = usePathname();
  const router = useRouter();
  const [isCollapsed, setIsCollapsed] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const logoutInProgress = useRef(false);

  const displayName = user?.displayName?.trim() || "Admin CoVie";
  const email = user?.email?.trim() || "admin-01@demo.covie.invalid";
  const initials = displayName
    .split(/\s+/)
    .map((w) => w[0])
    .filter(Boolean)
    .slice(0, 2)
    .join("")
    .toUpperCase() || "AD";

  const handleLogout = async () => {
    if (logoutInProgress.current) return;
    logoutInProgress.current = true;
    setIsLoggingOut(true);
    try {
      await logout((path) => router.replace(path));
    } catch {
      logoutInProgress.current = false;
      setIsLoggingOut(false);
    }
  };

  const query = searchQuery.toLowerCase().trim();
  const filteredSections = adminSections
    .map((section) => ({
      ...section,
      items: section.items.filter(
        (item) => item.label.toLowerCase().includes(query) || section.title.toLowerCase().includes(query)
      ),
    }))
    .filter((section) => section.items.length > 0);

  return (
    <div
      className={`workspace-shell workspace-shell--admin ${styles.shell} ${
        isCollapsed ? styles.shellCollapsed : styles.shellExpanded
      }`}
    >
      <aside
        className={`admin-sidebar ${styles.sidebar} ${
          isCollapsed ? styles.sidebarCollapsed : styles.sidebarExpanded
        }`}
        aria-label="Điều hướng quản trị"
      >
        {/* BRAND HEADER */}
        <div className={`${styles.brandHeader} ${isCollapsed ? styles.brandHeaderCollapsed : ""}`}>
          {isCollapsed ? (
            <div className={styles.brandRowCollapsed}>
              <Link className={styles.logoIcon} href="/admin" title="Bảng điều khiển CoVie Admin">
                <ShieldCheck size={22} weight="fill" />
              </Link>
              <button
                type="button"
                className={styles.collapseToggleCollapsed}
                onClick={() => setIsCollapsed(false)}
                title="Mở rộng thanh điều hướng"
                aria-label="Mở rộng menu"
              >
                <CaretRight size={14} weight="bold" />
              </button>
            </div>
          ) : (
            <div className={styles.brandRow}>
              <Link className={styles.brandLink} href="/admin" title="Bảng điều khiển CoVie Admin">
                <div className={styles.logoIcon}>
                  <ShieldCheck size={22} weight="fill" />
                </div>
                <div className={styles.brandMeta}>
                  <span className={styles.brandTitle}>CoVie Admin</span>
                  <span className={styles.brandSubtitle}>
                    <span className={styles.liveDot} aria-hidden="true" />
                    Hệ thống vận hành
                  </span>
                </div>
              </Link>
              <button
                type="button"
                className={styles.collapseToggle}
                onClick={() => setIsCollapsed(true)}
                title="Thu gọn thanh điều hướng"
                aria-label="Thu gọn menu"
              >
                <CaretLeft size={14} weight="bold" />
              </button>
            </div>
          )}

          {!isCollapsed && (
            <>
              <div className={styles.quickActions}>
                <Link
                  href="/"
                  target="_blank"
                  rel="noreferrer"
                  className={styles.storefrontLink}
                  title="Mở giao diện Chợ CoVie công khai"
                >
                  <MarketIcon size={14} weight="bold" />
                  <span>Xem Chợ CoVie</span>
                  <ArrowSquareOut size={12} weight="bold" />
                </Link>
              </div>

              <div className={styles.searchBox}>
                <MagnifyingGlass size={14} className={styles.searchIcon} />
                <input
                  type="text"
                  placeholder="Tìm nhanh trang..."
                  className={styles.searchInput}
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  aria-label="Tìm nhanh trang quản trị"
                />
              </div>
            </>
          )}
        </div>

        {/* SCROLLABLE NAV ITEMS */}
        <nav className={`admin-nav ${styles.navScroll}`} aria-label="Menu chính">
          {filteredSections.map((section) => (
            <div key={section.id} className={styles.navSection}>
              {!isCollapsed && <div className={styles.sectionHeader}>{section.title}</div>}
              {section.items.map((item) => {
                const active = isActive(pathname, item.href);
                const IconComponent = item.icon;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`admin-nav__link ${styles.navLink} ${active ? styles.navLinkActive : ""} ${
                      isCollapsed ? styles.collapsedItem : ""
                    }`}
                    aria-current={active ? "page" : undefined}
                    title={isCollapsed ? item.label : undefined}
                  >
                    <span className={styles.linkIndicator} aria-hidden="true" />
                    <IconComponent
                      size={18}
                      weight={active ? "fill" : "regular"}
                      className={styles.navIcon}
                      aria-hidden="true"
                    />
                    {!isCollapsed && (
                      <>
                        <span className={styles.linkLabel}>{item.label}</span>
                        {item.badge && (
                          <span
                            className={`${styles.badge} ${
                              item.badgeType === "warning"
                                ? styles.badgeWarning
                                : item.badgeType === "danger"
                                ? styles.badgeDanger
                                : styles.badgeInfo
                            }`}
                          >
                            {item.badge}
                          </span>
                        )}
                      </>
                    )}
                    {isCollapsed && item.badge && (
                      <span
                        className={`${styles.collapsedDot} ${
                          item.badgeType === "warning"
                            ? styles.collapsedDotWarning
                            : item.badgeType === "danger"
                            ? styles.collapsedDotDanger
                            : styles.collapsedDotInfo
                        }`}
                        title={`${item.label}: ${item.badge}`}
                        aria-hidden="true"
                      />
                    )}
                  </Link>
                );
              })}
            </div>
          ))}
        </nav>

        {/* USER PROFILE & FOOTER ACTIONS */}
        <div className={isCollapsed ? styles.collapsedFooter : styles.footer}>
          {!isCollapsed ? (
            <>
              <div className={styles.userCard}>
                <div className={styles.userAvatar} aria-hidden="true">
                  {initials}
                </div>
                <div className={styles.userInfo}>
                  <div className={styles.userNameRow}>
                    <span className={styles.userName} title={displayName}>
                      {displayName}
                    </span>
                    <span className={styles.roleChip}>SUPER ADMIN</span>
                  </div>
                  <span className={styles.userEmail} title={email}>
                    {email}
                  </span>
                </div>
              </div>

              <div className={styles.footerActions}>
                <Link href="/" className={styles.footerBtn} title="Quay lại chợ CoVie">
                  <MarketIcon size={14} />
                  <span>Về Chợ</span>
                </Link>
                <button
                  type="button"
                  className={`${styles.footerBtn} ${styles.logoutBtn}`}
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                  title="Đăng xuất khỏi hệ thống quản trị"
                >
                  <SignOut size={14} />
                  <span>{isLoggingOut ? "Đang thoát…" : "Đăng xuất"}</span>
                </button>
              </div>
            </>
          ) : (
            <>
              <div className={styles.collapsedUserAvatar} title={`${displayName} (${email})`}>
                {initials}
              </div>
              <Link href="/" className={styles.collapsedIconBtn} title="Về Chợ CoVie">
                <MarketIcon size={16} />
              </Link>
              <button
                type="button"
                className={`${styles.collapsedIconBtn} ${styles.collapsedIconBtnDanger}`}
                onClick={handleLogout}
                disabled={isLoggingOut}
                title="Đăng xuất"
                aria-label="Đăng xuất"
              >
                <SignOut size={16} />
              </button>
            </>
          )}
        </div>
      </aside>

      <main className="workspace-main admin-main">{children}</main>
    </div>
  );
}
