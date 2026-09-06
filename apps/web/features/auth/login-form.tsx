"use client";

import { FormEvent, useState, useSyncExternalStore } from "react";
import { CovieApiError } from "../../lib/api/api-error";
import { login } from "./auth-api";

type LoginFormProps = { onNavigate?: (path: string) => void };

export function LoginForm({ onNavigate = (path) => window.location.assign(path) }: LoginFormProps) {
  const hydrated = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false,
  );
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string>();
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});
    setSubmitting(true);
    try {
      await login({ email, password });
      onNavigate("/");
    } catch (error) {
      if (!(error instanceof CovieApiError)) {
        setMessage("Không thể đăng nhập. Vui lòng thử lại.");
      } else if (error.code === "INVALID_CREDENTIALS") {
        setMessage("Email hoặc mật khẩu không đúng.");
      } else if (error.code === "ACCOUNT_NOT_ACTIVE") {
        setMessage("Tài khoản chưa được kích hoạt.");
      } else if (error.code === "VALIDATION_FAILED") {
        setFields(error.fields ?? {});
      } else {
        setMessage("Không thể đăng nhập. Vui lòng thử lại.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="auth-form" method="post" onSubmit={submit}>
      <label htmlFor="email">
        <span>Email</span>
        <input id="email" name="email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
      </label>
      <small className="auth-field-hint">Email đã liên kết với tài khoản CoVie.</small>
      {fields.email?.map((error) => <p className="field-error" key={error}>{error}</p>)}

      <label htmlFor="password">
        <span>Mật khẩu</span>
        <input id="password" name="password" type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required />
      </label>
      <small className="auth-field-hint">Mật khẩu CoVie của bạn.</small>
      {fields.password?.map((error) => <p className="field-error" key={error}>{error}</p>)}

      {message ? <p className="form-alert" role="alert">{message}</p> : null}
      <button className="button-primary auth-form__submit" type="submit" disabled={!hydrated || submitting}>
        {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
      </button>
    </form>
  );
}
