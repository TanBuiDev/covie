"use client";

import { setOnboardingPassword } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function OnboardingPasswordFlow() {
  return (
    <main className="auth-surface">
      <section className="auth-card" aria-labelledby="onboarding-password-title">
        <header className="auth-card__header">
          <p className="auth-brand">CoVie <span>Chợ Việt</span></p>
          <h1 id="onboarding-password-title">Đặt mật khẩu</h1>
          <p>Tạo mật khẩu để hoàn tất thiết lập tài khoản.</p>
        </header>
        <PasswordForm submitPassword={setOnboardingPassword} />
        <p className="auth-card__hint">Nếu liên kết đã hết hạn, hãy bắt đầu lại bằng Google.</p>
        <GoogleLoginButton />
      </section>
    </main>
  );
}
