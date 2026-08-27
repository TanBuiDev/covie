import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { NeedCreateForm } from "@/features/needs/need-create-form";
import styles from "../needs-mobile.module.css";

export default function NewNeedPage() {
  return (
    <main className={`${styles.surface} needs-surface needs-surface--form`}>
      <header className={styles.appBar}>
        <Link className={styles.back} href="/needs" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Tạo Cần mua</h1>
      </header>
      <header className={styles.desktopHeader}>
        <h1>Đăng nhu cầu</h1>
        <p>Cho người bán gần bạn biết món đồ hoặc dịch vụ bạn đang tìm.</p>
      </header>
      <div className={`${styles.body} ${styles.formBody}`}><NeedCreateForm /></div>
    </main>
  );
}
