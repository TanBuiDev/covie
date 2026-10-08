import { AuthShell } from "../../../features/auth/auth-shell";
import { GoogleAuthLink, GoogleLoginButton } from "../../../features/auth/google-login-button";
import { LoginForm } from "../../../features/auth/login-form";

export default async function LoginPage({
  searchParams,
}: {
  searchParams?: Promise<{ returnTo?: string | string[] }>;
}) {
  const query = await searchParams;
  const returnTo = typeof query?.returnTo === "string" ? query.returnTo : undefined;

  return (
    <AuthShell
      heroHeadline="Mua sắm & trao đổi tiện lợi cùng hàng xóm CoVie"
      heroSubtitle="Đăng nhập để xem danh mục thực phẩm tươi sạch hôm nay, theo dõi đơn hàng và trao đổi trong cộng đồng nội khu."
      returnTo={returnTo}
    >
      <section className="auth-card" aria-labelledby="login-title">
        <header className="auth-card__header">
          <p className="auth-eyebrow">ĐĂNG NHẬP</p>
          <h1 id="login-title">Chào mừng bạn trở lại</h1>
          <p>
            Đăng nhập bằng email &amp; mật khẩu CoVie đã tạo, hoặc tiếp tục nhanh bằng tài khoản Google đã xác minh.
          </p>
        </header>

        <LoginForm
          returnTo={returnTo}
          forgotPasswordSlot={
            <GoogleAuthLink className="auth-forgot-inline" intent="PASSWORD_RESET">
              Quên mật khẩu?
            </GoogleAuthLink>
          }
        />

        <div className="auth-divider">
          <span>hoặc</span>
        </div>

        <GoogleLoginButton />

        <p className="auth-card__footer">
          Chưa có tài khoản?{" "}
          <GoogleAuthLink>Tạo tài khoản bằng Google</GoogleAuthLink>
        </p>
      </section>
    </AuthShell>
  );
}
