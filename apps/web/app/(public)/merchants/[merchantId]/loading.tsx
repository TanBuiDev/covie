import styles from "@/features/merchant/merchant-profile-states.module.css";

export default function MerchantProfileLoading() {
  return <main className={styles.loading} aria-busy="true"><header><span aria-hidden="true">‹</span><strong>Cửa hàng</strong></header><div>{Array.from({ length: 5 }, (_, index) => <i key={index} />)}</div></main>;
}
