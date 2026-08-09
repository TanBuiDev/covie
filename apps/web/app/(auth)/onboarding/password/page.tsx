import { setOnboardingPassword } from "../../../../features/auth/auth-api";
import { GoogleLoginButton } from "../../../../features/auth/google-login-button";
import { PasswordForm } from "../../../../features/auth/password-form";

export default function OnboardingPasswordPage() {
  return <main>
    <h1>Đặt mật khẩu</h1>
    <PasswordForm submitPassword={setOnboardingPassword} />
    <p>Nếu liên kết đã hết hạn, hãy bắt đầu lại.</p>
    <GoogleLoginButton />
  </main>;
}
