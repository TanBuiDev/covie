import Link from "next/link";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { GoogleLoginButton } from "@/features/auth/google-login-button";
import styles from "./register.module.css";

export default function RegisterPage() {
  return (
    <main className={styles.screen}>
      <header className={styles.appBar}>
        <Link className={styles.back} href="/login" aria-label="Quay lại đăng nhập">
          <CoVieIcon name="back" />
        </Link>
        <h1>Tạo tài khoản</h1>
      </header>

      <section className={styles.body} aria-labelledby="register-title">
        <p className={styles.eyebrow}>Tài khoản mới</p>
        <h2 id="register-title">Tạo tài khoản CoVie</h2>
        <p className={styles.description}>
          Bước đầu tiên dùng Google để xác minh danh tính. Sau đó bạn sẽ tạo mật khẩu CoVie để lần sau có thể đăng nhập bằng Google hoặc email/mật khẩu.
        </p>

        <div className={styles.truthCard}>
          <strong>Đăng ký bằng Google</strong>
          <p>Không có form đăng ký email/mật khẩu trực tiếp. Email đã xác minh từ Google sẽ trở thành email đăng nhập CoVie.</p>
        </div>

        <div className={styles.googleAction}>
          <GoogleLoginButton />
        </div>

        <Link className={styles.loginLink} href="/login">Đã có tài khoản? Đăng nhập</Link>
      </section>
    </main>
  );
}
