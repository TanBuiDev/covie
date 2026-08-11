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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});
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
    }
  }

  return <form onSubmit={submit}>
    <label htmlFor="new-password">Mật khẩu mới</label>
    <input id="new-password" name={fieldName} type="password" value={password} onChange={(event) => setPassword(event.target.value)} required minLength={10} />
    {fields[fieldName]?.map((error) => <p key={error}>{error}</p>)}
    {message && <p role="alert">{message}</p>}
    <button type="submit">Lưu mật khẩu</button>
  </form>;
}
