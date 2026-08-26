"use client";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "@/features/catalog/catalog-detail-states.module.css";

export default function CatalogItemError({ reset }: { error: Error & { digest?: string }; reset: () => void }) {
  const router = useRouter();
  return <section className={styles.error}><header><button aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button><h1>Chi tiết sản phẩm</h1></header><div><span className={styles.errorIcon}><CoVieIcon name="feedback-error" /></span><strong>Không thể tải trang</strong><p>Không thể tải dữ liệu lúc này. Hãy thử lại hoặc quay về trang trước.</p><section><button onClick={() => router.back()}>Quay lại</button><button onClick={reset}>Thử lại</button></section></div></section>;
}
