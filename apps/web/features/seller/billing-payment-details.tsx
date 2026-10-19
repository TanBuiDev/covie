"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { Copy } from "@phosphor-icons/react";
import { toDataURL } from "qrcode";
import type { components } from "@covie/api-client";
import { useBillingExpiry } from "./billing-expiry";

type CheckoutQr = Pick<Required<components["schemas"]["CheckoutResult"]>, "invoiceId" | "qrPayload">;

export function BillingPaymentDetails({ invoiceId, reference, status, expiresAt, checkout }: {
  invoiceId: string | undefined;
  reference: string | undefined;
  status: string | undefined;
  expiresAt: string | undefined;
  checkout?: CheckoutQr;
}) {
  const remaining = useBillingExpiry(expiresAt);
  const payload = checkout?.invoiceId === invoiceId ? checkout?.qrPayload : undefined;
  const pending = status === "PENDING_PAYMENT" && remaining !== null && remaining > 0;
  const [encoded, setEncoded] = useState<{ payload: string; url?: string }>();
  const [copyState, setCopyState] = useState<"idle" | "copied" | "failed">("idle");
  useEffect(() => {
    if (!pending || !payload) return;
    let active = true;
    toDataURL(payload, { width: 256, margin: 4, errorCorrectionLevel: "M" })
      .then(url => { if (active) setEncoded({ payload, url }); })
      .catch(() => { if (active) setEncoded({ payload }); });
    return () => { active = false; };
  }, [pending, payload]);

  async function copyReference() {
    if (!reference) return;
    try {
      await navigator.clipboard.writeText(reference);
      setCopyState("copied");
    } catch { setCopyState("failed"); }
  }

  const image = encoded?.payload === payload ? encoded?.url : undefined;
  return <div>
    {reference ? <>
      <p>Mã hóa đơn dùng khi tra cứu/hỗ trợ: <strong>{reference}</strong>.</p>
      <button type="button" className="admin-button admin-button--secondary"
        aria-label={`Sao chép mã hóa đơn ${reference}`} onClick={() => void copyReference()}>
        <Copy size={17} aria-hidden="true" /> Sao chép mã hóa đơn
      </button>
      {copyState === "copied" ? <p role="status">Đã sao chép mã hóa đơn.</p> : null}
      {copyState === "failed" ? <p role="alert">Không thể sao chép. Bạn có thể chọn mã hóa đơn ở trên hoặc thử lại.</p> : null}
    </> : null}
    {pending ? <>
      {image ? <Image src={image} width={256} height={256} unoptimized
        style={{ maxWidth: "100%", height: "auto" }} alt={`Mã QR thanh toán payOS cho hóa đơn ${reference ?? invoiceId ?? "đang chọn"}`} />
        : <p>{payload && encoded?.payload !== payload ? "Đang tạo mã QR thanh toán…"
          : "Mã QR hiện có trên trang thanh toán payOS. Mở link của hóa đơn này để tiếp tục."}</p>}
      <p>Chỉ sử dụng QR hoặc link thanh toán payOS của hóa đơn này; không tự thay nội dung chuyển khoản. Hiển thị QR không phải xác nhận đã thanh toán.</p>
    </> : null}
  </div>;
}
