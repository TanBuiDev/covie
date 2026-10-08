"use client";

import Image from "next/image";
import Link from "next/link";
import { useMemo, useState } from "react";
import type { components } from "@covie/api-client";
import {
  ArrowLeft,
  ChatCircleDots,
  MapPin,
  Package,
  PhoneCall,
  ShareNetwork,
  ShieldCheck,
  Star,
} from "@phosphor-icons/react";
import { ReportButton } from "@/features/reports/report-button";
import { ReviewList } from "@/features/reviews/review-list";
import { listPublicMerchantCatalog } from "./merchant-api";
import styles from "./merchant-profile.module.css";

type Merchant = components["schemas"]["PublicMerchantResponse"];
type Reviews = components["schemas"]["ReviewPageResponse"];
type Contact = components["schemas"]["PublicMerchantContactResponse"];
type Catalog = components["schemas"]["PublicMerchantCatalogResponse"];
type CatalogItem = components["schemas"]["PublicMerchantCatalogItemResponse"];
type ProfilePresentation = "SHOP" | "PROVIDER" | "NEUTRAL";

function initials(name?: string) {
  const words = name?.trim().split(/\s+/).filter(Boolean) ?? [];
  return words.slice(-2).map((word) => word[0]?.toLocaleUpperCase("vi-VN")).join("") || "CV";
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

function presentationFor(merchant: Merchant): ProfilePresentation {
  const types = new Set(merchant.capabilities?.catalogTypes ?? []);
  if (types.size === 1 && types.has("PRODUCT")) return "SHOP";
  if (types.size === 1 && types.has("SERVICE")) return "PROVIDER";
  return "NEUTRAL";
}

function itemTypeLabel(item: CatalogItem) {
  if (item.listingKind === "RESALE") return "Đồ đã qua sử dụng";
  return item.type === "SERVICE" ? "Dịch vụ" : "Sản phẩm";
}

function formatMoney(amount?: number, currency?: string) {
  if (amount == null || !currency) return null;
  try {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${amount.toLocaleString("vi-VN")} ${currency}`;
  }
}

function catalogHref(merchantId: string | undefined, cursor: string | undefined) {
  if (!merchantId) return "/search";
  return cursor ? `/merchants/${merchantId}?catalogCursor=${encodeURIComponent(cursor)}` : `/merchants/${merchantId}`;
}

export function MerchantProfile({
  merchant,
  reviews,
  catalog,
  catalogError = false,
  catalogCursor,
}: {
  merchant: Merchant;
  reviews?: Reviews;
  catalog?: Catalog;
  catalogError?: boolean;
  catalogCursor?: string;
}) {
  const [activeTab, setActiveTab] = useState<"ALL" | "PRODUCT" | "SERVICE" | "RESALE">("ALL");
  const [toastMsg, setToastMsg] = useState<string>();

  // State management for seamless catalog pagination (append new items, keep existing)
  const initialItems = useMemo(
    () =>
      (catalog?.page?.items ?? []).filter(
        (item): item is CatalogItem & { id: string } => Boolean(item.id),
      ),
    [catalog?.page?.items],
  );
  const [appendedItems, setAppendedItems] = useState<Array<CatalogItem & { id: string }>>([]);
  const [overrideCursor, setOverrideCursor] = useState<string | null | undefined>();
  const [overrideHasMore, setOverrideHasMore] = useState<boolean | undefined>();
  const [loadingMore, setLoadingMore] = useState(false);

  // Combine initialItems with appendedItems seamlessly
  const loadedItems = useMemo(() => {
    const existingIds = new Set(initialItems.map((i) => i.id));
    const filteredAppended = appendedItems.filter((i) => !existingIds.has(i.id));
    return [...initialItems, ...filteredAppended];
  }, [initialItems, appendedItems]);

  const effectiveNextCursor = overrideCursor !== undefined ? overrideCursor : catalog?.page?.nextCursor;
  const effectiveHasMore =
    overrideHasMore !== undefined
      ? overrideHasMore
      : Boolean(catalog?.page?.hasMore && catalog?.page?.nextCursor);

  const presentation = presentationFor(merchant);
  const provider = presentation === "PROVIDER";
  const surfaceLabel = provider ? "Nhà cung cấp" : presentation === "SHOP" ? "Cửa hàng" : "Đơn vị cung cấp";
  const fallbackName = provider
    ? "Nhà cung cấp CoVie"
    : presentation === "SHOP"
      ? "Cửa hàng CoVie"
      : "Đơn vị cung cấp CoVie";
  const completionPoints = merchant.reputation?.completionRateBasisPoints;
  const completionRate = completionPoints == null ? null : Math.round(completionPoints / 100);
  const rating = reviews?.rating;
  const ratingText =
    rating?.averageRating != null && rating.reviewCount
      ? `${rating.averageRating.toLocaleString("vi-VN")} · ${rating.reviewCount} đánh giá`
      : null;
  const primaryLocation = merchant.locations?.[0];
  const actions =
    merchant.contacts
      ?.map(contactAction)
      .filter((action): action is NonNullable<typeof action> => Boolean(action)) ?? [];
  const reportMeta = [surfaceLabel, primaryLocation?.name, primaryLocation?.addressText].filter(Boolean).join(" · ");

  const hasResale = loadedItems.some((i) => i.listingKind === "RESALE");
  const hasService = loadedItems.some((i) => i.type === "SERVICE");
  const hasProduct = loadedItems.some((i) => i.type === "PRODUCT" && i.listingKind !== "RESALE");
  const showTabs = loadedItems.length > 3 && [hasProduct, hasService, hasResale].filter(Boolean).length > 1;

  const filteredItems = useMemo(() => {
    if (activeTab === "ALL") return loadedItems;
    if (activeTab === "RESALE") return loadedItems.filter((i) => i.listingKind === "RESALE");
    if (activeTab === "SERVICE") return loadedItems.filter((i) => i.type === "SERVICE" && i.listingKind !== "RESALE");
    if (activeTab === "PRODUCT") return loadedItems.filter((i) => i.type === "PRODUCT" && i.listingKind !== "RESALE");
    return loadedItems;
  }, [loadedItems, activeTab]);

  async function handleLoadMore(e: React.MouseEvent) {
    // Prevent full page navigation reload so previous items are retained
    e.preventDefault();
    if (!merchant.id || !effectiveNextCursor || loadingMore) return;
    setLoadingMore(true);
    try {
      const res = await listPublicMerchantCatalog(merchant.id, effectiveNextCursor);
      const newItems = (res.page?.items ?? []).filter(
        (item): item is CatalogItem & { id: string } => Boolean(item.id),
      );
      setAppendedItems((prev) => [...prev, ...newItems]);
      setOverrideCursor(res.page?.nextCursor);
      setOverrideHasMore(Boolean(res.page?.hasMore && res.page?.nextCursor));
    } catch {
      // Fallback to URL navigation if client fetch fails
      window.location.href = catalogHref(merchant.id, effectiveNextCursor);
    } finally {
      setLoadingMore(false);
    }
  }

  async function handleShare() {
    if (typeof window !== "undefined" && navigator?.clipboard) {
      try {
        await navigator.clipboard.writeText(window.location.href);
        setToastMsg("Đã sao chép liên kết gian hàng vào bộ nhớ tạm");
        setTimeout(() => setToastMsg(undefined), 3000);
        return;
      } catch {
        // fallback
      }
    }
    setToastMsg("Đã chuẩn bị liên kết chia sẻ");
    setTimeout(() => setToastMsg(undefined), 3000);
  }

  return (
    <article className={styles.screen}>
      <header className={styles.appBar}>
        <Link href="/search" aria-label="Quay lại" className={styles.backBtn}>
          <ArrowLeft size={22} weight="bold" />
        </Link>
        <h1>{surfaceLabel}</h1>
        <button
          type="button"
          aria-label="Chia sẻ gian hàng"
          onClick={handleShare}
          className={styles.headerShareBtn}
          title="Chia sẻ"
        >
          <ShareNetwork size={20} weight="bold" />
        </button>
      </header>

      {toastMsg && (
        <div className={styles.toastNotification} role="status">
          {toastMsg}
        </div>
      )}

      <div className={styles.body}>
        <h1 className={styles.desktopTitle}>{surfaceLabel}</h1>

        {/* Left Column: Merchant Profile Summary & Trust (Sticky sidebar on Desktop) */}
        <div className={styles.desktopSummary}>
          <section className={styles.hero} aria-labelledby="merchant-name">
            {merchant.cover?.url ? (
              <Image
                src={merchant.cover.url}
                alt=""
                fill
                sizes="(min-width: 1024px) 420px, 358px"
                unoptimized
                priority
              />
            ) : (
              <div className={styles.heroPattern} aria-hidden="true" />
            )}
            <div className={styles.heroContent}>
              <small>
                {provider
                  ? "PROVIDER PUBLIC PROFILE"
                  : presentation === "SHOP"
                    ? "SHOP PUBLIC PROFILE"
                    : "PUBLIC MERCHANT PROFILE"}
              </small>
              <div className={styles.heroTitleRow}>
                <h2 id="merchant-name">{merchant.name ?? fallbackName}</h2>
                <span title="Xác thực CoVie">
                  <ShieldCheck size={20} weight="fill" className={styles.verifiedIcon} />
                </span>
              </div>
              {merchant.description && <p>{merchant.description}</p>}
            </div>
          </section>

          <section className={styles.identity} aria-label={`Thông tin ${surfaceLabel.toLocaleLowerCase("vi-VN")}`}>
            <div className={styles.avatar}>
              {merchant.logo?.url ? (
                <Image src={merchant.logo.url} alt="" fill sizes="56px" unoptimized />
              ) : (
                initials(merchant.name)
              )}
            </div>
            <div className={styles.identityBody} aria-label={`Chi tiết ${surfaceLabel.toLocaleLowerCase("vi-VN")}`}>
              <strong>{merchant.name ?? fallbackName}</strong>
              {primaryLocation?.name && <small>{primaryLocation.name}</small>}
              {distance(primaryLocation?.distanceMeters) && (
                <small>Cách bạn {distance(primaryLocation?.distanceMeters)}</small>
              )}
              {merchant.reputation?.completedOrders != null && (
                <b>Đã hoàn thành {merchant.reputation.completedOrders} đơn</b>
              )}
            </div>
            {merchant.id ? (
              <ReportButton
                targetType="MERCHANT"
                targetId={merchant.id}
                contextMerchantId={merchant.id}
                label={`Báo cáo ${surfaceLabel.toLocaleLowerCase("vi-VN")}`}
                targetTitle={merchant.name ?? fallbackName}
                targetMeta={reportMeta || undefined}
              />
            ) : null}
          </section>

          <section className={styles.trust}>
            <h3>Uy tín &amp; đánh giá</h3>
            <div className={styles.reputation}>
              <strong>Uy tín giao dịch</strong>
              {completionRate == null ? (
                <p>Chưa có đủ dữ liệu uy tín công khai.</p>
              ) : (
                <>
                  <div>
                    <span>Tỷ lệ hoàn thành</span>
                    <b>{completionRate}%</b>
                  </div>
                  <progress max="100" value={completionRate} aria-label="Tỷ lệ hoàn thành" />
                </>
              )}
            </div>
            <div className={styles.reviews}>
              <strong>Đánh giá từ người mua</strong>
              {ratingText ? (
                <p>
                  <Star size={16} weight="fill" className={styles.starIcon} aria-hidden="true" />
                  <span>{ratingText}</span>
                </p>
              ) : (
                <small>Chưa có đánh giá công khai.</small>
              )}
            </div>
          </section>

          {merchant.contacts?.length ? (
            <section className={styles.contacts}>
              <h3>Liên hệ công khai</h3>
              <p>
                {merchant.contacts
                  .map((contact) => (contact.type === "PHONE" ? contact.value : contact.type?.replace("_LINK", "")))
                  .filter(Boolean)
                  .join(" · ")}
              </p>
              {actions.length > 0 && (
                <div className={styles.contactActions}>
                  {actions.map((action) => (
                    <a
                      key={`${action.label}-${action.href}`}
                      href={action.href}
                      target={action.href.startsWith("http") ? "_blank" : undefined}
                      rel={action.href.startsWith("http") ? "noreferrer" : undefined}
                      className={styles.actionBtn}
                    >
                      {action.label === "Gọi điện" ? (
                        <PhoneCall size={16} weight="duotone" aria-hidden="true" />
                      ) : (
                        <ChatCircleDots size={16} weight="duotone" aria-hidden="true" />
                      )}
                      <span>{action.label}</span>
                    </a>
                  ))}
                  <button
                    type="button"
                    onClick={handleShare}
                    className={styles.shareShopBtn}
                    aria-label="Chia sẻ thông tin gian hàng"
                    title="Sao chép link"
                  >
                    <ShareNetwork size={16} weight="duotone" aria-hidden="true" />
                    <span>Chia sẻ</span>
                  </button>
                </div>
              )}
            </section>
          ) : null}
        </div>

        {/* Right Column: Public Content, Catalog Items AND Reviews in proper sequence */}
        <div className={styles.desktopPublicContent}>
          <section className={styles.locations}>
            <h3>Điểm phục vụ công khai</h3>
            {merchant.locations?.length ? (
              <div className={styles.locationsGrid}>
                {merchant.locations.map((location) => (
                  <div className={styles.locationCard} key={location.id ?? location.name}>
                    <div className={styles.locationHeader}>
                      <MapPin size={18} weight="duotone" className={styles.locationPin} />
                      <strong>{location.name ?? "Điểm phục vụ"}</strong>
                    </div>
                    {location.addressText && <p>{location.addressText}</p>}
                    {distance(location.distanceMeters) && <small>Cách bạn {distance(location.distanceMeters)}</small>}
                  </div>
                ))}
              </div>
            ) : (
              <p className={styles.empty}>Chưa có điểm phục vụ công khai.</p>
            )}
          </section>

          <section className={styles.catalog}>
            <div className={styles.catalogHeader}>
              <h3>{provider ? "Dịch vụ công khai" : "Danh mục công khai"}</h3>
              {showTabs && (
                <div className={styles.tabs} role="tablist" aria-label="Lọc mặt hàng">
                  <button
                    type="button"
                    role="tab"
                    aria-selected={activeTab === "ALL"}
                    className={`${styles.tabBtn} ${activeTab === "ALL" ? styles.activeTab : ""}`}
                    onClick={() => setActiveTab("ALL")}
                  >
                    Tất cả ({loadedItems.length})
                  </button>
                  {hasProduct && (
                    <button
                      type="button"
                      role="tab"
                      aria-selected={activeTab === "PRODUCT"}
                      className={`${styles.tabBtn} ${activeTab === "PRODUCT" ? styles.activeTab : ""}`}
                      onClick={() => setActiveTab("PRODUCT")}
                    >
                      Sản phẩm
                    </button>
                  )}
                  {hasService && (
                    <button
                      type="button"
                      role="tab"
                      aria-selected={activeTab === "SERVICE"}
                      className={`${styles.tabBtn} ${activeTab === "SERVICE" ? styles.activeTab : ""}`}
                      onClick={() => setActiveTab("SERVICE")}
                    >
                      Dịch vụ
                    </button>
                  )}
                  {hasResale && (
                    <button
                      type="button"
                      role="tab"
                      aria-selected={activeTab === "RESALE"}
                      className={`${styles.tabBtn} ${activeTab === "RESALE" ? styles.activeTab : ""}`}
                      onClick={() => setActiveTab("RESALE")}
                    >
                      Thanh lý
                    </button>
                  )}
                </div>
              )}
            </div>

            {catalogError ? (
              <div className={styles.catalogState}>
                <p>Chưa thể tải danh mục công khai.</p>
                <Link href={catalogHref(merchant.id, catalogCursor)}>Thử lại danh mục</Link>
              </div>
            ) : loadedItems.length ? (
              <>
                <div className={styles.catalogGrid}>
                  {filteredItems.map((item) => (
                    <Link className={styles.catalogCard} href={`/items/${item.id}`} key={item.id}>
                      <div className={styles.catalogMedia}>
                        {item.primaryMedia?.url ? (
                          <Image
                            src={item.primaryMedia.url}
                            alt=""
                            fill
                            sizes="(min-width: 1024px) 240px, 175px"
                            unoptimized
                          />
                        ) : (
                          <div className={styles.catalogPlaceholder}>
                            <Package size={32} weight="duotone" />
                          </div>
                        )}
                        <span>{itemTypeLabel(item)}</span>
                      </div>
                      <div className={styles.catalogCardBody}>
                        <strong>{item.name ?? "Mặt hàng CoVie"}</strong>
                        {formatMoney(item.priceFrom?.amount, item.priceFrom?.currency) ? (
                          <b>{formatMoney(item.priceFrom?.amount, item.priceFrom?.currency)}</b>
                        ) : null}
                        <small>{merchant.name ?? fallbackName}</small>
                      </div>
                    </Link>
                  ))}
                </div>

                {effectiveHasMore && effectiveNextCursor ? (
                  <div className={styles.moreWrapper}>
                    <a
                      className={styles.more}
                      href={catalogHref(merchant.id, effectiveNextCursor)}
                      onClick={handleLoadMore}
                    >
                      {loadingMore ? "Đang tải thêm…" : "Xem thêm"}
                    </a>
                  </div>
                ) : null}
              </>
            ) : (
              <p className={styles.empty}>{provider ? "Chưa có dịch vụ công khai." : "Chưa có mặt hàng công khai."}</p>
            )}
          </section>

          {/* Customer Reviews Section placed inside Column 2 (Right) so Sticky Column 1 NEVER overlaps it */}
          {merchant.id ? (
            <div className={styles.reviewsSection}>
              <ReviewList
                targetId={merchant.id}
                targetType="MERCHANT"
                contextMerchantId={merchant.id}
                initialPage={reviews}
              />
            </div>
          ) : null}
        </div>
      </div>
    </article>
  );
}
