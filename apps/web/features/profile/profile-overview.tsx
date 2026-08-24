import Link from "next/link";
import type { components } from "@covie/api-client";
import { LogoutButton } from "./logout-button";

type Me = components["schemas"]["MeResponse"];

export function ProfileOverview({ user, notificationsAvailable = false }: { user: Me; notificationsAvailable?: boolean }) {
  return (
    <section className="profile-overview">
      <header className="surface-header">
        <p className="eyebrow">Tài khoản CoVie</p>
        <h1>{user.displayName ?? "Tài khoản của tôi"}</h1>
        {user.email ? <p>{user.email}</p> : null}
      </header>

      <nav className="account-links" aria-label="Quản lý tài khoản">
        <Link href="/me/addresses">Địa chỉ đã lưu</Link>
        <Link href="/me/security/sessions">Bảo mật và phiên đăng nhập</Link>
        {notificationsAvailable ? <Link href="/me/notifications">Thông báo</Link> : null}
        <Link href="/seller">Seller Center</Link>
      </nav>

      <LogoutButton />
    </section>
  );
}
