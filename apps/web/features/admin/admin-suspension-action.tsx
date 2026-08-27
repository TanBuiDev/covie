"use client";

import { useState } from "react";

export function AdminSuspensionAction({ status, onSubmit }: { status: string; onSubmit: (operation: "suspend" | "restore", reason?: string) => Promise<void> }) {
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [intent, setIntent] = useState<"suspend" | "restore" | null>(null);
  const [error, setError] = useState<string>();
  const suspended = status === "SUSPENDED";

  async function submit() {
    if (!intent) return;
    setBusy(true);
    setError(undefined);
    try {
      await onSubmit(intent, intent === "suspend" ? reason.trim() : undefined);
      setReason("");
      setIntent(null);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể hoàn tất thao tác");
    } finally {
      setBusy(false);
    }
  }

  return <>
    <div className="admin-action-row">
      <button className="admin-button admin-button--danger" disabled={busy || suspended} onClick={() => setIntent("suspend")}>Tạm ngưng</button>
      <button className="admin-button admin-button--secondary" disabled={busy || !suspended} onClick={() => setIntent("restore")}>Khôi phục</button>
    </div>
    {intent ? <div className="dialog-backdrop" role="presentation" onMouseDown={event => { if (event.currentTarget === event.target && !busy) setIntent(null); }}>
      <div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="admin-suspension-title">
        <h2 id="admin-suspension-title">{intent === "suspend" ? "Xác nhận tạm ngưng" : "Xác nhận khôi phục"}</h2>
        <p>{intent === "suspend" ? "Tài khoản sẽ bị tạm ngưng theo quyền và chính sách do máy chủ áp dụng." : "Tài khoản sẽ được gửi yêu cầu khôi phục theo quyền do máy chủ áp dụng."}</p>
        {intent === "suspend" ? <label className="admin-field"><span>Lý do</span><input value={reason} onChange={event => setReason(event.target.value)} placeholder="Nhập mã hoặc lý do quản trị" autoFocus /></label> : null}
        {error ? <p className="admin-error" role="alert">{error}</p> : null}
        <div className="dialog-actions">
          <button className="admin-button admin-button--secondary" disabled={busy} onClick={() => setIntent(null)}>Huỷ</button>
          <button className={intent === "suspend" ? "admin-button admin-button--danger" : "admin-button admin-button--primary"} disabled={busy || (intent === "suspend" && !reason.trim())} onClick={() => void submit()}>{busy ? "Đang xử lý…" : "Xác nhận"}</button>
        </div>
      </div>
    </div> : null}
  </>;
}
