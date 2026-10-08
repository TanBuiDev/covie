"use client";

import { AuthShell } from "./auth-shell";
import { setOnboardingPassword } from "./auth-api";
import { GoogleLoginButton } from "./google-login-button";
import { PasswordForm } from "./password-form";

export function OnboardingPasswordFlow() {
  return (
    <AuthShell
      heroHeadline="Chào mừng bạn đến với cộng đồng CoVie"
      heroSubtitle="Chỉ còn một bước thiết lập mật khẩu nữa là bạn có thể tham gia mua bán, đăng bài tìm đồ và kết nối cùng hàng xóm thân thiết."
    >
      <section className="auth-card" aria-labelledby="onboarding-password-title">
        <header className="auth-card__header">
          <span className="auth-step-badge">Bước 2/2: Bảo vệ tài khoản</span>
          <h1 id="onboarding-password-title">Đặt mật khẩu</h1>
          <p>
            Tạo mật khẩu riêng cho CoVie để sau này bạn có thể đăng nhập linh hoạt bằng tài khoản Google hoặc bằng email &amp; mật khẩu.
          </p>
        </header>

        <PasswordForm submitPassword={setOnboardingPassword} />

        <div className="auth-divider">
          <span>hoặc bắt đầu lại</span>
        </div>

        <p className="auth-card__hint">Nếu phiên thiết lập đã hết hạn, vui lòng bắt đầu lại bằng Google:</p>
        <GoogleLoginButton />
      </section>
    </AuthShell>
  );
}
