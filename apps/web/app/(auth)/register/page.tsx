import Image from "next/image";
import Link from "next/link";
import { GoogleLoginButton } from "@/features/auth/google-login-button";

export default function RegisterPage() {
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
        <section className="auth-card" aria-labelledby="register-title">
          <header className="auth-card__header">
            <p className="auth-eyebrow">TÀI KHOẢN MỚI</p>
            <h1 id="register-title">Tạo tài khoản CoVie</h1>
            <p>
              Bước đầu tiên dùng Google để xác minh danh tính. Sau đó bạn sẽ tạo mật khẩu CoVie để lần sau có thể đăng nhập bằng Google hoặc email/mật khẩu.
            </p>
          </header>

          <div className="auth-truth-card">
            <strong>Đăng ký bằng Google</strong>
            <p>Không có form đăng ký email/mật khẩu trực tiếp. Email đã xác minh từ Google sẽ trở thành email đăng nhập CoVie của bạn.</p>
          </div>

          <GoogleLoginButton />

          <p className="auth-card__footer">
            Đã có tài khoản?{" "}
            <Link href="/login">Đăng nhập</Link>
          </p>
        </section>
      </div>
    </main>
  );
}
