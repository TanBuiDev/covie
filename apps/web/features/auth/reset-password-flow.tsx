"use client";

import { AuthShell } from "./auth-shell";
import { resetPasswordWithGoogle } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function ResetPasswordFlow() {
  return (
    <AuthShell
      heroHeadline="Khôi phục quyền truy cập tài khoản an toàn"
      heroSubtitle="Thiết lập mật khẩu CoVie mới sau khi đã xác thực qua Google để tiếp tục bảo vệ thông tin và mua sắm tiện lợi."
    >
      <section className="auth-card" aria-labelledby="reset-password-title">
        <header className="auth-card__header">
          <p className="auth-eyebrow">KHÔI PHỤC TÀI KHOẢN</p>
          <h1 id="reset-password-title">Đặt lại mật khẩu</h1>
          <p>Nhập mật khẩu mới cho tài khoản CoVie của bạn sau khi đã xác thực danh tính qua Google.</p>
        </header>

        <PasswordForm submitPassword={resetPasswordWithGoogle} />

        <div className="auth-divider">
          <span>hoặc bắt đầu lại</span>
        </div>

        <p className="auth-card__hint">Nếu phiên làm việc hoặc liên kết đã hết hạn, vui lòng xác thực Google lại:</p>
        <GoogleLoginButton intent="PASSWORD_RESET" />
      </section>
    </AuthShell>
  );
}
