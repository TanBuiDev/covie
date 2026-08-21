"use client";

import { FormEvent, useState } from "react";
import { createShippingPolicy } from "./shipping-api";

export function ShippingPolicyForm({ merchantId, locationId }: { merchantId: string; locationId: string }) {
  const [mode, setMode] = useState<"FREE" | "FIXED" | "NEGOTIATED" | "CONDITIONAL">("FREE");
  const [message, setMessage] = useState<string>();
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = new FormData(event.currentTarget); const fee = Number(form.get("baseFee"));
    try { await createShippingPolicy(merchantId, locationId, { mode, status: "ACTIVE", currencyCode: "VND", baseFee: mode === "FREE" || mode === "NEGOTIATED" ? undefined : fee, ruleMatchMode: mode === "CONDITIONAL" ? "ANY" : undefined }); window.location.reload(); }
    catch { setMessage("Không thể lưu chính sách giao hàng."); }
  }
  return <form onSubmit={submit}><h2>Chính sách giao hàng</h2><label>Loại phí <select value={mode} onChange={(event) => setMode(event.target.value as typeof mode)}><option value="FREE">Miễn phí</option><option value="FIXED">Phí cố định</option><option value="NEGOTIATED">Phí giao hàng thỏa thuận</option><option value="CONDITIONAL">Có điều kiện</option></select></label>{mode === "NEGOTIATED" ? <p>Phí giao hàng sẽ được thỏa thuận với người mua, không phải 0 đồng.</p> : mode !== "FREE" && <label>Phí cơ bản (VND) <input name="baseFee" type="number" min="0" required /></label>}{message && <p role="alert">{message}</p>}<button type="submit">Lưu chính sách</button></form>;
}
