import { resetPasswordWithGoogle } from "../../../features/auth/auth-api";
import { GoogleLoginButton } from "../../../features/auth/google-login-button";
import { PasswordForm } from "../../../features/auth/password-form";

export default function ResetPasswordPage() {
  return <main>
    <h1>Đặt lại mật khẩu</h1>
    <PasswordForm submitPassword={resetPasswordWithGoogle} />
    <p>Nếu liên kết đã hết hạn, hãy xác thực Google lại.</p>
    <GoogleLoginButton intent="PASSWORD_RESET" />
  </main>;
}
