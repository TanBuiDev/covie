import { redirect } from "next/navigation";
import { MerchantForm, MerchantList } from "@/features/merchant/merchant-form";
import { listMyMerchantsForServer } from "@/features/merchant/merchant-server-api";
import { requireUser } from "@/lib/auth/require-user";
import {
  Bicycle,
  CheckCircle,
  HouseLine,
  MapPinLine,
  PlusCircle,
  Question,
  ShieldCheck,
  Sparkle,
  Storefront,
} from "@phosphor-icons/react/dist/ssr";

export default async function SellerPage() {
  await requireUser();
  const merchants = await listMyMerchantsForServer();

  if (merchants.length === 1 && merchants[0]?.id) {
    redirect(`/seller/${encodeURIComponent(merchants[0].id)}`);
  }

  return (
    <main className="seller-entry seller-stack">
      <header className="surface-header">
        <p className="eyebrow">CoVie Seller</p>
        <h1>Seller Center</h1>
        <p>Chọn cửa hàng đang quản lý hoặc tạo cửa hàng đầu tiên. Merchant hiện tại luôn được thể hiện trên route, không nằm trong session ẩn phía client.</p>
      </header>

      {merchants.length === 0 ? (
        <section className="seller-onboarding-section">
          <div className="seller-onboarding-hero">
            <div className="seller-onboarding-hero__icon" aria-hidden="true">
              <Storefront size={32} weight="duotone" />
            </div>
            <div>
              <h2>Bán hàng trên CoVie</h2>
              <p className="seller-muted">Tạo cửa hàng đầu tiên để bắt đầu kết nối với cộng đồng cư dân toà nhà của bạn.</p>
            </div>
          </div>

          <div className="seller-onboarding-layout">
            <div className="seller-onboarding-layout__main">
              <div className="seller-form-wrapper">
                <MerchantForm />
              </div>
            </div>

            <aside className="seller-onboarding-layout__side">
              <div className="seller-roadmap-card">
                <div className="seller-roadmap-card__header">
                  <Sparkle size={18} weight="fill" className="seller-roadmap-card__icon" />
                  <strong>Lộ trình mở quán nội khu 3 bước</strong>
                </div>
                <ol className="seller-roadmap-list">
                  <li className="seller-roadmap-step seller-roadmap-step--active">
                    <span className="seller-roadmap-step__num">1</span>
                    <div className="seller-roadmap-step__content">
                      <strong>Đặt tên &amp; giới thiệu shop</strong>
                      <p>Khởi tạo danh xưng gian hàng trên CoVie ngay tại biểu mẫu bên cạnh.</p>
                    </div>
                  </li>
                  <li className="seller-roadmap-step">
                    <span className="seller-roadmap-step__num">2</span>
                    <div className="seller-roadmap-step__content">
                      <strong>Thiết lập điểm bán sảnh/tầng</strong>
                      <p>Ghim vị trí nhận hàng tại sảnh hoặc cửa căn hộ để cư dân dễ nhận.</p>
                    </div>
                  </li>
                  <li className="seller-roadmap-step">
                    <span className="seller-roadmap-step__num">3</span>
                    <div className="seller-roadmap-step__content">
                      <strong>Lên kệ món ăn &amp; sản phẩm</strong>
                      <p>Đăng thực đơn hoặc mặt hàng đầu tiên để đón đơn ngay từ hàng xóm.</p>
                    </div>
                  </li>
                </ol>
              </div>

              <div className="seller-benefits-card">
                <strong>Giá trị đồng hành cùng CoVie</strong>
                <div className="seller-benefits-grid seller-benefits-grid--compact">
                  <div className="seller-benefit-item">
                    <div className="seller-benefit-icon" aria-hidden="true">
                      <HouseLine size={20} weight="duotone" />
                    </div>
                    <div>
                      <strong>Bán hàng nội khu</strong>
                      <p>Tiếp cận trực tiếp cộng đồng cư dân trong cùng tòa nhà và khu lân cận.</p>
                    </div>
                  </div>
                  <div className="seller-benefit-item">
                    <div className="seller-benefit-icon" aria-hidden="true">
                      <Bicycle size={20} weight="duotone" />
                    </div>
                    <div>
                      <strong>Giao nhận siêu tốc</strong>
                      <p>Khoảng cách ngắn, giao nhận linh hoạt qua sảnh chính thuận tiện.</p>
                    </div>
                  </div>
                  <div className="seller-benefit-item">
                    <div className="seller-benefit-icon" aria-hidden="true">
                      <ShieldCheck size={20} weight="duotone" />
                    </div>
                    <div>
                      <strong>Cộng đồng tin cậy</strong>
                      <p>Giao dịch dựa trên chỉ số uy tín cư dân minh bạch và văn minh.</p>
                    </div>
                  </div>
                </div>
              </div>

              <div className="seller-faq-card">
                <div className="seller-faq-card__header">
                  <Question size={18} weight="bold" />
                  <strong>Hỏi đáp nhanh</strong>
                </div>
                <div className="seller-faq-list">
                  <div className="seller-faq-item">
                    <span className="seller-faq-q">CoVie có thu phí sàn không?</span>
                    <span className="seller-faq-a">Hoàn toàn 0% phí hoa hồng cho các giao dịch nội khu giữa cư dân.</span>
                  </div>
                  <div className="seller-faq-item">
                    <span className="seller-faq-q">Khách hàng thanh toán thế nào?</span>
                    <span className="seller-faq-a">Cư dân chuyển khoản ngân hàng trực tiếp hoặc tiền mặt khi nhận hàng.</span>
                  </div>
                </div>
              </div>
            </aside>
          </div>
        </section>
      ) : (
        <section className="seller-section">
          <div className="seller-section__heading">
            <div>
              <h2>Chọn cửa hàng</h2>
              <p className="seller-muted seller-mt-xs">
                Bạn đang tham gia quản lý {merchants.length} gian hàng trên CoVie.
              </p>
            </div>
          </div>
          <MerchantList merchants={merchants} />

          <details className="seller-create-expand">
            <summary className="seller-button seller-button--secondary">
              + Mở thêm cửa hàng mới
            </summary>
            <div className="seller-create-expand__form">
              <div className="seller-create-expand__intro">
                <strong>Khởi tạo gian hàng bổ sung</strong>
                <p className="seller-muted">Tạo thêm cửa hàng để phân tách các ngành hàng hoặc dịch vụ khác nhau trong khu căn hộ.</p>
              </div>
              <MerchantForm />
            </div>
          </details>
        </section>
      )}
    </main>
  );
}
