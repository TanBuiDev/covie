"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { CoVieIcon } from "@/components/ui/covie-icon";
import {
  Buildings,
  House,
  MapPin,
  MapPinLine,
  Plus,
  Trash,
} from "@phosphor-icons/react";
import { deleteSavedAddress, updateSavedAddress } from "./address-api";
import styles from "./address-list.module.css";

type Address = components["schemas"]["UserAddressResponse"];

function addressTitle(address: Address) {
  return [address.label || "Địa chỉ", address.recipientName].filter(Boolean).join(" · ");
}

function AddressCard({
  address,
  onDelete,
  onSetDefault,
  settingDefault = false,
}: {
  address: Address;
  onDelete: () => void;
  onSetDefault?: () => void;
  settingDefault?: boolean;
}) {
  const label = address.label || "Địa chỉ";
  const secondaryNote = [address.note, !address.defaultAddress ? "Không phải địa chỉ mặc định" : undefined]
    .filter(Boolean)
    .join(" · ");

  const isHome = label.toLowerCase().includes("nhà");
  const isWork = label.toLowerCase().includes("công ty") || label.toLowerCase().includes("văn phòng");

  return (
    <article className={styles.card}>
      <div className={styles.cardTopRow}>
        <div className={styles.cardHeaderLeft}>
          <div className={styles.addressIconWrap} aria-hidden="true">
            {isHome ? (
              <House size={18} weight="duotone" />
            ) : isWork ? (
              <Buildings size={18} weight="duotone" />
            ) : (
              <MapPin size={18} weight="duotone" />
            )}
          </div>
          <div className={styles.titleGroup}>
            <strong>{addressTitle(address)}</strong>
            {address.phone ? <p className={styles.phone}>{address.phone}</p> : null}
          </div>
        </div>
        {address.defaultAddress ? (
          <span className={styles.defaultLabel}>MẶC ĐỊNH</span>
        ) : null}
      </div>

      <div className={styles.addressDetails}>
        <p className={styles.addressText}>{address.addressText || "Chưa có nội dung địa chỉ"}</p>
        {secondaryNote ? <small className={styles.secondaryNote}>{secondaryNote}</small> : null}
      </div>

      {address.id ? (
        <div className={`${styles.cardActions} ${!address.defaultAddress ? styles.nonDefaultActions : ""}`}>
          <Link
            className={styles.editAction}
            href={`/me/addresses/${encodeURIComponent(address.id)}`}
            aria-label={`Chỉnh sửa ${label}`}
          >
            Chỉnh sửa
          </Link>
          <button
            className={styles.deleteAction}
            type="button"
            aria-label={`Xóa ${label}`}
            onClick={onDelete}
          >
            Xóa
          </button>
          {!address.defaultAddress && onSetDefault ? (
            <button
              className={styles.setDefaultAction}
              type="button"
              disabled={settingDefault}
              onClick={onSetDefault}
            >
              {settingDefault ? "Đang đặt…" : "Đặt mặc định"}
            </button>
          ) : null}
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
  const [settingDefaultId, setSettingDefaultId] = useState<string>();
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

  async function handleSetDefault(target: Address) {
    if (
      !target.id ||
      target.defaultAddress ||
      settingDefaultId ||
      !target.label ||
      !target.addressText ||
      target.latitude == null ||
      target.longitude == null
    ) {
      return;
    }
    setSettingDefaultId(target.id);
    setMessage(undefined);
    try {
      await updateSavedAddress(target.id, {
        label: target.label,
        recipientName: target.recipientName ?? undefined,
        phone: target.phone ?? undefined,
        addressText: target.addressText,
        latitude: target.latitude,
        longitude: target.longitude,
        pinSource: target.pinSource ?? "USER_ADJUSTED",
        note: target.note ?? undefined,
        defaultAddress: true,
      });
      router.refresh();
    } catch {
      setMessage("Không thể cập nhật địa chỉ mặc định. Vui lòng thử lại.");
    } finally {
      setSettingDefaultId(undefined);
    }
  }

  const readyContent = (
    <div className={styles.readyContent}>
      {message ? <p className={styles.error} role="alert">{message}</p> : null}
      {addresses.length ? (
        <ul className={styles.list}>
          {addresses.map((address) => (
            <li key={address.id ?? `${address.label}-${address.addressText}`}>
              <AddressCard
                address={address}
                onDelete={() => setDeleteTarget(address)}
                onSetDefault={() => void handleSetDefault(address)}
                settingDefault={settingDefaultId === address.id}
              />
            </li>
          ))}
        </ul>
      ) : (
        <section className={styles.empty}>
          <div className={styles.emptyIconWrap} aria-hidden="true">
            <MapPinLine size={48} weight="duotone" />
          </div>
          <h2>Chưa có địa chỉ đã lưu</h2>
          <p>Thêm địa chỉ để chọn nhanh khi xác nhận đơn hàng.</p>
        </section>
      )}
      <Link className={styles.primaryAction} href="/me/addresses/new">
        <Plus size={16} weight="bold" aria-hidden="true" />
        <span>Thêm địa chỉ mới</span>
      </Link>
      <p className={styles.privacyNote}>Thông tin địa chỉ cá nhân không được lưu để sử dụng ngoại tuyến.</p>
    </div>
  );

  return (
    <section className={styles.screen} aria-label="Địa chỉ đã lưu" data-consumer-focus>
      <header className={styles.appBar}>
        <button type="button" aria-label="Quay lại" onClick={() => router.back()}>
          <CoVieIcon name="back" />
        </button>
        <h1>Địa chỉ của tôi</h1>
      </header>

      <div className={`${styles.content} ${deleteTarget ? styles.deleting : ""}`}>
        <div className={styles.pageHeaderRow}>
          <div>
            <h1 className={styles.desktopTitle}>Địa chỉ của tôi</h1>
            <p className={styles.desktopSubtitle}>Quản lý danh sách địa chỉ nhận hàng và khu vực sinh sống</p>
          </div>
        </div>

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
        ) : (
          <>
            {readyContent}
            {deleteTarget ? (
              <div className={styles.mobileDeleteFlow}>
                <AddressCard address={deleteTarget} onDelete={() => undefined} />
                <section className={styles.warning} role="alert">
                  <CoVieIcon name="feedback-warning" />
                  <div>
                    <h2>Xóa địa chỉ này?</h2>
                    <p>Địa chỉ sẽ bị xóa khỏi tài khoản. Hành động cần xác nhận trước khi thực hiện.</p>
                  </div>
                </section>
                {message ? <p className={styles.error} role="alert">{message}</p> : null}
                <div className={styles.confirmActions}>
                  <button
                    type="button"
                    disabled={pending}
                    onClick={() => { setDeleteTarget(undefined); setMessage(undefined); }}
                  >
                    Giữ địa chỉ
                  </button>
                  <button
                    type="button"
                    disabled={pending}
                    onClick={() => void confirmDelete()}
                  >
                    {pending ? "Đang xóa…" : "Xác nhận xóa"}
                  </button>
                </div>
              </div>
            ) : null}
            <div className={styles.desktopConfirm}>
              <ConfirmDialog
                open={Boolean(deleteTarget)}
                title="Xóa địa chỉ này?"
                description="Địa chỉ sẽ bị xóa khỏi tài khoản. Hành động này không thể hoàn tác."
                cancelLabel="Giữ địa chỉ"
                confirmLabel={pending ? "Đang xóa…" : "Xác nhận xóa"}
                dangerous
                onCancel={() => { if (!pending) { setDeleteTarget(undefined); setMessage(undefined); } }}
                onConfirm={() => void confirmDelete()}
              />
            </div>
          </>
        )}
      </div>
    </section>
  );
}
