import { SessionList } from "@/features/auth/session-list";
import styles from "@/features/auth/session-list.module.css";
import { getActiveSessions } from "@/features/profile/profile-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function SessionsPage() {
  await requireUser();
  const sessions = await getActiveSessions();
  return (
    <main className={styles.page}>
      <header className={styles.pageHeader}>
        <h1>Bảo mật</h1>
        <p>Quản lý các thiết bị đang đăng nhập vào tài khoản CoVie của bạn.</p>
      </header>
      <SessionList sessions={sessions} />
    </main>
  );
}
