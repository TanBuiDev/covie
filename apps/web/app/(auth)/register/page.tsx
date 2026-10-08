import Link from "next/link";
import { Lightning, ShieldCheck } from "@phosphor-icons/react/dist/ssr";
import { AuthShell } from "@/features/auth/auth-shell";
import { GoogleLoginButton } from "@/features/auth/google-login-button";

export default function RegisterPage() {
  return (
    <AuthShell
      heroHeadline="Gia nhập chợ nội khu cư dân CoVie ngay hôm nay"
      heroSubtitle="Kết nối cùng hàng xóm, nhận thông báo thực phẩm tươi sạch mỗi sáng, mua sắm an tâm với 100% tài khoản đã xác thực."
    >
      <section className="auth-card" aria-labelledby="register-title">
        <header className="auth-card__header">
          <p className="auth-eyebrow">TÀI KHOẢN MỚI</p>
          <h1 id="register-title">Tạo tài khoản CoVie</h1>
          <p>
            Chỉ với 2 bước đơn giản để kích hoạt tài khoản cư dân và bắt đầu mua bán nội khu.
          </p>
        </header>

        {/* 3-Step Visual Stepper */}
        <div className="auth-stepper" aria-label="Các bước kích hoạt tài khoản">
          <div className="auth-stepper__step auth-stepper__step--active">
            <span className="auth-stepper__num">1</span>
            <span className="auth-stepper__title">Xác thực Google</span>
          </div>
          <span className="auth-stepper__arrow" aria-hidden="true">→</span>
          <div className="auth-stepper__step">
            <span className="auth-stepper__num">2</span>
            <span className="auth-stepper__title">Tạo mật khẩu CoVie</span>
          </div>
          <span className="auth-stepper__arrow" aria-hidden="true">→</span>
          <div className="auth-stepper__step">
            <span className="auth-stepper__num">3</span>
            <span className="auth-stepper__title">Khám phá chợ</span>
          </div>
        </div>

        <div className="auth-truth-card">
          <strong>Đăng ký an toàn qua Google</strong>
          <p>
            Email đã xác thực từ tài khoản Google của bạn sẽ trở thành tài khoản đăng nhập chính thức trên CoVie, bảo vệ cộng đồng khỏi tài khoản ảo.
          </p>
        </div>

        <div className="auth-resident-perks">
          <div className="auth-resident-perk">
            <span className="auth-resident-perk__icon" aria-hidden="true">
              <ShieldCheck size={20} weight="duotone" />
            </span>
            <div className="auth-resident-perk__text">
              <strong>100% Cư dân thật</strong>
              <span>Giao dịch an tâm với các hộ lân cận trong khu đô thị.</span>
            </div>
          </div>
          <div className="auth-resident-perk">
            <span className="auth-resident-perk__icon" aria-hidden="true">
              <Lightning size={20} weight="duotone" />
            </span>
            <div className="auth-resident-perk__text">
              <strong>Nhận hàng sau 15 phút</strong>
              <span>Không cần chờ shipper liên tỉnh, nhận đồ ngay tại sảnh hoặc căn hộ.</span>
            </div>
          </div>
        </div>

        <GoogleLoginButton />

        <p className="auth-card__footer">
          Đã có tài khoản?{" "}
          <Link href="/login">Đăng nhập</Link>
        </p>
      </section>
    </AuthShell>
  );
}
