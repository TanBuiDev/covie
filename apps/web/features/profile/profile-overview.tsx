"use client";

import Link from "next/link";
import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import type { components } from "@covie/api-client";
import {
  ArrowRight,
  Bell,
  ClockCounterClockwise,
  CaretRight,
  LockKey,
  MapPin,
  Package,
  ShieldCheck,
  Sparkle,
  Star,
  Storefront,
} from "@phosphor-icons/react";
import { LogoutButton } from "./logout-button";
import { ProfileEditForm } from "./profile-edit-form";
import styles from "./profile-overview.module.css";

type Me = components["schemas"]["MeResponse"] & {
  phone?: string | null;
  avatarUrl?: string | null;
  avatarMediaId?: string | null;
};

interface MenuItem {
  href: string;
  title: string;
  copy: string;
  icon: typeof Package;
  colorScheme: "emerald" | "amber" | "blue" | "purple" | "teal";
}

const accountItems: MenuItem[] = [
  {
    href: "/orders",
    title: "Đơn hàng của tôi",
    copy: "Xem lịch sử và theo dõi trạng thái các đơn hàng.",
    icon: Package,
    colorScheme: "emerald",
  },
  {
    href: "/needs",
    title: "Nhu cầu cần mua",
    copy: "Đăng tìm món đồ, dịch vụ và theo dõi báo giá người bán.",
    icon: Sparkle,
    colorScheme: "teal",
  },
  {
    href: "/me/reputation",
    title: "Uy tín của tôi",
    copy: "Xem tỷ lệ hoàn thành giao dịch do hệ thống ghi nhận.",
    icon: Star,
    colorScheme: "amber",
  },
  {
    href: "/me/addresses",
    title: "Địa chỉ đã lưu",
    copy: "Quản lý địa chỉ giao hàng và khu vực sử dụng dịch vụ.",
    icon: MapPin,
    colorScheme: "blue",
  },
  {
    href: "/me/security/sessions",
    title: "Bảo mật & phiên đăng nhập",
    copy: "Kiểm tra và thu hồi các thiết bị đang đăng nhập.",
    icon: LockKey,
    colorScheme: "purple",
  },
];

export function ProfileOverview({
  user,
  notificationsAvailable = false,
  activityAvailable = false,
}: { user: Me; notificationsAvailable?: boolean; activityAvailable?: boolean }) {
  return <ProfileOverviewContent key={user.id ?? user.email ?? "profile"} user={user} notificationsAvailable={notificationsAvailable} activityAvailable={activityAvailable} />;
}

