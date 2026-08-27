"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { uploadImage } from "@/features/media/direct-image-upload";
import { createMerchantContact, deleteMerchantContact } from "./contact-api";

type MerchantContact = components["schemas"]["MerchantContactResponse"];
type MerchantContactInput = components["schemas"]["MerchantContactRequest"];
type ContactType = MerchantContactInput["contactType"];

function contactTypeCopy(type?: ContactType) {
  switch (type) {
    case "PHONE": return "Điện thoại";
    case "ZALO_LINK": return "Zalo link";
    case "ZALO_QR": return "Zalo QR";
    case "MESSENGER_LINK": return "Messenger link";
    case "OTHER": return "Khác";
    default: return "Liên hệ";
  }
}

function isSelectedFile(value: FormDataEntryValue | null): value is File {
  return value !== null && typeof value !== "string" && typeof value.size === "number" && value.size > 0;
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
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const [messageIsError, setMessageIsError] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<MerchantContact>();
  const visibleContacts = useMemo(
    () => contacts.filter((contact) => (merchantLocationId ? contact.merchantLocationId === merchantLocationId : !contact.merchantLocationId)),
    [contacts, merchantLocationId],
  );
  const zaloContacts = visibleContacts.filter((contact) => contact.contactType === "ZALO_LINK");
  const messengerContacts = visibleContacts.filter((contact) => contact.contactType === "MESSENGER_LINK");
  const qrContacts = visibleContacts.filter((contact) => contact.contactType === "ZALO_QR");
  const zaloContact = zaloContacts[0];
  const messengerContact = messengerContacts[0];
  const qrContact = qrContacts[0];

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;

    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const zaloValue = String(form.get("zaloLink") ?? "").trim();
    const messengerValue = String(form.get("messengerLink") ?? "").trim();
    const qrEntry = form.get("qrFile");
    const qrControl = formElement.elements.namedItem("qrFile");
    const qrInputFile = qrControl && "files" in qrControl
      ? (qrControl as HTMLInputElement).files?.[0] ?? null
      : null;
    const qrFile = qrInputFile ?? (isSelectedFile(qrEntry) ? qrEntry : null);
    const requests: MerchantContactInput[] = [];

    if (!zaloContact && zaloValue) {
      requests.push({ merchantLocationId, contactType: "ZALO_LINK", value: zaloValue, publicContact: true });
    }
    if (!messengerContact && messengerValue) {
      requests.push({ merchantLocationId, contactType: "MESSENGER_LINK", value: messengerValue, publicContact: true });
    }

    setPending(true);
    setMessage(undefined);
    setMessageIsError(false);
    try {
      if (!qrContact && qrFile && qrFile.size > 0) {
        const media = await uploadImage(qrFile, "MERCHANT_CONTACT_QR");
        if (!media.mediaId) throw new Error("MERCHANT_CONTACT_QR_MEDIA_ID_MISSING");
        requests.push({ merchantLocationId, contactType: "ZALO_QR", qrMediaId: media.mediaId, publicContact: true });
      }

      if (requests.length === 0) {
        setMessage("Không có kênh mới để lưu. Các kênh đã tồn tại hiện chỉ có thể xem hoặc xóa trong phần quản lý.");
        return;
      }

      for (const request of requests) {
        await createMerchantContact(merchantId, request);
      }
      setMessage("Đã lưu liên hệ.");
      formElement.reset();
      router.refresh();
    } catch {
      setMessageIsError(true);
      setMessage("Không thể lưu đầy đủ liên hệ. Các kênh đã lưu thành công vẫn được giữ; hãy tải lại và kiểm tra trước khi thử lại.");
      router.refresh();
    } finally {
      setPending(false);
    }
  }

  async function remove() {
    if (!deleteTarget?.id || pending) return;
    setPending(true);
    setMessage(undefined);
    setMessageIsError(false);
    try {
      await deleteMerchantContact(merchantId, deleteTarget.id);
      setDeleteTarget(undefined);
      router.refresh();
    } catch {
      setMessageIsError(true);
      setMessage("Không thể xóa kênh liên hệ. Vui lòng thử lại.");
    } finally {
      setPending(false);
    }
  }

  const hasDuplicateFrozenType = zaloContacts.length > 1 || messengerContacts.length > 1 || qrContacts.length > 1;

  return (
    <section className="seller-contact-settings" aria-label={title}>
      <form className="seller-contact-form" onSubmit={submit} aria-busy={pending}>
        <label>
          Zalo link
          <input
            name="zaloLink"
            type="url"
            inputMode="url"
            defaultValue={zaloContact?.value ?? ""}
            readOnly={Boolean(zaloContact)}
            disabled={pending}
            placeholder={zaloContact ? undefined : "https://zalo.me/..."}
          />
          <small>{zaloContact ? "Liên kết đã lưu hiện chỉ có thể xem." : "Liên kết liên hệ công khai"}</small>
        </label>

        <label>
          Messenger link
          <input
            name="messengerLink"
            type="url"
            inputMode="url"
            defaultValue={messengerContact?.value ?? ""}
            readOnly={Boolean(messengerContact)}
            disabled={pending}
            placeholder={messengerContact ? undefined : "https://m.me/..."}
          />
          <small>{messengerContact ? "Liên kết đã lưu hiện chỉ có thể xem." : "Liên kết liên hệ công khai"}</small>
        </label>

        <section className="seller-contact-qr" aria-labelledby="seller-contact-qr-heading">
          <strong id="seller-contact-qr-heading">QR liên hệ</strong>
          <p>Ảnh QR dùng để liên hệ Zalo. Không dùng cho thanh toán.</p>
          {qrContact ? (
            <p className="seller-contact-qr__saved" role="status">Ảnh QR liên hệ đã được lưu. Hiện chưa hỗ trợ thay trực tiếp ảnh đã lưu.</p>
          ) : (
            <label className="seller-contact-qr__picker">
              <span>Chọn ảnh QR liên hệ</span>
              <input name="qrFile" type="file" accept="image/jpeg,image/png,image/webp" disabled={pending} />
            </label>
          )}
        </section>

        {hasDuplicateFrozenType ? (
          <p className="seller-contract-note" role="status">Có nhiều kênh cùng loại đang được lưu. Màn hình này hiển thị một giá trị; dùng phần quản lý trên desktop để xem hoặc xóa bản ghi thừa.</p>
        ) : null}
        {message ? <p role={messageIsError ? "alert" : "status"}>{message}</p> : null}
        <button className="seller-contact-save" type="submit" disabled={pending}>
          {pending ? "Đang lưu…" : "Lưu liên hệ"}
        </button>
      </form>

      {visibleContacts.length ? (
        <details className="seller-contact-management seller-desktop-only">
          <summary>Quản lý kênh liên hệ</summary>
          <div className="seller-row-list">
            {visibleContacts.map((contact, index) => (
              <div className="seller-row" key={contact.id ?? `${contact.contactType ?? "contact"}-${index}`}>
                <div>
                  <strong>{contactTypeCopy(contact.contactType)}</strong>
                  {contact.value ? <small>{contact.value}</small> : null}
                  {contact.qrMediaId ? <small>Ảnh QR liên hệ đã được lưu.</small> : null}
                  <small>{contact.publicContact ? "Hiển thị công khai" : "Nội bộ"}</small>
                </div>
                <div className="seller-row__actions">
                  {contact.id ? <button type="button" className="button-danger" disabled={pending} onClick={() => setDeleteTarget(contact)}>Xóa</button> : null}
                </div>
              </div>
            ))}
          </div>
        </details>
      ) : null}

      <ConfirmDialog
        open={Boolean(deleteTarget)}
        title="Xóa kênh liên hệ?"
        description="Hành động này xóa kênh liên hệ đã chọn khỏi cấu hình cửa hàng."
        confirmLabel={pending ? "Đang xóa…" : "Xóa kênh"}
        dangerous
        confirmDisabled={pending}
        cancelDisabled={pending}
        onCancel={() => { if (!pending) setDeleteTarget(undefined); }}
        onConfirm={() => { void remove(); }}
      />
    </section>
  );
}
