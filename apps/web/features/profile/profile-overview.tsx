import Link from "next/link";
import type { components } from "@covie/api-client";
import { LogoutButton } from "./logout-button";
import styles from "./profile-overview.module.css";

type Me = components["schemas"]["MeResponse"];

const accountItems = [
  { href: "/orders", title: "Đơn hàng của tôi", copy: "Xem lịch sử và theo dõi trạng thái các đơn hàng." },
  { href: "/me/reputation", title: "Uy tín của tôi", copy: "Xem tỷ lệ hoàn thành giao dịch do hệ thống ghi nhận." },
  { href: "/me/addresses", title: "Địa chỉ đã lưu", copy: "Quản lý địa chỉ giao hàng và khu vực sử dụng dịch vụ." },
  { href: "/me/security/sessions", title: "Bảo mật & phiên đăng nhập", copy: "Kiểm tra và thu hồi các thiết bị đang đăng nhập." },
] as const;

export function ProfileOverview({ user, notificationsAvailable = false }: { user: Me; notificationsAvailable?: boolean }) {
  const initial = (user.displayName ?? user.email ?? "C").trim().charAt(0).toUpperCase();
  return (
    <section className={styles.screen} aria-labelledby="profile-title">
      <header className={styles.header}>
        <div className={styles.avatar} aria-hidden="true">{initial}</div>
        <div className={styles.identity}>
          <p className={styles.eyebrow}>Tài khoản CoVie</p>
          <h1 id="profile-title">{user.displayName ?? "Tài khoản của tôi"}</h1>
          {user.email ? <p className={styles.email}>{user.email}</p> : null}
        </div>
      </header>

      <nav className={styles.links} aria-label="Quản lý tài khoản">
        {notificationsAvailable ? (
          <Link className={styles.card} href="/me/notifications">
            <span><strong>Thông báo</strong><small>Theo dõi cập nhật từ đơn hàng, nhu cầu và tài khoản.</small></span>
            <span className={styles.chevron} aria-hidden="true">›</span>
          </Link>
        ) : null}
        {accountItems.map((item) => (
          <Link className={styles.card} href={item.href} key={item.href}>
            <span><strong>{item.title}</strong><small>{item.copy}</small></span>
            <span className={styles.chevron} aria-hidden="true">›</span>
          </Link>
        ))}
        <Link className={`${styles.card} ${styles.seller}`} href="/seller">
          <span><strong>Seller Center</strong><small>Chuyển sang khu vực dành cho người bán.</small></span>
          <span className={styles.chevron} aria-hidden="true">›</span>
        </Link>
      </nav>

      <div className={styles.logout}><LogoutButton /></div>
    </section>
  );
}
