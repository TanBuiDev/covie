"use client";

import { resetPasswordWithGoogle } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function ResetPasswordFlow() {
  return (
    <main className="auth-surface">
      <section className="auth-card" aria-labelledby="reset-password-title">
        <header className="auth-card__header">
          <p className="auth-brand">CoVie <span>Chợ Việt</span></p>
          <h1 id="reset-password-title">Đặt lại mật khẩu</h1>
          <p>Đặt mật khẩu mới sau khi xác thực tài khoản Google của bạn.</p>
        </header>
        <PasswordForm fieldName="newPassword" submitPassword={resetPasswordWithGoogle} />
        <p className="auth-card__hint">Nếu liên kết đã hết hạn, hãy xác thực Google lại.</p>
        <GoogleLoginButton intent="PASSWORD_RESET" />
      </section>
    </main>
  );
}
