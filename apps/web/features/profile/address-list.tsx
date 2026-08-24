"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { deleteSavedAddress } from "./address-api";
import { AddressForm } from "./address-form";

type Address = components["schemas"]["UserAddressResponse"];

export function AddressList({ addresses }: { addresses: Address[] }) {
  const [deleteTarget, setDeleteTarget] = useState<Address>();
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();

  async function confirmDelete() {
    if (!deleteTarget?.id || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await deleteSavedAddress(deleteTarget.id);
      window.location.reload();
    } catch {
      setMessage("Không thể xóa địa chỉ. Vui lòng tải lại và thử lại.");
      setDeleteTarget(undefined);
      setPending(false);
    }
  }

  if (!addresses.length) return <p>Bạn chưa lưu địa chỉ nào.</p>;

  return (
    <section aria-label="Địa chỉ đã lưu">
      {message ? <p role="alert">{message}</p> : null}
      <ul className="management-list">
        {addresses.map((address) => (
          <li key={address.id ?? `${address.label}-${address.addressText}`}>
            <div>
              <strong>{address.label ?? "Địa chỉ"}</strong>{" "}
              {address.defaultAddress ? <span className="status-chip">Mặc định</span> : null}
              <p>{address.addressText}</p>
              {address.recipientName || address.phone ? <small>{[address.recipientName, address.phone].filter(Boolean).join(" · ")}</small> : null}
            </div>
            <div className="action-row">
              <details>
                <summary>Chỉnh sửa</summary>
                <AddressForm address={address} />
              </details>
              <button type="button" className="button-danger" onClick={() => setDeleteTarget(address)}>Xóa</button>
            </div>
          </li>
        ))}
      </ul>
      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Xóa địa chỉ đã lưu?"
        description="Địa chỉ sẽ không còn xuất hiện để chọn cho các lần đặt hàng tiếp theo."
        confirmLabel={pending ? "Đang xóa…" : "Xóa địa chỉ"}
        dangerous
        onCancel={() => { if (!pending) setDeleteTarget(undefined); }}
        onConfirm={() => void confirmDelete()}
      />
    </section>
  );
}
