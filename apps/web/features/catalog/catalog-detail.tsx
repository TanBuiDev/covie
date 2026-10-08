"use client";
import { AdSlot } from "@/features/ads/ad-slot";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { components } from "@covie/api-client";
import {
  ArrowLeft,
  Bicycle,
  ChatCircleDots,
  Heart,
  MapPin,
  Minus,
  Package,
  Plus,
  ShareNetwork,
  ShieldCheck,
  Star,
  Storefront,
  Tag,
  Wrench,
} from "@phosphor-icons/react";
import { addCartItem } from "@/features/cart/cart-api";
import { ReportButton } from "@/features/reports/report-button";
import { listCatalogReviews } from "@/features/reviews/review-api";
import { ReviewList } from "@/features/reviews/review-list";
import { CovieApiError } from "@/lib/api/api-error";
import styles from "./catalog-detail.module.css";
import resaleStyles from "./catalog-resale.module.css";

type CatalogDetailData = components["schemas"]["CatalogItemDetailResponse"];
type Rating = components["schemas"]["RatingSummary"];

function money(value?: components["schemas"]["Money"]) {
  if (value?.amount == null) return "Giá liên hệ";
  return `${new Intl.NumberFormat("vi-VN").format(value.amount)} ₫`;
}

function completion(item: CatalogDetailData) {
  const points = item.merchant?.reputation?.completionRateBasisPoints;
  return points == null ? null : `Hoàn thành ${Math.round(points / 100)}% đơn`;
}

