"use client";

import { FormEvent, useState } from "react";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { CovieApiError } from "@/lib/api/api-error";
import { createReport, type ReportTargetType } from "./report-api";
import styles from "./report-button.module.css";

const reasons = [
  ["MISLEADING_CONTENT", "Thông tin gây hiểu nhầm"],
  ["INAPPROPRIATE_CONTENT", "Nội dung không phù hợp"],
  ["SPAM", "Spam"],
  ["FRAUD_OR_SCAM", "Nghi ngờ lừa đảo"],
  ["OTHER", "Lý do khác"],
] as const;

export function ReportButton({
  targetType,
  targetId,
  contextMerchantId,
  label = "Báo cáo",
}: {
  targetType: ReportTargetType;
  targetId: string;
  contextMerchantId?: string;
  label?: string;
}) {
  const [open, setOpen] = useState(false);
  const [reasonCode, setReasonCode] = useState<(typeof reasons)[number][0]>("MISLEADING_CONTENT");
  const [note, setNote] = useState("");
  const [busy, setBusy] = useState(false);
  const [idempotencyKey, setIdempotencyKey] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [done, setDone] = useState(false);

  function close() {
    if (busy) return;
    setOpen(false);
    if (!done) setMessage(undefined);
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy || done) return;
    const key = idempotencyKey ?? crypto.randomUUID();
    if (!idempotencyKey) setIdempotencyKey(key);
    setBusy(true);
    setMessage(undefined);
    try {
      const result = await createReport({
        targetType,
        targetId,
        contextMerchantId,
        reasonCode,
        note: note.trim() || undefined,
      }, key);
      setDone(true);
      setMessage(result.caseId ? `Đã gửi báo cáo. Mã xử lý: ${result.caseId}.` : "Đã gửi báo cáo.");
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) {
        setMessage("Bạn cần đăng nhập để gửi báo cáo.");
      } else {
        setMessage("Chưa thể gửi báo cáo. Vui lòng thử lại.");
      }
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className={`report-control ${styles.control}`}>
      <button className={`report-control__trigger ${styles.trigger}`} type="button" onClick={() => setOpen(true)} disabled={done}>
        {done ? "Đã báo cáo" : label}
      </button>
      {open ? (
        <div className={`report-control__backdrop ${styles.backdrop}`} role="presentation" onMouseDown={(event) => { if (event.currentTarget === event.target) close(); }}>
          <section className={`report-control__dialog ${styles.dialog}`} role="dialog" aria-modal="true" aria-labelledby={`report-title-${targetId}`}>
            <div className={`report-control__header ${styles.header}`}>
              <div>
                <h2 id={`report-title-${targetId}`}>Báo cáo nội dung</h2>
                <p>CoVie sẽ chuyển báo cáo này vào quy trình kiểm duyệt.</p>
              </div>
              <button className={styles.close} type="button" aria-label="Đóng báo cáo" onClick={close}>
                <CoVieIcon name="back" />
              </button>
            </div>
            {done ? (
              <div className={`report-control__success ${styles.success}`} role="status">
                <p>{message}</p>
                <button className="button-primary" type="button" onClick={() => setOpen(false)}>Đóng</button>
              </div>
            ) : (
              <form className={`report-control__form ${styles.form}`} onSubmit={submit}>
                <label>
                  <span>Lý do báo cáo</span>
                  <select value={reasonCode} onChange={(event) => setReasonCode(event.target.value as (typeof reasons)[number][0])}>
                    {reasons.map(([value, text]) => <option key={value} value={value}>{text}</option>)}
                  </select>
                  <small className={styles.helper}>Chọn lý do phù hợp nhất.</small>
                </label>
                <label>
                  <span>Mô tả thêm</span>
                  <textarea maxLength={2000} value={note} onChange={(event) => setNote(event.target.value)} placeholder="Mô tả ngắn để đội ngũ kiểm tra nội dung." />
                  <small className={styles.helper}>Không chia sẻ thông tin nhạy cảm.</small>
                </label>
                {message ? <p className="form-alert" role="alert">{message}</p> : null}
                <div className={`report-control__actions ${styles.actions}`}>
                  <button className="button-secondary" type="button" onClick={close} disabled={busy}>Hủy</button>
                  <button className="button-primary" type="submit" disabled={busy}>{busy ? "Đang gửi…" : "Gửi báo cáo"}</button>
                </div>
                <p className={styles.disclaimer}>Báo cáo nội dung không phải là quy trình tranh chấp giao dịch và không cam kết một kết quả xử lý cụ thể.</p>
              </form>
            )}
          </section>
        </div>
      ) : null}
    </div>
  );
}
