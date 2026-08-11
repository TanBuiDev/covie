"use client";

import { setOnboardingPassword } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function OnboardingPasswordFlow() {
  return <main>
    <h1>Đặt mật khẩu</h1>
    <PasswordForm submitPassword={setOnboardingPassword} />
    <p>Nếu liên kết đã hết hạn, hãy bắt đầu lại.</p>
    <GoogleLoginButton />
  </main>;
}
