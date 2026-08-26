import Image from "next/image";
import Link from "next/link";
import type { components } from "@covie/api-client";
import { CoVieIcon } from "@/components/ui/covie-icon";
import styles from "./merchant-profile.module.css";

type Merchant = components["schemas"]["PublicMerchantResponse"];
type Reviews = components["schemas"]["ReviewPageResponse"];
type Contact = components["schemas"]["PublicMerchantContactResponse"];

function initials(name?: string) {
  const words = name?.trim().split(/\s+/).filter(Boolean) ?? [];
  return words.slice(-2).map(word => word[0]?.toLocaleUpperCase("vi-VN")).join("") || "CV";
}

function contactAction(contact: Contact) {
  const value = contact.value?.trim();
  if (!value) return null;
  if (contact.type === "PHONE") {
    const phone = value.replace(/[^\d+]/g, "");
    return phone ? { href: `tel:${phone}`, label: "Gọi điện" } : null;
  }
  if (contact.type !== "ZALO_LINK" && contact.type !== "MESSENGER_LINK") return null;
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" && url.protocol !== "http:") return null;
    return { href: url.toString(), label: contact.type === "ZALO_LINK" ? "Mở Zalo" : "Messenger" };
  } catch {
    return null;
  }
}

function distance(value?: number) {
  if (value == null) return null;
  if (value < 1000) return `${Math.round(value)} m`;
  return `${(value / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km`;
}

export function MerchantProfile({ merchant, reviews }: { merchant: Merchant; reviews?: Reviews }) {
  const completionPoints = merchant.reputation?.completionRateBasisPoints;
  const completionRate = completionPoints == null ? null : Math.round(completionPoints / 100);
  const rating = reviews?.rating;
  const ratingText = rating?.averageRating != null && rating.reviewCount
    ? `${rating.averageRating.toLocaleString("vi-VN")} · ${rating.reviewCount} đánh giá`
    : null;
  const primaryLocation = merchant.locations?.[0];
  const actions = merchant.contacts?.map(contactAction).filter((action): action is NonNullable<typeof action> => Boolean(action)) ?? [];

  return <article className={styles.screen}>
    <header className={styles.appBar}>
      <Link href="/search" aria-label="Quay lại"><CoVieIcon name="back" /></Link>
      <h1>Cửa hàng</h1>
    </header>

    <div className={styles.body}>
      <section className={styles.hero} aria-labelledby="merchant-name">
        {merchant.cover?.url && <Image src={merchant.cover.url} alt="" fill sizes="358px" unoptimized />}
        <div><small>SHOP PUBLIC PROFILE</small><h2 id="merchant-name">{merchant.name ?? "Cửa hàng CoVie"}</h2>{merchant.description && <p>{merchant.description}</p>}</div>
      </section>

      <section className={styles.identity} aria-label="Thông tin cửa hàng">
        <div className={styles.avatar}>{merchant.logo?.url ? <Image src={merchant.logo.url} alt="" fill sizes="48px" unoptimized /> : initials(merchant.name)}</div>
        <div><strong>{merchant.name ?? "Cửa hàng CoVie"}</strong>{primaryLocation?.name && <small>{primaryLocation.name}</small>}{distance(primaryLocation?.distanceMeters) && <small>Cách bạn {distance(primaryLocation?.distanceMeters)}</small>}{merchant.reputation?.completedOrders != null && <b>Đã hoàn thành {merchant.reputation.completedOrders} đơn</b>}</div>
      </section>

      <section className={styles.trust}>
        <h3>Uy tín &amp; đánh giá</h3>
        <div className={styles.reputation}><strong>Uy tín giao dịch</strong>{completionRate == null ? <p>Chưa có đủ dữ liệu uy tín công khai.</p> : <><div><span>Tỷ lệ hoàn thành</span><b>{completionRate}%</b></div><progress max="100" value={completionRate} aria-label="Tỷ lệ hoàn thành" /></>}</div>
        <div className={styles.reviews}><strong>Đánh giá từ người mua</strong>{ratingText ? <p><CoVieIcon name="rating-star" />{ratingText}</p> : <small>Chưa có đánh giá công khai.</small>}</div>
      </section>

      {merchant.contacts?.length ? <section className={styles.contacts}>
        <h3>Liên hệ công khai</h3>
        <p>{merchant.contacts.map(contact => contact.type === "PHONE" ? contact.value : contact.type?.replace("_LINK", "")).filter(Boolean).join(" · ")}</p>
        {actions.length > 0 && <div>{actions.map(action => <a key={`${action.label}-${action.href}`} href={action.href} target={action.href.startsWith("http") ? "_blank" : undefined} rel={action.href.startsWith("http") ? "noreferrer" : undefined}>{action.label}</a>)}</div>}
      </section> : null}

      <section className={styles.locations}>
        <h3>Điểm phục vụ công khai</h3>
        {merchant.locations?.length ? merchant.locations.map(location => <div className={styles.locationCard} key={location.id ?? location.name}>
          <strong>{location.name ?? "Điểm phục vụ"}</strong>
          {location.addressText && <p>{location.addressText}</p>}
          {distance(location.distanceMeters) && <small>Cách bạn {distance(location.distanceMeters)}</small>}
        </div>) : <p className={styles.empty}>Chưa có điểm phục vụ công khai.</p>}
      </section>

      <section className={styles.catalog}>
        <h3>Danh mục công khai</h3>
        <div className={styles.empty}>Chưa thể tải danh mục của cửa hàng.</div>
      </section>
    </div>
  </article>;
}
