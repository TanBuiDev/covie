"use client";
import { useRouter } from "next/navigation";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./cart-list.module.css";

export function CartAppBar() {
  const router = useRouter();
  return <header className={styles.appBar}><button aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button><h1>Giỏ hàng</h1></header>;
}