export function CatalogDetail({ item }: { item: CatalogDetailData }) {
  const router = useRouter();
  const service = item.type === "SERVICE";
  const resale = item.listingKind === "RESALE";
  const singleUnit = service || resale;
  const initialVariant = useMemo(
    () =>
      item.variants?.find((variant) => variant.defaultVariant) ??
      item.variants?.find((variant) => variant.offer?.available) ??
      item.variants?.[0],
    [item.variants],
  );
  const [selectedVariantId, setSelectedVariantId] = useState(initialVariant?.id);
  const selectedVariant = useMemo(
    () => item.variants?.find((variant) => variant.id === selectedVariantId) ?? initialVariant,
    [initialVariant, item.variants, selectedVariantId],
  );
  const offer = selectedVariant?.offer;
  const [quantity, setQuantity] = useState(1);
  const [rating, setRating] = useState<Rating>();
  const [busy, setBusy] = useState(false);
  const [cartId, setCartId] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [unauthorized, setUnauthorized] = useState(false);
  const [activeMediaIndex, setActiveMediaIndex] = useState(0);
  const [wishlisted, setWishlisted] = useState(false);
  const [toastFeedback, setToastFeedback] = useState<string>();

  const available = Boolean(offer?.available && offer.locationVariantId);

  useEffect(() => {
    if (!item.id) return;
    let cancelled = false;
    listCatalogReviews(item.id)
      .then((result) => {
        if (!cancelled) setRating(result.rating);
      })
      .catch(() => undefined);
    return () => {
      cancelled = true;
    };
  }, [item.id]);

  function buyNow() {
    if (!available || !item.id || !item.resolvedLocation?.id || !offer?.locationVariantId || busy) return;
    const params = new URLSearchParams({
      source: "DIRECT",
      itemId: item.id,
      locationId: item.resolvedLocation.id,
      locationVariantId: offer.locationVariantId,
      quantity: String(singleUnit ? 1 : quantity),
    });
    router.push(`/checkout?${params}`);
  }

  async function add() {
    if (!available || !offer?.locationVariantId || busy) return;
    setBusy(true);
    setMessage(undefined);
    setUnauthorized(false);
    try {
      const cart = await addCartItem({
        locationVariantId: offer.locationVariantId,
        quantity: singleUnit ? 1 : quantity,
      });
      setCartId(cart.id);
      setMessage("Đã thêm vào giỏ hàng.");
    } catch (error) {
      if (error instanceof CovieApiError && error.status === 401) {
        setUnauthorized(true);
        setMessage(service ? "Đăng nhập để thêm dịch vụ vào giỏ." : "Đăng nhập để thêm sản phẩm vào giỏ.");
      } else {
        setMessage("Chưa thể thêm vào giỏ. Vui lòng thử lại.");
      }
    } finally {
      setBusy(false);
    }
  }

  function handleToggleWishlist() {
    setWishlisted((prev) => {
      const next = !prev;
      setToastFeedback(next ? "Đã lưu vào danh sách yêu thích" : "Đã bỏ khỏi danh sách yêu thích");
      setTimeout(() => setToastFeedback(undefined), 3000);
      return next;
    });
  }

  async function handleShare() {
    if (typeof window !== "undefined" && navigator?.clipboard) {
      try {
        await navigator.clipboard.writeText(window.location.href);
        setToastFeedback("Đã sao chép liên kết vào bộ nhớ tạm");
        setTimeout(() => setToastFeedback(undefined), 3000);
        return;
      } catch {
        // fallback
      }
    }
    setToastFeedback("Đã chuẩn bị liên kết chia sẻ");
    setTimeout(() => setToastFeedback(undefined), 3000);
  }

  const distance =
    item.resolvedLocation?.distanceMeters == null
      ? null
      : `${Math.round(item.resolvedLocation.distanceMeters)} m`;
  const location = [distance, item.resolvedLocation?.name, item.resolvedLocation?.addressText]
    .filter(Boolean)
    .join(" · ");
  const serviceLocation = [item.resolvedLocation?.name, item.resolvedLocation?.addressText]
    .filter(Boolean)
    .join(" · ");
  const ratingText =
    rating?.averageRating != null && rating.reviewCount
      ? `${rating.averageRating.toLocaleString("vi-VN")} · ${rating.reviewCount} đánh giá`
      : null;
  const reportType = service ? "Dịch vụ" : resale ? "Thanh lý" : "Sản phẩm";
  const reportMeta = [reportType, item.merchant?.name, item.resolvedLocation?.name].filter(Boolean).join(" · ");

  const activePhoto = item.media?.[activeMediaIndex] ?? item.media?.[0];
  const photoCountText = item.media?.length
    ? `Ảnh thực tế · ${activeMediaIndex + 1}/${item.media.length}`
    : "Chưa có ảnh thực tế";

  const media = resale ? (
    <div className={resaleStyles.resaleMedia}>
      <div className={resaleStyles.resalePhoto}>
        {activePhoto?.url && (
          <Image
            src={activePhoto.url}
            alt={item.name ?? "Ảnh thực tế sản phẩm thanh lý"}
            fill
            sizes="(min-width: 1024px) 520px, 326px"
            unoptimized
          />
        )}
      </div>
      <small>{photoCountText}</small>
      {item.media && item.media.length > 1 && (
        <div className={resaleStyles.thumbnailStrip} role="tablist" aria-label="Danh sách ảnh thực tế">
          {item.media.map((m, idx) => (
            <button
              key={m.id ?? idx}
              type="button"
              className={`${resaleStyles.thumbnailBtn} ${idx === activeMediaIndex ? resaleStyles.activeThumbnail : ""}`}
              onClick={() => setActiveMediaIndex(idx)}
              aria-label={`Xem ảnh thực tế ${idx + 1}`}
            >
              {m.url && <Image src={m.url} alt="" fill sizes="60px" unoptimized />}
            </button>
          ))}
        </div>
      )}
    </div>
  ) : !service ? (
    <div className={styles.galleryWrapper}>
      <div className={styles.media}>
        {activePhoto?.url ? (
          <Image
            src={activePhoto.url}
            alt={item.name ?? "Hình ảnh sản phẩm"}
            fill
            sizes="(min-width: 1024px) 520px, 390px"
            unoptimized
            priority
          />
        ) : (
          <Image
            src="/figma/product-media-placeholder.svg"
            alt=""
            fill
            sizes="(min-width: 1024px) 520px, 390px"
            priority
          />
        )}
        {item.media && item.media.length > 1 && (
          <span className={styles.photoBadge}>
            {activeMediaIndex + 1}/{item.media.length}
          </span>
        )}
      </div>
      {item.media && item.media.length > 1 && (
        <div className={styles.thumbnailStrip} role="tablist" aria-label="Danh sách hình ảnh">
          {item.media.map((m, idx) => (
            <button
              key={m.id ?? idx}
              type="button"
              className={`${styles.thumbnailBtn} ${idx === activeMediaIndex ? styles.activeThumbnail : ""}`}
              onClick={() => setActiveMediaIndex(idx)}
              aria-label={`Xem ảnh ${idx + 1}`}
            >
              {m.url && <Image src={m.url} alt="" fill sizes="64px" unoptimized />}
            </button>
          ))}
        </div>
      )}
    </div>
  ) : (
    <div className={styles.serviceMedia}>
      <div className={styles.serviceIconHero}>
        <Wrench size={36} weight="duotone" className={styles.serviceDuotoneIcon} />
      </div>
      <span>Dịch vụ</span>
      <strong>{item.name ?? "Dịch vụ CoVie"}</strong>
      <p className={styles.serviceHeroSubtitle}>Phục vụ tận tâm · Báo giá minh bạch · Đối tác uy tín</p>
    </div>
  );

  return (
    <article className={`${styles.detail} ${service ? styles.serviceDetail : resale ? resaleStyles.resaleDetail : ""}`}>
      <header className={styles.appBar}>
        <button aria-label="Quay lại" onClick={() => router.back()} className={styles.navButton}>
          <ArrowLeft size={22} weight="bold" />
        </button>
        <h1>{service ? "Chi tiết dịch vụ" : resale ? "Chi tiết thanh lý" : "Chi tiết sản phẩm"}</h1>
        <div className={styles.appBarActions}>
          <button
            type="button"
            aria-label="Lưu vào yêu thích"
            onClick={handleToggleWishlist}
            className={`${styles.iconButton} ${wishlisted ? styles.iconButtonActive : ""}`}
          >
            <Heart size={22} weight={wishlisted ? "fill" : "regular"} />
          </button>
          <button
            type="button"
            aria-label="Chia sẻ"
            onClick={handleShare}
            className={styles.iconButton}
          >
            <ShareNetwork size={22} weight="bold" />
          </button>
        </div>
      </header>

      {toastFeedback && (
        <div className={styles.toastNotification} role="status">
          {toastFeedback}
        </div>
      )}

      <div className={styles.desktopContainer}>
        <h1 className={styles.desktopTitle}>
          {service ? "Chi tiết dịch vụ" : resale ? "Chi tiết thanh lý" : "Chi tiết sản phẩm"}
        </h1>

        <div className={styles.desktopColumns}>
          <div className={styles.mediaColumn}>{media}</div>

          <div className={styles.body}>
            <section
              className={`${styles.listing} ${resale ? resaleStyles.resaleListing : ""}`}
              aria-labelledby="listing-title"
            >
              <div className={styles.listingHeaderRow}>
                <span className={styles.typeBadge}>
                  {service ? (
                    <>
                      <Wrench size={13} weight="duotone" /> Dịch vụ
                    </>
                  ) : resale ? (
                    <>
                      <Tag size={13} weight="duotone" /> Thanh lý
                    </>
                  ) : (
                    <>
                      <Package size={13} weight="duotone" /> Sản phẩm
                    </>
                  )}
                </span>
                <div className={styles.desktopQuickActions}>
                  <button
                    type="button"
                    aria-label="Lưu vào yêu thích"
                    onClick={handleToggleWishlist}
                    className={`${styles.quickActionBtn} ${wishlisted ? styles.quickActionActive : ""}`}
                  >
                    <Heart size={18} weight={wishlisted ? "fill" : "duotone"} />
                    <span>{wishlisted ? "Đã lưu" : "Lưu tin"}</span>
                  </button>
                  <button
                    type="button"
                    aria-label="Chia sẻ"
                    onClick={handleShare}
                    className={styles.quickActionBtn}
                  >
                    <ShareNetwork size={18} weight="duotone" />
                    <span>Chia sẻ</span>
                  </button>
                </div>
              </div>

              <h2 id="listing-title">{item.name ?? (service ? "Dịch vụ" : "Sản phẩm")}</h2>

              <div className={styles.priceContainer}>
                <strong className={styles.mainPrice}>{money(offer?.price)}</strong>
                {offer?.available && (
                  <span className={styles.inStockBadge}>
                    <ShieldCheck size={14} weight="fill" /> Còn hàng
                  </span>
                )}
              </div>

              <p className={styles.merchantMiniName}>{item.merchant?.name ?? "Người bán CoVie"}</p>

              {service
                ? serviceLocation && (
                    <small className={styles.locationMeta}>
                      <MapPin size={14} weight="duotone" /> Phục vụ {serviceLocation}
                    </small>
                  )
                : location && (
                    <small className={styles.locationMeta}>
                      <MapPin size={14} weight="duotone" /> {location}
                    </small>
                  )}

              {ratingText && (
                <div className={styles.rating}>
                  <Star size={16} weight="fill" className={styles.starIcon} />
                  <span>{ratingText}</span>
                </div>
              )}

              {item.id ? (
                <div className="trust-actions">
                  <ReportButton
                    targetType="CATALOG_ITEM"
                    targetId={item.id}
                    contextMerchantId={item.merchant?.id}
                    label="Báo cáo nội dung"
                    targetTitle={item.name ?? reportType}
                    targetMeta={reportMeta || undefined}
                  />
                </div>
              ) : null}
            </section>

            {service ? (
              <>
                <fieldset className={styles.serviceOptions} role="radiogroup">
                  <legend>Phân loại</legend>
                  {item.variants?.map((variant) => (
                    <label key={variant.id ?? variant.name} className={styles.serviceOption}>
                      <input
                        type="radio"
                        name="service-variant"
                        checked={variant.id === selectedVariant?.id}
                        disabled={!variant.offer?.available || busy}
                        onChange={() => setSelectedVariantId(variant.id)}
                      />
                      <span>{variant.name ?? "Tùy chọn dịch vụ"}</span>
                      <strong>{money(variant.offer?.price)}</strong>
                    </label>
                  ))}
                </fieldset>
                <section className={styles.serviceFulfillment} aria-label="Phương thức thực hiện">
                  <span aria-hidden="true" />
                  <div>
                    <strong>Phối hợp dịch vụ</strong>
                    <small>Trao đổi thời gian thực hiện sau khi đặt</small>
                  </div>
                </section>
                <section className={styles.serviceNote}>
                  <strong>Thời gian thực hiện</strong>
                  <p>
                    Thời gian thực hiện được hai bên phối hợp theo thông tin và trạng thái đơn hàng do hệ thống cung
                    cấp. V1 không có calendar booking.
                  </p>
                </section>
              </>
            ) : resale ? (
              <>
                <section className={resaleStyles.conditionCard}>
                  <strong>Tình trạng</strong>
                  <p>{item.description ?? "Người bán chưa cung cấp mô tả tình trạng."}</p>
                  <small>Thông tin do người bán cung cấp.</small>
                </section>
                <section className={resaleStyles.transactionCard}>
                  <strong>Vị trí giao dịch</strong>
                  <b>{item.resolvedLocation?.name ?? "Điểm bán CoVie"}</b>
                  {item.resolvedLocation?.addressText && <small>{item.resolvedLocation.addressText}</small>}
                </section>
                <section className={resaleStyles.resaleFulfillment}>
                  <strong>Giao nhận</strong>
                  <b>Xác nhận trong giỏ hàng</b>
                  <small>Hình thức nhận hàng được xác nhận theo điểm bán và địa chỉ của bạn.</small>
                </section>
              </>
            ) : (
              <>
                <section className={styles.merchant}>
                  <div className={styles.merchantProfile}>
                    <div className={styles.merchantAvatar} aria-hidden="true">
                      <Storefront size={22} weight="duotone" />
                    </div>
                    <div>
                      <div className={styles.merchantNameRow}>
                        <strong>{item.merchant?.name ?? "Người bán CoVie"}</strong>
                        <ShieldCheck size={16} weight="fill" className={styles.merchantVerifiedIcon} />
                      </div>
                      {completion(item) && <small>{completion(item)}</small>}
                    </div>
                  </div>
                  <div className={styles.merchantActions}>
                    {item.merchant?.id ? (
                      <Link href={`/merchants/${item.merchant.id}`} className={styles.viewShopBtn}>
                        <Storefront size={16} weight="duotone" aria-hidden="true" />
                        <span>Xem shop</span>
                      </Link>
                    ) : (
                      <button type="button" disabled className={styles.viewShopBtn}>
                        <Storefront size={16} weight="duotone" aria-hidden="true" />
                        <span>Xem shop</span>
                      </button>
                    )}
                    <button
                      type="button"
                      className={styles.chatButton}
                      onClick={() => setToastFeedback("Tính năng nhắn tin trực tiếp với shop đang kết nối")}
                      aria-label="Nhắn tin với người bán"
                    >
                      <ChatCircleDots size={16} weight="duotone" aria-hidden="true" />
                      <span>Nhắn tin</span>
                    </button>
                  </div>
                </section>

                <section className={styles.shipping}>
                  <div className={styles.shippingHeader}>
                    <Bicycle size={18} weight="duotone" className={styles.shippingIcon} />
                    <strong>Giao nhận</strong>
                  </div>
                  <b>Xác nhận trong giỏ hàng</b>
                  <small>Phí và thời gian giao do hệ thống xác nhận theo địa chỉ nhận.</small>
                </section>

                <section className={styles.quantity}>
                  <strong>Số lượng</strong>
                  <div className={styles.stepperContainer}>
                    <button
                      aria-label="Giảm số lượng"
                      disabled={quantity <= 1 || busy}
                      onClick={() => setQuantity((value) => Math.max(1, value - 1))}
                    >
                      <Minus size={16} weight="bold" />
                    </button>
                    <output aria-label="Số lượng">{quantity}</output>
                    <button
                      aria-label="Tăng số lượng"
                      disabled={!available || busy}
                      onClick={() => setQuantity((value) => value + 1)}
                    >
                      <Plus size={16} weight="bold" />
                    </button>
                  </div>
                </section>

                {item.description && (
                  <section className={styles.description}>
                    <strong>Mô tả</strong>
                    <p>{item.description}</p>
                  </section>
                )}
              </>
            )}

            {!available && <p className={styles.unavailable}>Hiện không khả dụng tại điểm bán này</p>}

            {message && (
              <div className={styles.feedback} role="status">
                <span>{message}</span>
                {unauthorized && <Link href="/login">Đăng nhập</Link>}
                {cartId && <button onClick={() => router.push(`/cart/${cartId}`)}>Xem giỏ hàng</button>}
              </div>
            )}

            <footer className={styles.actions}>
              <button
                type="button"
                className={styles.addToCartBtn}
                disabled={!available || busy}
                onClick={() => void add()}
              >
                {busy ? "Đang thêm…" : "Thêm vào giỏ"}
              </button>
              <button
                type="button"
                className={styles.buyNowBtn}
                disabled={!available || busy || !item.id || !item.resolvedLocation?.id}
                onClick={buyNow}
              >
                {service ? "Đặt dịch vụ" : "Mua ngay"}
              </button>
            </footer>
          </div>
        </div>

        <AdSlot placement="ITEM_DETAIL" />
        {item.id ? (
          <div className={styles.reviewsSection}>
            <ReviewList targetId={item.id} targetType="CATALOG_ITEM" contextMerchantId={item.merchant?.id} />
          </div>
        ) : null}
      </div>
    </article>
  );
}
