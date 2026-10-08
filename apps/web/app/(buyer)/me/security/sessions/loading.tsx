import { LoadingState } from "@/components/ui/loading-state";
import styles from "@/features/auth/session-list.module.css";

export default function SessionsLoading() {
  return <main className={styles.page}><LoadingState label="Đang tải phiên đăng nhập…" /></main>;
}
