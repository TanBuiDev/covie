"use client";

import { FormEvent, useState } from "react";
import { CovieApiError } from "../../lib/api/api-error";
import { login } from "./auth-api";

type LoginFormProps = { onNavigate?: (path: string) => void };

export function LoginForm({ onNavigate = (path) => window.location.assign(path) }: LoginFormProps) {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState<string>();
  const [fields, setFields] = useState<Record<string, string[]>>({});

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});
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
    }
  }

  return <form onSubmit={submit}>
    <label htmlFor="email">Email</label>
    <input id="email" name="email" type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
    {fields.email?.map((error) => <p key={error}>{error}</p>)}
    <label htmlFor="password">Mật khẩu</label>
    <input id="password" name="password" type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
    {fields.password?.map((error) => <p key={error}>{error}</p>)}
    {message && <p role="alert">{message}</p>}
    <button type="submit">Đăng nhập</button>
  </form>;
}
