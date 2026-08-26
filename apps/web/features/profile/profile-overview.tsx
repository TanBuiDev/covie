import Link from "next/link";
import type { components } from "@covie/api-client";
import { LogoutButton } from "./logout-button";

type Me = components["schemas"]["MeResponse"];

const accountItems = [
  { href: "/me/addresses", title: "Địa chỉ đã lưu", copy: "Quản lý địa chỉ giao hàng và khu vực sử dụng dịch vụ." },
  { href: "/me/security/sessions", title: "Bảo mật", copy: "Kiểm tra và thu hồi các phiên đăng nhập đang hoạt động." },
] as const;

export function ProfileOverview({ user, notificationsAvailable = false }: { user: Me; notificationsAvailable?: boolean }) {
  return (
    <section className="profile-overview">
      <header className="profile-overview__header">
        <div className="profile-avatar" aria-hidden="true">{(user.displayName ?? user.email ?? "C").trim().charAt(0).toUpperCase()}</div>
        <div>
          <p className="eyebrow">Tài khoản CoVie</p>
          <h1>{user.displayName ?? "Tài khoản của tôi"}</h1>
          {user.email ? <p>{user.email}</p> : null}
        </div>
      </header>

      <nav className="account-links" aria-label="Quản lý tài khoản">
        {accountItems.map((item) => (
          <Link className="account-link-card" href={item.href} key={item.href}>
            <span><strong>{item.title}</strong><small>{item.copy}</small></span>
            <span aria-hidden="true">›</span>
          </Link>
        ))}
        {notificationsAvailable ? (
          <Link className="account-link-card" href="/me/notifications">
            <span><strong>Thông báo</strong><small>Theo dõi cập nhật từ đơn hàng, nhu cầu và tài khoản.</small></span>
            <span aria-hidden="true">›</span>
          </Link>
        ) : null}
        <Link className="account-link-card" href="/seller">
          <span><strong>Seller Center</strong><small>Chuyển sang khu vực dành cho người bán.</small></span>
          <span aria-hidden="true">›</span>
        </Link>
      </nav>

      <div className="profile-overview__logout"><LogoutButton /></div>
    </section>
  );
}
