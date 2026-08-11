import { GoogleLoginButton } from "../../../features/auth/google-login-button";
import { LoginForm } from "../../../features/auth/login-form";

export default function LoginPage() {
  return <main>
    <h1>Đăng nhập</h1>
    <LoginForm />
    <GoogleLoginButton />
    <a href="/api/v1/auth/google/start?intent=PASSWORD_RESET">Quên mật khẩu?</a>
  </main>;
}
