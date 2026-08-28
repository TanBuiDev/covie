import type { components } from "@covie/api-client";
import { EmptyState } from "@/components/ui/empty-state";
import styles from "./buyer-reputation.module.css";

type Reputation = components["schemas"]["BuyerReputationSummary"];

export function BuyerReputation({ reputation }: { reputation: Reputation }) {
  if (reputation.completionRateBasisPoints == null) {
    return <EmptyState title="Chưa có dữ liệu uy tín" description="Chỉ số sẽ xuất hiện sau khi bạn có đơn đủ điều kiện đánh giá uy tín." />;
  }
  const rate = Math.min(100, Math.max(0, reputation.completionRateBasisPoints / 100));
  return (
    <section className={styles.surface} aria-labelledby="buyer-reputation-title">
      <header><p className="eyebrow">BUY-009</p><h1 id="buyer-reputation-title">Uy tín người mua</h1></header>
      <article className={styles.summary}>
        <div><span>Tỷ lệ hoàn thành</span><strong>{rate.toLocaleString("vi-VN", { maximumFractionDigits: 2 })}%</strong></div>
        <progress aria-label="Tỷ lệ hoàn thành" max={100} value={rate} />
        <dl>
          <div><dt>Đơn đủ điều kiện</dt><dd>{(reputation.eligibleOrders ?? 0).toLocaleString("vi-VN")}</dd></div>
          <div><dt>Đơn hoàn tất</dt><dd>{(reputation.completedOrders ?? 0).toLocaleString("vi-VN")}</dd></div>
        </dl>
      </article>
      {reputation.warning ? <p className={styles.warning} role="status">Tài khoản đang có cảnh báo uy tín. Hãy hoàn tất đúng cam kết các đơn tiếp theo.</p> : <p className={styles.note}>Uy tín được tính theo chính sách hiện hành của CoVie.</p>}
    </section>
  );
}
