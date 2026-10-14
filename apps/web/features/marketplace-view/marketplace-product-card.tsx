"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { BookmarkSimple, ChatCircle, Heart, MapPin, Package, ShareNetwork, Storefront, Tag, Wrench } from "@phosphor-icons/react";
import type { components } from "@covie/api-client";
import type { MarketplaceViewMode } from "./marketplace-view-preference";
import styles from "./marketplace-view.module.css";

type Item = components["schemas"]["CatalogItemSummaryResponse"];

function formatMoney(item: Item): string {
  if (item.priceFrom?.amount == null) return "Giá liên hệ";
  const amount = new Intl.NumberFormat("vi-VN").format(item.priceFrom.amount);
  return amount + " " + (item.priceFrom.currency === "VND" || !item.priceFrom.currency ? "₫" : item.priceFrom.currency);
}

function formatDistance(meters?: number): string | null {
  if (meters == null || !Number.isFinite(meters) || meters < 0) return null;
  return meters < 1000 ? Math.round(meters) + " m" : (meters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 }) + " km";
}

export function MarketplaceProductCard({
  item,
  mode,
  href,
}: {
  item: Item;
  mode: MarketplaceViewMode;
  href?: string;
}) {
  const [shareMessage, setShareMessage] = useState<string>();
  const service = item.type === "SERVICE";
  const resale = item.listingKind === "RESALE";
  const kind = service ? "Dịch vụ" : resale ? "Thanh lý" : "Sản phẩm";
  const url = href ?? (item.id ? "/items/" + encodeURIComponent(item.id) : "/search");
  const name = item.name ?? kind;
  const distance = formatDistance(item.resolvedLocation?.distanceMeters);
  const merchantName = item.merchant?.name ?? "Người bán CoVie";

  async function share() {
    const canonical = new URL(url, window.location.origin).toString();
    setShareMessage(undefined);
    try {
      if (typeof navigator.share === "function") {
        await navigator.share({ title: name, url: canonical });
        return;
      }
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(canonical);
        setShareMessage("Đã sao chép liên kết sản phẩm.");
        return;
      }
    } catch {
      // Rejected share or clipboard permission: do not claim success.
    }
    setShareMessage("Không thể chia sẻ tự động. Mở trang chi tiết để sao chép đường dẫn.");
  }

  return (
    <article className={[styles.card, mode === "social" ? styles.social : styles.catalog].join(" ")}>
      {mode === "social" && (
        <header className={styles.author}>
          <Storefront size={20} aria-hidden="true" />
          <span className={styles.authorName}>{merchantName}</span>
          {distance && <small>{distance}</small>}
        </header>
      )}
      <Link className={styles.media} href={url} aria-label={"Xem " + name}>
        {item.primaryMedia?.url ? (
          <Image
            src={item.primaryMedia.url}
            alt={name}
            fill
            sizes={mode === "social" ? "(min-width: 1024px) 480px, 100vw" : "(min-width: 1024px) 240px, 50vw"}
            unoptimized
          />
        ) : (
          <span className={styles.mediaFallback}>
            {service ? <Wrench size={32} aria-hidden="true" /> : resale ? <Tag size={32} aria-hidden="true" /> : <Package size={32} aria-hidden="true" />}
            <span>Chưa có ảnh</span>
          </span>
        )}
        <span className={styles.kind}>{kind}</span>
      </Link>
      <div className={styles.body}>
        <h3 className={styles.title}><Link href={url}>{name}</Link></h3>
        <strong className={styles.price}>{formatMoney(item)}</strong>
        {mode === "catalog" && <span className={styles.merchant}>{merchantName}</span>}
        <p className={styles.location}>
          <MapPin size={15} aria-hidden="true" />
          <span>{item.resolvedLocation?.name ?? "Chưa xác định vị trí"}{distance ? " · " + distance : ""}</span>
        </p>
        {mode === "social" && (
          <div className={styles.interactions} aria-label="Tương tác cộng đồng sắp ra mắt">
            <button type="button" disabled title="Chức năng thích sẽ được triển khai sau" aria-label="Thích — chưa khả dụng">
              <Heart size={20} aria-hidden="true" />
            </button>
            <button type="button" disabled title="Bình luận sẽ được triển khai sau" aria-label="Bình luận — chưa khả dụng">
              <ChatCircle size={20} aria-hidden="true" />
            </button>
            <button type="button" onClick={() => void share()} aria-label={"Chia sẻ " + name}>
              <ShareNetwork size={20} aria-hidden="true" />
            </button>
            <button type="button" disabled title="Lưu sẽ được triển khai sau" aria-label="Lưu — chưa khả dụng">
              <BookmarkSimple size={20} aria-hidden="true" />
            </button>
          </div>
        )}
        {mode === "social" && <small className={styles.comingSoon}>Like, bình luận và lưu sẽ được bổ sung sau.</small>}
        {shareMessage && <p className={styles.shareMessage} role="status">{shareMessage}</p>}
        <Link href={url} className={styles.cta}>
          {service ? "Xem dịch vụ" : resale ? "Xem món đồ" : "Xem sản phẩm"}
        </Link>
      </div>
    </article>
  );
}
