import { GoogleLoginButton } from "../../../features/auth/google-login-button";
import { LoginForm } from "../../../features/auth/login-form";

export default function LoginPage() {
  return (
    <main className="auth-surface">
      <section className="auth-card" aria-labelledby="login-title">
        <header className="auth-card__header">
          <p className="auth-brand">CoVie <span>Chợ Việt</span></p>
          <h1 id="login-title">Đăng nhập</h1>
          <p>Tiếp tục mua bán và kết nối với cộng đồng quanh bạn.</p>
        </header>
        <LoginForm />
        <div className="auth-divider"><span>hoặc</span></div>
        <GoogleLoginButton />
        <a className="auth-forgot" href="/api/v1/auth/google/start?intent=PASSWORD_RESET">Quên mật khẩu?</a>
      </section>
    </main>
  );
}
