"use client";

import Link from "next/link";
import { ErrorState } from "@/components/ui/error-state";
import styles from "@/features/auth/session-list.module.css";

export default function SessionsError({ retry }: { error: Error & { digest?: string }; retry: () => void }) {
  return <main className={styles.page}>
    <ErrorState title="Không thể tải phiên đăng nhập"
      description="Vui lòng thử lại để kiểm tra các thiết bị đang đăng nhập."
      action={<><button className="button-secondary" type="button" onClick={retry}>Thử lại</button><Link href="/me">Quay lại hồ sơ</Link></>}
    />
  </main>;
}
