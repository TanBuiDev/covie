"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { deleteSavedAddress } from "./address-api";
import styles from "./address-list.module.css";

type Address = components["schemas"]["UserAddressResponse"];

function addressTitle(address: Address) {
  return [address.label || "Địa chỉ", address.recipientName].filter(Boolean).join(" · ");
}

function AddressCard({ address, onDelete }: { address: Address; onDelete: () => void }) {
  const label = address.label || "Địa chỉ";
  const secondaryNote = [address.note, !address.defaultAddress ? "Không phải địa chỉ mặc định" : undefined]
    .filter(Boolean)
    .join(" · ");

  return (
    <article className={styles.card}>
      {address.defaultAddress ? <span className={styles.defaultLabel}>MẶC ĐỊNH</span> : null}
      <strong>{addressTitle(address)}</strong>
      {address.phone ? <p>{address.phone}</p> : null}
      <p>{address.addressText || "Chưa có nội dung địa chỉ"}</p>
      {secondaryNote ? <small>{secondaryNote}</small> : null}
      {address.defaultAddress && address.id ? (
        <div className={styles.cardActions}>
          <Link href={`/me/addresses/${encodeURIComponent(address.id)}`} aria-label={`Chỉnh sửa ${label}`}>Chỉnh sửa</Link>
          <button type="button" aria-label={`Xóa ${label}`} onClick={onDelete}>Xóa</button>
        </div>
      ) : null}
    </article>
  );
}

export function AddressList({
  addresses,
  state = "ready",
  onRetry,
}: {
  addresses: Address[];
  state?: "ready" | "loading" | "error";
  onRetry?: () => void;
}) {
  const router = useRouter();
  const [deleteTarget, setDeleteTarget] = useState<Address>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();

  async function confirmDelete() {
    if (!deleteTarget?.id || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await deleteSavedAddress(deleteTarget.id);
      setDeleteTarget(undefined);
      router.refresh();
    } catch {
      setMessage("Không thể xóa địa chỉ. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  return (
    <section className={styles.screen} aria-label="Địa chỉ đã lưu" data-consumer-focus>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button>
        <h1>Địa chỉ của tôi</h1>
      </header>

      <div className={styles.content}>
        {state === "loading" ? (
          <div className={styles.loading} role="status">
            <span>Đang tải địa chỉ…</span>
            <i /><i />
          </div>
        ) : state === "error" ? (
          <section className={styles.loadError} role="alert">
            <h2>Không thể tải địa chỉ</h2>
            <p>Kết nối có thể đang gián đoạn. Dữ liệu địa chỉ cá nhân chưa được hiển thị.</p>
            <button type="button" onClick={onRetry}>Thử lại</button>
          </section>
        ) : deleteTarget ? (
          <>
            <AddressCard address={deleteTarget} onDelete={() => undefined} />
            <section className={styles.warning} role="alert">
              <span aria-hidden="true">△</span>
              <div><h2>Xóa địa chỉ này?</h2><p>Địa chỉ sẽ bị xóa khỏi tài khoản. Hành động cần xác nhận trước khi thực hiện.</p></div>
            </section>
            {message ? <p className={styles.error} role="alert">{message}</p> : null}
            <div className={styles.confirmActions}>
              <button type="button" disabled={pending} onClick={() => { setDeleteTarget(undefined); setMessage(undefined); }}>Giữ địa chỉ</button>
              <button type="button" disabled={pending} onClick={() => void confirmDelete()}>{pending ? "Đang xóa…" : "Xác nhận xóa"}</button>
            </div>
          </>
        ) : (
          <>
            {message ? <p className={styles.error} role="alert">{message}</p> : null}
            {addresses.length ? (
              <ul className={styles.list}>
                {addresses.map((address) => <li key={address.id ?? `${address.label}-${address.addressText}`}><AddressCard address={address} onDelete={() => setDeleteTarget(address)} /></li>)}
              </ul>
            ) : (
              <section className={styles.empty}>
                <h2>Chưa có địa chỉ đã lưu</h2>
                <p>Thêm địa chỉ để chọn nhanh khi xác nhận đơn hàng.</p>
              </section>
            )}
            <Link className={styles.primaryAction} href="/me/addresses/new">Thêm địa chỉ mới</Link>
            <p className={styles.privacyNote}>Thông tin địa chỉ cá nhân không được lưu để sử dụng ngoại tuyến.</p>
          </>
        )}
      </div>
    </section>
  );
}
