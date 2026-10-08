import Link from "next/link";
import styles from "./ad-banner.module.css";

export type AdBannerVariant = "flash" | "nearby" | "spotlight" | "hero";

export type AdBannerProps = {
  variant: AdBannerVariant;
  /** Destination for the sample creative. The demo defaults to discovery. */
  href?: string;
};

const accessibleNames: Record<AdBannerVariant, string> = {
  flash: "Ưu đãi nổi bật",
  nearby: "Khám phá gần bạn",
  spotlight: "Gian hàng địa phương",
  hero: "Khám phá chợ Việt gần bạn",
};

/**
 * Four presentation-only ad creatives. No tracking, autoplay video,
 * remote scripts or dynamic campaign claims. A real ad service can
 * later supply the link and replace the placeholder copy.
 */
export function AdBanner({ variant, href = "/search" }: AdBannerProps) {
  return (
    <Link
      href={href}
      aria-label={`Quảng cáo mẫu: ${accessibleNames[variant]}`}
      className={`${styles.banner} ${styles[variant]}`}
    >
      <span className={styles.disclosure}>Quảng cáo · Mẫu</span>

      {variant === "flash" && (
        <>
          <span className={styles.flashHalo} aria-hidden="true" />
          <span className={styles.flashBulbs} aria-hidden="true">
            {Array.from({ length: 10 }, (_, i) => <i key={i} />)}
          </span>
          <span className={styles.flashEyebrow}>CoVie DEALS</span>
          <strong className={styles.flashTitle}>ƯU ĐÃI<br />NỔI BẬT</strong>
          <span className={styles.flashDivider} aria-hidden="true">✦</span>
          <span className={styles.flashDescription}>Khám phá các lựa chọn hấp dẫn quanh bạn.</span>
          <span className={styles.flashArt} aria-hidden="true">
            <span className={styles.flashBag}>✦</span>
            <span className={styles.flashOrbit} />
          </span>
          <span className={styles.flashButton}>Khám phá ngay <span aria-hidden="true">↗</span></span>
        </>
      )}

      {variant === "nearby" && (
        <>
          <span className={styles.nearbyPattern} aria-hidden="true" />
          <span className={styles.nearbyEyebrow}>GẦN HƠN · TIỆN HƠN</span>
          <strong className={styles.nearbyTitle}>Chợ hay<br />ở ngay<br /><em>quanh đây.</em></strong>
          <span className={styles.nearbyPinScene} aria-hidden="true">
            <span className={styles.nearbyRing} />
            <span className={styles.nearbyRing} />
            <span className={styles.nearbyPin}>⌖</span>
            <span className={styles.nearbyDot} />
          </span>
          <span className={styles.nearbyDescription}>Tìm hàng hóa và dịch vụ ở gần bạn.</span>
          <span className={styles.nearbyButton}>Xem quanh tôi <span aria-hidden="true">→</span></span>
        </>
      )}

      {variant === "spotlight" && (
        <>
          <span className={styles.spotlightBeam} aria-hidden="true" />
          <span className={styles.spotlightEyebrow}>CO VIE · GÓC KHÁM PHÁ</span>
          <strong className={styles.spotlightTitle}>Gian hàng<br /><em>gần bạn</em></strong>
          <span className={styles.spotlightDescription}>Một khu chợ số hiện đại, kết nối cộng đồng địa phương.</span>
          <span className={styles.spotlightIllustration} aria-hidden="true">
            <span className={styles.shopRoof} />
            <span className={styles.shopBody}>
              <span className={styles.shopAwning} />
              <span className={styles.shopWindow} />
              <span className={styles.shopDoor} />
            </span>
            <span className={styles.shopCoin}>✳</span>
            <span className={styles.shopSparkle}>✦</span>
          </span>
          <span className={styles.spotlightButton}>Khám phá gian hàng <span aria-hidden="true">↗</span></span>
          <span className={styles.spotlightFooter}>Hàng hóa · Dịch vụ · Nhu cầu</span>
        </>
      )}

      {variant === "hero" && (
        <>
          <span className={styles.heroGrid} aria-hidden="true" />
          <span className={styles.heroCopy}>
            <span className={styles.heroEyebrow}>CoVie — CHỢ VIỆT HIỆN ĐẠI</span>
            <strong className={styles.heroTitle}>Có cả một khu chợ<br /><em>ở gần bạn.</em></strong>
            <span className={styles.heroDescription}>Tìm sản phẩm, dịch vụ và những kết nối địa phương.</span>
            <span className={styles.heroButton}>Bắt đầu khám phá <span aria-hidden="true">↗</span></span>
          </span>
          <span className={styles.heroScene} aria-hidden="true">
            <span className={styles.heroHalo} />
            <span className={styles.heroCardBack}><span>✦</span></span>
            <span className={styles.heroCardFront}>
              <span className={styles.heroShopIcon}>⌂</span>
              <span className={styles.heroCardLine} />
              <span className={styles.heroCardLine} />
              <span className={styles.heroCardPill}>GẦN BẠN</span>
            </span>
            <span className={styles.heroStar}>✳</span>
          </span>
        </>
      )}
    </Link>
  );
}
