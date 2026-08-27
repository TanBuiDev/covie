"use client";

import { FormEvent, useState } from "react";
import { CovieApiError } from "../../lib/api/api-error";

type PasswordFormProps = {
  submitPassword: (password: string) => Promise<void>;
  fieldName?: string;
  onComplete?: (path: string) => void;
  expiredMessage?: string;
};

export function PasswordForm({ submitPassword, fieldName = "password", onComplete = (path) => window.location.assign(path), expiredMessage }: PasswordFormProps) {
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState(expiredMessage);
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});
    setSubmitting(true);
    try {
      await submitPassword(password);
      onComplete("/");
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "VALIDATION_FAILED") {
        setFields(error.fields ?? {});
      } else if (error instanceof CovieApiError && error.code.startsWith("AUTH_ACTION_")) {
        setMessage("Liên kết xác thực đã hết hạn. Hãy đăng nhập Google lại.");
      } else {
        setMessage("Không thể cập nhật mật khẩu. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" onSubmit={submit}>
      <label htmlFor="new-password">
        <span>Mật khẩu mới</span>
        <input id="new-password" name={fieldName} type="password" autoComplete="new-password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={10} />
      </label>
      <small className="auth-form__help">Dùng ít nhất 10 ký tự.</small>
      {fields[fieldName]?.map((error) => <p className="field-error" key={error}>{error}</p>)}
      {message ? <p className="form-alert" role="alert">{message}</p> : null}
      <button className="button-primary auth-form__submit" disabled={submitting} type="submit">{submitting ? "Đang lưu…" : "Lưu mật khẩu"}</button>
    </form>
  );
}
