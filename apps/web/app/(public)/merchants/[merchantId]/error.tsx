"use client";

import Link from "next/link";
import { useEffect } from "react";
import styles from "@/features/merchant/merchant-profile-states.module.css";

export default function MerchantProfileError({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => { console.error(error); }, [error]);
  return <main className={styles.error}><header><Link href="/search" aria-label="Quay lại">‹</Link><h1>Cửa hàng</h1></header><div><strong>Chưa thể tải hồ sơ công khai</strong><p>Cửa hàng có thể tạm ngừng hiển thị hoặc kết nối đang gián đoạn.</p><section><Link href="/search">Về khám phá</Link><button onClick={reset}>Thử lại</button></section></div></main>;
}
