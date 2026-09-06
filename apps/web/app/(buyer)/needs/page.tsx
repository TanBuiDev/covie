import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { NeedList } from "@/features/needs/need-list";
import styles from "./needs-mobile.module.css";

export default function NeedsPage() {
  return (
    <main className={`${styles.surface} needs-surface`}>
      <header className={styles.appBar}>
        <Link className={styles.back} href="/" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Cần mua</h1>
      </header>
      <header className={styles.desktopHeader}>
        <h1>Cần mua</h1>
      </header>
      <div className={styles.body}><NeedList /></div>
    </main>
  );
}
