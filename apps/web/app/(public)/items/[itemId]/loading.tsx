import styles from "@/features/catalog/catalog-detail-states.module.css";
import { CoVieIcon } from "@/components/ui/covie-icon";

export default function CatalogItemLoading() {
  return <div className={styles.loading} aria-label="Đang tải chi tiết sản phẩm"><header><span><CoVieIcon name="back" /></span><strong>Chi tiết sản phẩm</strong></header>{[1,2].map(block => <div className={styles.skeleton} key={block}><i /><section><b /><b /><b /></section><footer><b /><b /></footer></div>)}</div>;
}
