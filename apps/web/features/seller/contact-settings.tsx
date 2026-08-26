"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";
import { uploadImage } from "@/features/media/direct-image-upload";
import { createMerchantContact, deleteMerchantContact } from "./contact-api";

type MerchantContact = components["schemas"]["MerchantContactResponse"];
type ContactType = components["schemas"]["MerchantContactRequest"]["contactType"];

function contactTypeCopy(type?: ContactType) {
  switch (type) {
    case "PHONE": return "Điện thoại";
    case "ZALO_LINK": return "Zalo";
    case "ZALO_QR": return "Zalo QR";
    case "MESSENGER_LINK": return "Messenger";
    case "OTHER": return "Khác";
    default: return "Liên hệ";
  }
}

export function MerchantContactSettings({
  merchantId,
  merchantLocationId,
  contacts,
  title = "Kênh liên hệ",
}: {
  merchantId: string;
  merchantLocationId?: string;
  contacts: MerchantContact[];
  title?: string;
}) {
  const router = useRouter();
  const [type, setType] = useState<ContactType>("PHONE");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const [deleteTarget, setDeleteTarget] = useState<MerchantContact>();
  const visibleContacts = useMemo(
    () => contacts.filter((contact) => (merchantLocationId ? contact.merchantLocationId === merchantLocationId : !contact.merchantLocationId)),
    [contacts, merchantLocationId],
  );

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    setPending(true);
    setMessage(undefined);
    try {
      let qrMediaId: string | undefined;
      let value = String(form.get("value") ?? "").trim() || undefined;
      if (type === "ZALO_QR") {
        const file = form.get("qrFile");
        if (!(file instanceof File) || file.size === 0) {
          setMessage("Chọn ảnh QR Zalo trước khi lưu.");
          return;
        }
        const media = await uploadImage(file, "MERCHANT_CONTACT_QR");
        if (!media.mediaId) throw new Error("MERCHANT_CONTACT_QR_MEDIA_ID_MISSING");
        qrMediaId = media.mediaId;
        value = undefined;
      }

      await createMerchantContact(merchantId, {
        merchantLocationId,
        contactType: type,
        value,
        qrMediaId,
        primary: form.get("primary") === "on",
        publicContact: form.get("publicContact") === "on",
      });
      event.currentTarget.reset();
      setType("PHONE");
      router.refresh();
    } catch {
      setMessage("Không thể lưu kênh liên hệ. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  async function remove() {
    if (!deleteTarget?.id || pending) return;
    setPending(true);
    setMessage(undefined);
    try {
      await deleteMerchantContact(merchantId, deleteTarget.id);
      setDeleteTarget(undefined);
      router.refresh();
    } catch {
      setMessage("Không thể xóa kênh liên hệ. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="seller-section" aria-labelledby="seller-contact-heading">
      <div className="seller-section__heading"><h2 id="seller-contact-heading">{title}</h2></div>
      {visibleContacts.length ? (
        <div className="seller-row-list">
          {visibleContacts.map((contact, index) => (
            <div className="seller-row" key={contact.id ?? `${contact.contactType ?? "contact"}-${index}`}>
              <div>
                <strong>{contactTypeCopy(contact.contactType)}</strong>
                {contact.value ? <small>{contact.value}</small> : null}
                {contact.qrMediaId ? <small>Ảnh QR đã được lưu trên media service.</small> : null}
                <small>{[contact.primary ? "Kênh chính" : null, contact.publicContact ? "Hiển thị công khai" : "Nội bộ"].filter(Boolean).join(" · ")}</small>
              </div>
              <div className="seller-row__actions">
                <StatusChip status={contact.status} />
                {contact.id ? <button type="button" className="button-danger" disabled={pending} onClick={() => setDeleteTarget(contact)}>Xóa</button> : null}
              </div>
            </div>
          ))}
        </div>
      ) : (
        <EmptyState title="Chưa có kênh liên hệ" description="Thêm số điện thoại, link nhắn tin hoặc QR Zalo theo contract Merchant Contact." />
      )}

      <form onSubmit={submit} aria-busy={pending}>
        <h2>Thêm kênh liên hệ</h2>
        <label>
          Loại kênh
          <select disabled={pending} value={type} onChange={(event) => setType(event.target.value as ContactType)}>
            <option value="PHONE">Điện thoại</option>
            <option value="ZALO_LINK">Zalo link</option>
            <option value="ZALO_QR">Zalo QR</option>
            <option value="MESSENGER_LINK">Messenger</option>
            <option value="OTHER">Khác</option>
          </select>
        </label>
        {type === "ZALO_QR" ? (
          <label>
            Ảnh QR Zalo
            <input name="qrFile" type="file" accept="image/jpeg,image/png,image/webp" required disabled={pending} />
          </label>
        ) : (
          <label>
            {type === "PHONE" ? "Số điện thoại" : "Giá trị hoặc đường dẫn"}
            <input name="value" type={type === "PHONE" ? "tel" : "text"} required disabled={pending} />
          </label>
        )}
        <label><input name="primary" type="checkbox" disabled={pending} /> Kênh chính</label>
        <label><input name="publicContact" type="checkbox" disabled={pending} defaultChecked /> Hiển thị công khai</label>
        {message ? <p role="alert">{message}</p> : null}
        <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : "Thêm kênh liên hệ"}</button>
      </form>

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Xóa kênh liên hệ?"
        description="Hành động này xóa kênh liên hệ đã chọn khỏi cấu hình cửa hàng."
        confirmLabel={pending ? "Đang xóa…" : "Xóa kênh"}
        dangerous
        onCancel={() => { if (!pending) setDeleteTarget(undefined); }}
        onConfirm={() => { void remove(); }}
      />
    </section>
  );
}
