"use client";

import Link from "next/link";
import { useState } from "react";
import {
  Bicycle,
  ChatCircleDots,
  CheckCircle,
  EnvelopeSimple,
  FacebookLogo,
  Handshake,
  Headset,
  MapPin,
  PaperPlaneRight,
  PhoneCall,
  ShieldCheck,
  Storefront,
} from "@phosphor-icons/react";
import { LocationChooser } from "@/features/location/location-chooser";
import { useBrowseLocation } from "@/features/location/use-browse-location";
import styles from "./app-footer.module.css";

const TRUST_PILLARS = [
  {
    icon: ShieldCheck,
    title: "100% Cư dân xác thực",
    desc: "Giao dịch an toàn, người thật việc thật trong cộng đồng láng giềng.",
  },
  {
    icon: Bicycle,
    title: "Giao nhanh 15 - 30 phút",
    desc: "Nhận hàng hỏa tốc nội khu hoặc hẹn gặp trao tay tại sảnh căn hộ.",
  },
  {
    icon: Handshake,
    title: "Mua bán & Dịch vụ đa dạng",
    desc: "Bách hóa, món ngon, sửa chữa tại nhà & thanh lý đồ cũ tiện lợi.",
  },
  {
    icon: Headset,
    title: "Hỗ trợ cộng đồng 24/7",
    desc: "Ban quản trị & đội ngũ hỗ trợ cư dân tận tâm giải quyết mọi vấn đề.",
  },
] as const;

