import type { Metadata } from "next";
import { AdBanner } from "@/components/ads/ad-banner";
import styles from "./page.module.css";

export const metadata: Metadata = {
  title: "Thư viện banner mẫu | CoVie",
  description: "Bốn mẫu banner quảng cáo chuyển động nhẹ dành cho CoVie.",
};

export default function BannerDemoPage() {
  return (
    <div className={styles.page}>
      <header className={styles.header}>
        <p className={styles.kicker}>COVIE · THƯ VIỆN QUẢNG CÁO MẪU</p>
        <h1>Banner quảng cáo có hiệu ứng</h1>
        <p>
          Bốn ý tưởng xem thử cho chợ địa phương: ánh đèn nhẹ, điểm vị trí chuyển động,
          gian hàng nổi bật và banner ngang. Nội dung chỉ mang tính minh họa;
          nhấp vào banner sẽ mở trang khám phá.
        </p>
      </header>

      <section aria-labelledby="sidebar-gallery">
        <div className={styles.sectionHeading}>
          <div>
            <h2 id="sidebar-gallery">Banner dọc cho hai bên website</h2>
            <p>Dành cho màn hình desktop đủ rộng, không chen vào vùng sản phẩm.</p>
          </div>
        </div>
        <div className={styles.gallery}>
          <article className={styles.example}>
            <div className={styles.preview}><AdBanner variant="flash" /></div>
            <div className={styles.details}>
              <h3>01 · Emerald LED</h3>
              <p>160 × 600 px · Ánh đèn và túi quà chuyển động nhẹ</p>
            </div>
          </article>
          <article className={styles.example}>
            <div className={styles.preview}><AdBanner variant="nearby" /></div>
            <div className={styles.details}>
              <h3>02 · Khám phá quanh đây</h3>
              <p>160 × 600 px · Định vị và vòng sáng lan tỏa</p>
            </div>
          </article>
          <article className={styles.example}>
            <div className={styles.preview}><AdBanner variant="spotlight" /></div>
            <div className={styles.details}>
              <h3>03 · Gian hàng địa phương</h3>
              <p>300 × 600 px · Ánh sáng quét qua và gian hàng nổi</p>
            </div>
          </article>
        </div>
      </section>

      <section aria-labelledby="hero-gallery">
        <div className={styles.sectionHeading}>
          <div>
            <h2 id="hero-gallery">Banner ngang cho trang chủ</h2>
            <p>Bố cục linh hoạt theo chiều ngang, không dùng video tự chạy.</p>
          </div>
        </div>
        <div className={styles.widePreview}>
          <AdBanner variant="hero" />
        </div>
        <div className={styles.details}>
          <h3>04 · CoVie gần bạn</h3>
          <p>Toàn chiều rộng container · Minh họa nổi, ánh sáng nền và CTA rõ ràng</p>
        </div>
      </section>

      <p className={styles.note}>
        Đây là các mẫu trình diễn, không phải chiến dịch đang chạy. Hiệu ứng tự tắt khi
        người dùng bật cài đặt giảm chuyển động (<code>prefers-reduced-motion</code>).
      </p>
    </div>
  );
}