function ProfileOverviewContent({
  user: initialUser,
  notificationsAvailable = false,
  activityAvailable = false,
}: {
  user: Me;
  notificationsAvailable?: boolean;
  activityAvailable?: boolean;
}) {
  const [user, setUser] = useState(initialUser);
  const [editing, setEditing] = useState(false);
  const [saved, setSaved] = useState(false);
  const [brokenAvatar, setBrokenAvatar] = useState(false);
  const editButton = useRef<HTMLButtonElement>(null);
  const restoreFocus = useRef(false);
  useEffect(() => { if (!editing && restoreFocus.current) { restoreFocus.current = false; editButton.current?.focus(); } }, [editing]);
  function closeEditor() { restoreFocus.current = true; setEditing(false); }
  const initial = (user.displayName ?? user.email ?? "C").trim().charAt(0).toUpperCase();

  const allItems: MenuItem[] = notificationsAvailable
    ? [
        {
          href: "/me/notifications",
          title: "Thông báo",
          copy: "Theo dõi cập nhật từ đơn hàng, nhu cầu và tài khoản.",
          icon: Bell,
          colorScheme: "blue",
        },
        ...accountItems,
      ]
    : accountItems;

  return (
    <section className={styles.screen} aria-labelledby="profile-title">
      <div className={styles.dashboardLayout}>
        {/* CỘT TRÁI: Thẻ hồ sơ cá nhân & Thông tin cư dân */}
        <aside className={styles.residentSidebar}>
          <div className={styles.profileCard}>
            <div className={styles.profileHeader}>
              <div className={styles.avatarWrap}>
                {user.avatarUrl && !brokenAvatar ? <Image className={styles.avatarImage} src={user.avatarUrl} alt="Ảnh đại diện của bạn" width={64} height={64} unoptimized onError={() => setBrokenAvatar(true)} /> : <span className={styles.avatarText} aria-hidden="true">{initial}</span>}
                <span className={styles.avatarOnlineDot} />
              </div>
              <div className={styles.identity}>
                <span className={styles.eyebrow}>Tài khoản CoVie</span>
                <h1 id="profile-title" className={styles.userName}>
                  {user.displayName ?? "Tài khoản của tôi"}
                </h1>
                {user.email ? <p className={styles.email}>{user.email}</p> : null}
                {user.phone ? <p className={styles.email}>{user.phone}</p> : null}
              </div>
            </div>

            <button ref={editButton} type="button" className={`button-secondary ${styles.editButton}`} aria-expanded={editing} aria-controls="profile-editor" disabled={editing} onClick={() => { setSaved(false); setEditing(true); }}>Chỉnh sửa hồ sơ</button>
            {saved && <p role="status" className={styles.saved}>Hồ sơ đã được cập nhật.</p>}

            <div className={styles.residentMeta}>
              <div className={styles.metaBadge}>
                <ShieldCheck size={15} weight="fill" className={styles.verifiedIcon} />
                <span>Cư dân đã xác thực</span>
              </div>
              <span className={styles.metaCommunity}>Khu dân cư Vinhomes</span>
            </div>

            <div className={styles.sidebarLogout}>
              <LogoutButton />
            </div>
          </div>
          {editing && <div id="profile-editor"><ProfileEditForm key={user.id ?? "profile"} user={user} onCancel={closeEditor} onSaved={profile => { setUser(profile); setBrokenAvatar(false); setSaved(true); closeEditor(); }} /></div>}
        </aside>

        {/* CỘT PHẢI: Trung tâm điều khiển & Quản lý tính năng */}
        <main className={styles.controlCenter}>
          {/* Banner chuyển sang Kênh người bán */}
          <Link className={styles.sellerBanner} href="/seller">
            <div className={styles.sellerBannerLeft}>
              <div className={styles.sellerIconBox}>
                <Storefront size={26} weight="duotone" />
              </div>
              <div className={styles.sellerBannerInfo}>
                <span className={styles.sellerEyebrow}>Dành cho người bán & kinh doanh</span>
                <strong className={styles.sellerTitle}>Kênh người bán (Seller Center)</strong>
                <p className={styles.sellerDesc}>
                  Đăng bán sản phẩm, cung cấp dịch vụ và quản lý đơn hàng nhanh chóng
                </p>
              </div>
            </div>
            <div className={styles.sellerBannerCta}>
              <span>Mở kênh</span>
              <ArrowRight size={16} weight="bold" />
            </div>
          </Link>

          {/* Lưới danh mục điều hướng */}
          <div className={styles.menuSectionHeader}>
            <h2>Trung tâm tài khoản</h2>
            <p>Quản lý đơn hàng, nhu cầu tìm kiếm và thông tin cá nhân của bạn</p>
          </div>

          <nav className={styles.menuGrid} aria-label="Quản lý tài khoản">
            {activityAvailable && <Link className={`${styles.menuCard} ${styles["menuCard--emerald"]}`} href="/me/activity">
              <div className={styles.menuIconBox}><ClockCounterClockwise size={22} weight="duotone" /></div>
              <div className={styles.menuContent}><strong>Hoạt động của tôi</strong><small>Xem nội dung đang thích, lưu và bình luận của bạn.</small></div>
              <CaretRight size={18} weight="bold" className={styles.menuChevron} aria-hidden="true" />
            </Link>}
            {allItems.map((item) => {
              const IconComponent = item.icon;
              return (
                <Link
                  className={`${styles.menuCard} ${styles[`menuCard--${item.colorScheme}`]}`}
                  href={item.href}
                  key={item.href}
                >
                  <div className={styles.menuIconBox}>
                    <IconComponent size={22} weight="duotone" />
                  </div>
                  <div className={styles.menuContent}>
                    <strong>{item.title}</strong>
                    <small>{item.copy}</small>
                  </div>
                  <CaretRight size={18} weight="bold" className={styles.menuChevron} aria-hidden="true" />
                </Link>
              );
            })}
          </nav>
        </main>
      </div>
    </section>
  );
}
