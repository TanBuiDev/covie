import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { NeedDetail } from "@/features/needs/need-detail";
import styles from "../needs-mobile.module.css";

export default async function NeedDetailPage({ params }: { params: Promise<{ needId: string }> }) {
  const { needId } = await params;
  return (
    <main className={`${styles.surface} needs-surface`}>
      <header className={styles.appBar}>
        <Link className={styles.back} href="/needs" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
        <h1>Chi tiết Cần mua</h1>
      </header>
      <div className={styles.body}><NeedDetail needId={needId} /></div>
    </main>
  );
}
