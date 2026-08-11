"use client";

import { resetPasswordWithGoogle } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function ResetPasswordFlow() {
  return <main>
    <h1>Đặt lại mật khẩu</h1>
    <PasswordForm fieldName="newPassword" submitPassword={resetPasswordWithGoogle} />
    <p>Nếu liên kết đã hết hạn, hãy xác thực Google lại.</p>
    <GoogleLoginButton intent="PASSWORD_RESET" />
  </main>;
}
