import styles from "@/features/merchant/merchant-profile-states.module.css";

export default function MerchantProfileLoading() {
  return (
    <main className={styles.loading} aria-busy="true">
      <header>
        <span aria-hidden="true">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none">
            <path d="M15 18 9 12l6-6" stroke="#475569" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        </span>
        <strong>Cửa hàng</strong>
      </header>
      <section aria-label="Đang tải thông tin cửa hàng">
        <span className={styles.avatarSkeleton} data-skeleton="avatar" aria-hidden="true" />
        <span className={styles.textSkeletons} aria-hidden="true">
          <i data-skeleton="text" />
          <i data-skeleton="text" />
          <i data-skeleton="text" />
        </span>
        <span className={styles.actionSkeletons} aria-hidden="true">
          <i data-skeleton="action" />
          <i data-skeleton="action" />
        </span>
      </section>
    </main>
  );
}
