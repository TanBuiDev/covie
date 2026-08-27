"use client";

import { useState } from "react";
import { requestBrowserApi } from "@/lib/api/api-client";
import { readApiError } from "@/lib/api/api-error";

export type ModerationActionKind = "approve" | "reject" | "hide-content" | "restore-content" | "close";

const labels: Record<ModerationActionKind, string> = {
  approve: "Phê duyệt",
  reject: "Từ chối",
  "hide-content": "Ẩn nội dung",
  "restore-content": "Khôi phục nội dung",
  close: "Đóng hồ sơ",
};

const reasonRequired = new Set<ModerationActionKind>(["reject", "hide-content", "close"]);
const destructive = new Set<ModerationActionKind>(["reject", "hide-content", "close"]);

export function ModerationActions({ caseId, actions, onComplete, onConflict }: { caseId: string; actions: ModerationActionKind[]; onComplete: () => Promise<void> | void; onConflict: (message: string) => void }) {
  const [intent, setIntent] = useState<ModerationActionKind>();
  const [reasonCode, setReasonCode] = useState("");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string>();

  async function submit() {
    if (!intent) return;
    setBusy(true); setError(undefined);
    try {
      const response = await requestBrowserApi("POST", `/api/v1/admin/moderation-cases/${encodeURIComponent(caseId)}/${intent}`, {
        reasonCode: reasonCode.trim() || undefined,
        note: note.trim() || undefined,
      }, { "Idempotency-Key": crypto.randomUUID(), "Content-Type": "application/json" });
      if (response.status === 409 || response.status === 412) {
        const problem = await readApiError(response);
        onConflict(problem.message || "Hồ sơ đã thay đổi. Hãy tải lại trước khi thực hiện hành động.");
        setIntent(undefined);
        return;
      }
      if (!response.ok) throw await readApiError(response);
      setIntent(undefined); setReasonCode(""); setNote("");
      await onComplete();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể thực hiện hành động kiểm duyệt");
    } finally { setBusy(false); }
  }

  if (!actions.length) return <p className="admin-caption">Không có hành động phù hợp với trạng thái hồ sơ hiện tại.</p>;

  return <>
    <div className="admin-action-row">{actions.map(action => <button className={destructive.has(action) ? "admin-button admin-button--danger" : "admin-button admin-button--secondary"} type="button" key={action} disabled={busy} onClick={() => { setError(undefined); setIntent(action); }}>{labels[action]}</button>)}</div>
    {intent ? <div className="dialog-backdrop" role="presentation" onMouseDown={event => { if (event.currentTarget === event.target && !busy) setIntent(undefined); }}><div className="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="moderation-confirm-title"><h2 id="moderation-confirm-title">Xác nhận {labels[intent].toLowerCase()}</h2><p>Hành động sẽ được gửi qua endpoint quản trị với Idempotency-Key. Máy chủ quyết định trạng thái hợp lệ cuối cùng.</p><label className="admin-field"><span>Mã lý do{reasonRequired.has(intent) ? " *" : ""}</span><input value={reasonCode} onChange={event => setReasonCode(event.target.value)} maxLength={60} placeholder="reasonCode" /></label><label className="admin-field"><span>Ghi chú</span><textarea value={note} onChange={event => setNote(event.target.value)} maxLength={2000} rows={3} /></label>{error ? <p className="admin-error" role="alert">{error}</p> : null}<div className="dialog-actions"><button className="admin-button admin-button--secondary" disabled={busy} onClick={() => setIntent(undefined)}>Huỷ</button><button className={destructive.has(intent) ? "admin-button admin-button--danger" : "admin-button admin-button--primary"} disabled={busy || (reasonRequired.has(intent) && !reasonCode.trim())} onClick={() => void submit()}>{busy ? "Đang xử lý…" : "Xác nhận"}</button></div></div></div> : null}
  </>;
}
