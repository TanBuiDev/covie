"use client";

import { FormEvent, useState } from "react";
import { CheckCircle, WarningCircle } from "@phosphor-icons/react";
import { CovieApiError } from "../../lib/api/api-error";
import { PasswordInput } from "../../components/ui/password-input";
import { PasswordStrengthMeter } from "./password-strength-meter";

type PasswordFormProps = {
  submitPassword: (password: string) => Promise<void>;
  fieldName?: string;
  onComplete?: (path: string) => void;
  expiredMessage?: string;
};

export function PasswordForm({
  submitPassword,
  fieldName = "password",
  onComplete = (path) => window.location.assign(path),
  expiredMessage,
}: PasswordFormProps) {
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [message, setMessage] = useState(expiredMessage);
  const [fields, setFields] = useState<Record<string, string[]>>({});
  const [submitting, setSubmitting] = useState(false);

  const hasTypedConfirm = confirmPassword.length > 0;
  const isMatch = hasTypedConfirm && password.length > 0 && confirmPassword === password;
  const isMismatch = hasTypedConfirm && confirmPassword !== password;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setMessage(undefined);
    setFields({});

    if (hasTypedConfirm && confirmPassword !== password) {
      setMessage("Mật khẩu xác nhận không khớp.");
      return;
    }

    setSubmitting(true);
    try {
      await submitPassword(password);
      onComplete("/");
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "VALIDATION_FAILED") {
        setFields(error.fields ?? {});
        if (!error.fields?.[fieldName]?.length) {
          setMessage(Object.values(error.fields ?? {}).flat().join(" ") || "Mật khẩu không hợp lệ. Vui lòng kiểm tra và thử lại.");
        }
      } else if (error instanceof CovieApiError && error.code === "PASSWORD_RECOVERY_NOT_AVAILABLE") {
        setMessage("Phiên đặt lại mật khẩu không còn hợp lệ. Hãy xác thực Google lại bằng nút bên dưới.");
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
        <PasswordInput
          id="new-password"
          name={fieldName}
          autoComplete="new-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
          minLength={10}
        />
      </label>
      <small className="auth-form__help">Dùng ít nhất 10 ký tự, kết hợp chữ, số và ký tự đặc biệt.</small>

      <PasswordStrengthMeter password={password} />

      {fields[fieldName]?.map((error) => <p className="field-error" key={error}>{error}</p>)}

      <label htmlFor="confirm-password">
        <span>Xác nhận mật khẩu mới</span>
        <PasswordInput
          id="confirm-password"
          name="confirmPassword"
          autoComplete="new-password"
          value={confirmPassword}
          onChange={(event) => setConfirmPassword(event.target.value)}
          error={isMismatch}
        />
      </label>

      {hasTypedConfirm && (
        <div
          className={`auth-match-indicator ${isMatch ? "auth-match-indicator--success" : "auth-match-indicator--error"}`}
          role="status"
        >
          {isMatch ? (
            <>
              <CheckCircle size={15} weight="fill" />
              <span>Mật khẩu xác nhận khớp</span>
            </>
          ) : (
            <>
              <WarningCircle size={15} weight="fill" />
              <span>Mật khẩu xác nhận chưa khớp</span>
            </>
          )}
        </div>
      )}

      {message ? <p className="form-alert" role="alert">{message}</p> : null}

      <button className="button-primary auth-form__submit" disabled={submitting} type="submit">
        {submitting ? "Đang lưu…" : "Lưu mật khẩu"}
      </button>
    </form>
  );
}
