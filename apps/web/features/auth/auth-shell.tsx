import Image from "next/image";
import Link from "next/link";
import { type ReactNode } from "react";
import { ArrowLeft, ShieldCheck, Lightning, Users, Sparkle, HouseLine, Leaf } from "@phosphor-icons/react/dist/ssr";
import styles from "./auth-shell.module.css";

interface AuthShellProps {
  children: ReactNode;
  heroHeadline?: string;
  heroSubtitle?: string;
  returnTo?: string;
  backLabel?: string;
}

export function AuthShell({
  children,
  heroHeadline = "Chợ cư dân văn minh, kết nối mọi nhà",
  heroSubtitle = "Nền tảng mua sắm & trao đổi tiện lợi, an tâm giữa các hộ cư dân trong cùng tòa nhà và phân khu đô thị.",
  returnTo,
  backLabel,
}: AuthShellProps) {
  const isInternal = returnTo && returnTo.startsWith("/") && !returnTo.startsWith("//");
  const safeBackHref = isInternal ? returnTo : "/";
  let backText = backLabel ?? "Quay lại trang chủ";
  if (!backLabel && isInternal) {
    if (returnTo.startsWith("/cart")) backText = "Quay lại giỏ hàng";
    else if (returnTo.startsWith("/checkout")) backText = "Quay lại thanh toán";
    else if (returnTo.startsWith("/items")) backText = "Quay lại sản phẩm";
    else if (returnTo.startsWith("/seller")) backText = "Quay lại kênh người bán";
    else backText = "Quay lại";
  }

  return (
    <div className={styles.container}>
      <header className={styles.topBar}>
        <div className={styles.topBarInner}>
          <Link className={styles.brand} href="/" aria-label="CoVie Trang chủ">
            <Image
              src="/figma/covie-market-pin.svg"
              width={40}
              height={40}
              alt=""
              aria-hidden="true"
              priority
              unoptimized
            />
            <span className={styles.brandText}>
              <strong>CoVie</strong>
              <small>Chợ Việt</small>
            </span>
          </Link>
          <div className={styles.topBarRight}>
            <Link href={safeBackHref} className={styles.backHomeLink}>
              <ArrowLeft size={15} weight="bold" className={styles.backHomeIcon} />
              <span>{backText}</span>
            </Link>
          </div>
        </div>
      </header>

      <main className={styles.main}>
        <div className={styles.splitLayout}>
          {/* Form Pane (Left) */}
          <div className={styles.formPane}>
            {children}
          </div>

          {/* Emerald Community Showcase (Right on Desktop) */}
          <aside className={styles.showcasePane} aria-label="Giới thiệu cộng đồng CoVie">
            <div className={styles.showcaseContent}>
              <div className={styles.badge}>
                <Sparkle size={14} weight="fill" className={styles.badgeIcon} />
                <span>Cộng đồng nội khu CoVie</span>
              </div>

              <h2 className={styles.headline}>{heroHeadline}</h2>
              <p className={styles.subtitle}>{heroSubtitle}</p>

              <div className={styles.features}>
                <div className={styles.featureItem}>
                  <div className={styles.featureIconWrap}>
                    <ShieldCheck size={24} weight="duotone" />
                  </div>
                  <div className={styles.featureText}>
                    <strong>100% Cư dân xác thực</strong>
                    <span>Xác minh tài khoản minh bạch, mua bán và trao đổi an tâm tuyệt đối.</span>
                  </div>
                </div>

                <div className={styles.featureItem}>
                  <div className={styles.featureIconWrap}>
                    <Lightning size={24} weight="duotone" />
                  </div>
                  <div className={styles.featureText}>
                    <strong>Giao siêu tốc 15 phút</strong>
                    <span>Hàng xóm ngay cùng tầng, cùng block giao nhận nhanh gọn lẹ.</span>
                  </div>
                </div>

                <div className={styles.featureItem}>
                  <div className={styles.featureIconWrap}>
                    <Users size={24} weight="duotone" />
                  </div>
                  <div className={styles.featureText}>
                    <strong>Hàng xóm văn minh & tin cậy</strong>
                    <span>Đánh giá thật, sản phẩm chất lượng, chia sẻ thực phẩm và đồ dùng hữu ích.</span>
                  </div>
                </div>
              </div>

              <div className={styles.socialProof}>
                <div className={styles.avatarGroup}>
                  <span className={styles.avatarMini}><HouseLine size={16} weight="duotone" /></span>
                  <span className={styles.avatarMini}><Leaf size={16} weight="duotone" /></span>
                  <span className={styles.avatarMini}><Sparkle size={16} weight="fill" /></span>
                </div>
                <div className={styles.socialProofText}>
                  <strong>Hơn 500+ hộ cư dân</strong>
                  <span>đang kết nối và giao dịch tích cực mỗi ngày</span>
                </div>
              </div>
            </div>
          </aside>
        </div>
      </main>
    </div>
  );
}