export function AppFooter() {
  const { location } = useBrowseLocation();
  const [chooserOpen, setChooserOpen] = useState(false);
  const [newsletterInput, setNewsletterInput] = useState("");
  const [newsletterStatus, setNewsletterStatus] = useState<"idle" | "success" | "error">("idle");
  const [newsletterMessage, setNewsletterMessage] = useState("");

  function handleNewsletterSubmit(e: React.FormEvent) {
    e.preventDefault();
    const trimmed = newsletterInput.trim();
    if (!trimmed) {
      setNewsletterStatus("error");
      setNewsletterMessage("Vui lòng nhập email hoặc số điện thoại.");
      return;
    }
    setNewsletterStatus("success");
    setNewsletterMessage("Đã đăng ký nhận bản tin thành công! CoVie sẽ gửi ưu đãi sớm nhất.");
    setNewsletterInput("");
  }

  return (
    <footer className={styles.footer} aria-label="Chân trang CoVie">
      <div className={styles.container}>
        {/* 1. Main Navigation Grid */}
        <div className={styles.mainGrid}>
          {/* Column 1: Brand & Community Location Hub */}
          <div className={styles.brandCol}>
            <Link href="/" className={styles.brandLogo} aria-label="Trang chủ CoVie">
              <span className={styles.logoBadge}>
                <Storefront size={22} weight="duotone" />
              </span>
              <span className={styles.logoText}>CoVie</span>
            </Link>
            <p className={styles.brandTagline}>
              Nền tảng thương mại & dịch vụ kết nối cư dân hàng xóm — Mua sắm gần bạn, sống tiện nghi mỗi ngày.
            </p>

            {/* Zone Switcher Widget */}
            <div className={styles.locationWidget}>
              <div className={styles.locationInfo}>
                <MapPin size={18} weight="duotone" className={styles.locationPin} />
                <div>
                  <small>Khu đô thị phục vụ</small>
                  <strong>{location?.label ?? "Vinhomes Grand Park"}</strong>
                </div>
              </div>
              <button
                type="button"
                className={styles.changeLocationBtn}
                onClick={() => setChooserOpen(true)}
                aria-label="Đổi phân khu hoặc điểm nhận"
              >
                Đổi khu
              </button>
            </div>

            {/* Direct Contacts */}
            <div className={styles.contactList}>
              <a href="tel:19006868" className={styles.contactItem} title="Tổng đài hỗ trợ cư dân">
                <PhoneCall size={16} weight="duotone" />
                <span>Hotline: 1900 6868 (8:00 - 21:00)</span>
              </a>
              <a href="mailto:hotro@covie.vn" className={styles.contactItem} title="Email hỗ trợ">
                <EnvelopeSimple size={16} weight="duotone" />
                <span>hotro@covie.vn</span>
              </a>
            </div>

            {/* Social channels */}
            <div className={styles.socialGroup}>
              <a
                href="https://facebook.com"
                target="_blank"
                rel="noreferrer"
                className={styles.socialBtn}
                aria-label="Cộng đồng cư dân CoVie trên Facebook"
                title="Facebook Cộng đồng"
              >
                <FacebookLogo size={18} weight="fill" />
              </a>
              <a
                href="https://zalo.me"
                target="_blank"
                rel="noreferrer"
                className={styles.socialBtn}
                aria-label="Kênh Zalo Hỗ trợ cư dân"
                title="Zalo Hỗ trợ"
              >
                <ChatCircleDots size={18} weight="fill" />
              </a>
            </div>
          </div>

          {/* Column 2: Discover & Features */}
          <div className={styles.navCol}>
            <h3 className={styles.colTitle}>Khám phá & Tiện ích</h3>
            <ul className={styles.navList}>
              <li>
                <Link href="/needs">Nhu cầu cư dân (Cần mua / tìm việc)</Link>
              </li>
              <li>
                <Link href="/search?listingKind=RESALE">Chợ thanh lý đồ cũ (Resale)</Link>
              </li>
              <li>
                <Link href="/search?type=SERVICE">Dịch vụ sửa chữa & Tiện ích tại nhà</Link>
              </li>
              <li>
                <Link href="/search">Bách hóa & Đồ gia dụng nội khu</Link>
              </li>
              <li>
                <Link href="/cart">Giỏ hàng & Theo dõi đơn hàng</Link>
              </li>
            </ul>
          </div>

          {/* Column 3: Merchants & Community Partners */}
          <div className={styles.navCol}>
            <h3 className={styles.colTitle}>Dành cho Người bán</h3>
            <ul className={styles.navList}>
              <li>
                <Link href="/seller/register">Đăng ký mở gian hàng cư dân</Link>
              </li>
              <li>
                <Link href="/seller/dashboard">Cổng quản trị người bán</Link>
              </li>
              <li>
                <Link href="/search">Quy chế người bán văn minh</Link>
              </li>
              <li>
                <Link href="/needs">Nhận việc & Phục vụ nhu cầu hàng xóm</Link>
              </li>
              <li>
                <Link href="/privacy">Chính sách phí & Giao nhận nội khu</Link>
              </li>
            </ul>
          </div>

          {/* Column 4: Newsletter & Trust Assurance */}
          <div className={styles.newsletterCol}>
            <h3 className={styles.colTitle}>Bản tin Cư dân</h3>
            <p className={styles.newsletterSubtitle}>
              Nhận thông báo món ngon độc quyền, dịch vụ giảm giá và tin tức đời sống cư dân mỗi tuần.
            </p>

            <form onSubmit={handleNewsletterSubmit} className={styles.newsletterForm}>
              <div className={styles.inputWrapper}>
                <input
                  type="text"
                  value={newsletterInput}
                  onChange={(e) => {
                    setNewsletterInput(e.target.value);
                    if (newsletterStatus !== "idle") setNewsletterStatus("idle");
                  }}
                  placeholder="Email hoặc SĐT của bạn..."
                  className={styles.newsletterInput}
                  aria-label="Email hoặc SĐT nhận bản tin"
                />
                <button
                  type="submit"
                  className={styles.newsletterSubmitBtn}
                  aria-label="Đăng ký nhận bản tin"
                  title="Gửi đăng ký"
                >
                  <PaperPlaneRight size={18} weight="bold" />
                </button>
              </div>

              {newsletterStatus === "success" && (
                <div className={styles.newsletterSuccess} role="status">
                  <CheckCircle size={16} weight="fill" />
                  <span>{newsletterMessage}</span>
                </div>
              )}
              {newsletterStatus === "error" && (
                <div className={styles.newsletterError} role="alert">
                  {newsletterMessage}
                </div>
              )}
            </form>

            <div className={styles.safetyBadge}>
              <ShieldCheck size={18} weight="duotone" className={styles.safetyIcon} />
              <span>Giao dịch an toàn và bảo mật thông tin cư dân 100%</span>
            </div>
          </div>
        </div>

        {/* 2. Value Proposition & Trust Pillars (Đưa xuống phần dưới, cùng một khối liền mạch) */}
        <section className={styles.trustBar} aria-label="Cam kết niềm tin CoVie">
          <div className={styles.trustGrid}>
            {TRUST_PILLARS.map((pillar) => {
              const Icon = pillar.icon;
              return (
                <div key={pillar.title} className={styles.trustItem}>
                  <div className={styles.trustIconWrapper} aria-hidden="true">
                    <Icon size={24} weight="duotone" className={styles.trustIcon} />
                  </div>
                  <div className={styles.trustContent}>
                    <strong>{pillar.title}</strong>
                    <p>{pillar.desc}</p>
                  </div>
                </div>
              );
            })}
          </div>
        </section>

        {/* 3. Bottom Legal & Copyright Bar */}
        <div className={styles.bottomBar}>
          <div className={styles.copyrightText}>
            © {new Date().getFullYear()} CoVie Marketplace. Nền tảng kết nối cư dân văn minh, hiện đại.
          </div>
          <div className={styles.legalLinks}>
            <Link href="/privacy">Quy chế hoạt động</Link>
            <span aria-hidden="true">·</span>
            <Link href="/privacy">Chính sách bảo mật</Link>
            <span aria-hidden="true">·</span>
            <Link href="/privacy">Giải quyết khiếu nại</Link>
          </div>
        </div>
      </div>

      {/* Location Chooser Modal triggerable from Footer */}
      {chooserOpen && (
        <div className={styles.modalBackdrop} onClick={() => setChooserOpen(false)}>
          <div className={styles.modalDialog} onClick={(e) => e.stopPropagation()}>
            <LocationChooser
              onComplete={() => setChooserOpen(false)}
              onCancel={() => setChooserOpen(false)}
            />
          </div>
        </div>
      )}
    </footer>
  );
}
