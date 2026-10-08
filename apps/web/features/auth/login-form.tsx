"use client";

import { FormEvent, type ReactNode, useState, useSyncExternalStore } from "react";
import { Info } from "@phosphor-icons/react";
import { CovieApiError } from "../../lib/api/api-error";
import { login } from "./auth-api";
import { safeReturnTo } from "../../lib/auth/return-to";
import { PasswordInput } from "../../components/ui/password-input";

type LoginFormProps = {
  onNavigate?: (path: string) => void;
  returnTo?: string;
  forgotPasswordSlot?: ReactNode;
};

function getReturnToMessage(returnTo?: string): string | null {
  if (!returnTo) return null;
  const lower = returnTo.toLowerCase();
  if (lower.includes("seller")) {
    return "Bạn đang truy cập Kênh Người Bán. Vui lòng đăng nhập để tiếp tục quản lý gian hàng.";
  }
  if (lower.includes("cart") || lower.includes("checkout") || lower.includes("order")) {
    return "Bạn đang thực hiện thanh toán. Vui lòng đăng nhập để tiếp tục đơn hàng.";
  }
  if (lower.includes("need")) {
    return "Bạn đang tương tác với Nhu cầu cư dân. Vui lòng đăng nhập để xem thông tin chi tiết.";
  }
  if (lower.includes("admin")) {
    return "Vui lòng đăng nhập tài khoản Quản trị viên để truy cập trang quản trị.";
  }
  return "Vui lòng đăng nhập tài khoản CoVie để tiếp tục phiên làm việc.";
}

export function LoginForm({
  onNavigate = (path) => window.location.assign(path),
  returnTo,
  forgotPasswordSlot,
}: LoginFormProps) {
  const hydrated = useSyncExternalStore(
    () => () => undefined,
    () => true,
    () => false,
  );
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [rememberMe, setRememberMe] = useState(false);
  const [message, setMessage] = useState<string>();
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [submitting, setSubmitting] = useState(false);

  const returnToHint = getReturnToMessage(returnTo);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});
    setSubmitting(true);
    try {
      await login({ email, password });
      onNavigate(safeReturnTo(returnTo));
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
      {returnToHint ? (
        <div className="auth-return-banner" role="status">
          <Info size={18} weight="fill" className="auth-return-banner__icon" />
          <span>{returnToHint}</span>
        </div>
      ) : null}

      <label htmlFor="email">
        <span>Email</span>
        <input
          id="email"
          name="email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          required
        />
      </label>
      <small className="auth-field-hint">Email đã liên kết với tài khoản CoVie.</small>
      {fields.email?.map((error) => <p className="field-error" key={error}>{error}</p>)}

      <label htmlFor="password">
        <span>Mật khẩu</span>
        <PasswordInput
          id="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />
      </label>
      <small className="auth-field-hint">Mật khẩu CoVie của bạn.</small>
      {fields.password?.map((error) => <p className="field-error" key={error}>{error}</p>)}

      <div className="auth-form__extra-row">
        <label className="auth-remember-checkbox">
          <input
            type="checkbox"
            name="rememberMe"
            checked={rememberMe}
            onChange={(e) => setRememberMe(e.target.checked)}
          />
          <span>Ghi nhớ đăng nhập</span>
        </label>
        {forgotPasswordSlot}
      </div>

      {message ? <p className="form-alert" role="alert">{message}</p> : null}
      <button className="button-primary auth-form__submit" type="submit" disabled={!hydrated || submitting}>
        {submitting ? "Đang đăng nhập…" : "Đăng nhập"}
      </button>
    </form>
  );
}
