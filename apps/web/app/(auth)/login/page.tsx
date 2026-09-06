import Image from "next/image";
import Link from "next/link";
import { GoogleLoginButton } from "../../../features/auth/google-login-button";
import { LoginForm } from "../../../features/auth/login-form";

export default function LoginPage() {
  return (
    <main className="auth-surface">
      <header className="auth-top-bar">
        <Link className="auth-top-brand" href="/" aria-label="CoVie Trang chủ">
          <Image
            src="/figma/covie-market-pin.svg"
            width={44}
            height={44}
            alt=""
            aria-hidden="true"
            priority
            unoptimized
          />
          <span className="auth-top-brand__text">
            <strong>CoVie</strong>
            <small>Chợ Việt</small>
          </span>
        </Link>
      </header>

      <div className="auth-main-wrap">
        <section className="auth-card" aria-labelledby="login-title">
          <header className="auth-card__header">
            <p className="auth-eyebrow">ĐĂNG NHẬP</p>
            <h1 id="login-title">Chào mừng bạn trở lại</h1>
            <p>Đăng nhập bằng email/mật khẩu CoVie đã tạo sau lần xác minh Google, hoặc tiếp tục bằng Google.</p>
          </header>

          <LoginForm />

          <a className="auth-forgot" href="/api/v1/auth/google/start?intent=PASSWORD_RESET">
            Quên mật khẩu?
          </a>

          <div className="auth-divider"><span>hoặc</span></div>

          <GoogleLoginButton />

          <p className="auth-card__footer">
            Chưa có tài khoản?{" "}
            <a href="/api/v1/auth/google/start?intent=LOGIN">Tạo tài khoản bằng Google</a>
          </p>
        </section>
      </div>
    </main>
  );
}
