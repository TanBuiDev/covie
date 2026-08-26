"use client";

import Image from "next/image";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import type { components } from "@covie/api-client";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { addCartItem } from "@/features/cart/cart-api";
import { listCatalogReviews } from "@/features/reviews/review-api";
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
  const initialVariant = useMemo(() => item.variants?.find(v => v.defaultVariant) ?? item.variants?.find(v => v.offer?.available) ?? item.variants?.[0], [item.variants]);
  const [selectedVariantId, setSelectedVariantId] = useState(initialVariant?.id);
  const selectedVariant = useMemo(() => item.variants?.find(v => v.id === selectedVariantId) ?? initialVariant, [initialVariant, item.variants, selectedVariantId]);
  const offer = selectedVariant?.offer;
  const [quantity, setQuantity] = useState(1);
  const [rating, setRating] = useState<Rating>();
  const [busy, setBusy] = useState(false);
  const [cartId, setCartId] = useState<string>();
  const [message, setMessage] = useState<string>();
  const [unauthorized, setUnauthorized] = useState(false);
  const available = Boolean(offer?.available && offer.locationVariantId);

  useEffect(() => {
    if (!item.id) return; let cancelled = false;
    listCatalogReviews(item.id).then(result => { if (!cancelled) setRating(result.rating); }).catch(() => undefined);
    return () => { cancelled = true; };
  }, [item.id]);

  async function add(goToCart = false) {
    if (!available || !offer?.locationVariantId || busy) return;
    setBusy(true); setMessage(undefined); setUnauthorized(false);
    try {
      const cart = await addCartItem({ locationVariantId: offer.locationVariantId, quantity: singleUnit ? 1 : quantity });
      if (goToCart && cart.id) router.push(`/cart/${cart.id}`);
      else { setCartId(cart.id); setMessage("Đã thêm vào giỏ hàng."); }
    } catch (error) {
      if (error instanceof CovieApiError && error.status === 401) { setUnauthorized(true); setMessage(service ? "Đăng nhập để thêm dịch vụ vào giỏ." : "Đăng nhập để thêm sản phẩm vào giỏ."); }
      else setMessage("Chưa thể thêm vào giỏ. Vui lòng thử lại.");
    } finally { setBusy(false); }
  }

  const distance = item.resolvedLocation?.distanceMeters == null ? null : `${Math.round(item.resolvedLocation.distanceMeters)} m`;
  const location = [distance, item.resolvedLocation?.name, item.resolvedLocation?.addressText].filter(Boolean).join(" · ");
  const serviceLocation = [item.resolvedLocation?.name, item.resolvedLocation?.addressText].filter(Boolean).join(" · ");
  const ratingText = rating?.averageRating != null && rating.reviewCount ? `${rating.averageRating.toLocaleString("vi-VN")} · ${rating.reviewCount} đánh giá` : null;

  const media = resale ? <div className={resaleStyles.resaleMedia}>
    <div className={resaleStyles.resalePhoto}>{item.media?.[0]?.url && <Image src={item.media[0].url!} alt={item.name ?? "Ảnh thực tế sản phẩm thanh lý"} fill sizes="(min-width: 1024px) 520px, 326px" unoptimized />}</div>
    <small>{item.media?.length ? `Ảnh thực tế · 1/${item.media.length}` : "Chưa có ảnh thực tế"}</small>
  </div> : !service ? <div className={styles.media}>
    {item.media?.[0]?.url ? <Image src={item.media[0].url!} alt={item.name ?? "Hình ảnh sản phẩm"} fill sizes="(min-width: 1024px) 520px, 390px" unoptimized /> : <Image src="/figma/product-media-placeholder.svg" alt="" fill sizes="(min-width: 1024px) 520px, 390px" priority />}
  </div> : <div className={styles.serviceMedia}><span>Dịch vụ</span><strong>{item.name ?? "Dịch vụ CoVie"}</strong></div>;

  return <article className={`${styles.detail} ${service ? styles.serviceDetail : resale ? resaleStyles.resaleDetail : ""}`}>
    <header className={styles.appBar}><button aria-label="Quay lại" onClick={() => router.back()}><CoVieIcon name="back" /></button><h1>{service ? "Chi tiết dịch vụ" : resale ? "Chi tiết thanh lý" : "Chi tiết sản phẩm"}</h1></header>
    <div className={styles.desktopContainer}>
      <h1 className={styles.desktopTitle}>{service ? "Chi tiết dịch vụ" : resale ? "Chi tiết thanh lý" : "Chi tiết sản phẩm"}</h1>
      <div className={styles.desktopColumns}>
        <div className={styles.mediaColumn}>{media}</div>
        <div className={styles.body}>
          <section className={`${styles.listing} ${resale ? resaleStyles.resaleListing : ""}`} aria-labelledby="listing-title"><span>{service ? "Dịch vụ" : item.listingKind === "RESALE" ? "Thanh lý" : "Sản phẩm"}</span><h2 id="listing-title">{item.name ?? (service ? "Dịch vụ" : "Sản phẩm")}</h2><strong>{money(offer?.price)}</strong><p>{item.merchant?.name ?? "Người bán CoVie"}</p>{service ? serviceLocation && <small>Phục vụ {serviceLocation}</small> : location && <small>{location}</small>}{ratingText && <div className={styles.rating}><CoVieIcon name="rating-star" />{ratingText}</div>}</section>
          {service ? <>
            <fieldset className={styles.serviceOptions} role="radiogroup">
              <legend>Phân loại</legend>
              {item.variants?.map(variant => <label key={variant.id ?? variant.name} className={styles.serviceOption}>
                <input type="radio" name="service-variant" checked={variant.id === selectedVariant?.id} disabled={!variant.offer?.available || busy} onChange={() => setSelectedVariantId(variant.id)} />
                <span>{variant.name ?? "Tùy chọn dịch vụ"}</span>
                <strong>{money(variant.offer?.price)}</strong>
              </label>)}
            </fieldset>
            <section className={styles.serviceFulfillment} aria-label="Phương thức thực hiện"><span aria-hidden="true" /><div><strong>Phối hợp dịch vụ</strong><small>Trao đổi thời gian thực hiện sau khi đặt</small></div></section>
            <section className={styles.serviceNote}><strong>Thời gian thực hiện</strong><p>Sau khi Provider xác nhận đơn, hai bên sẽ liên hệ để thống nhất thời gian thực hiện. V1 không có calendar booking.</p></section>
          </> : resale ? <>
            <section className={resaleStyles.conditionCard}><strong>Tình trạng</strong><p>{item.description ?? "Người bán chưa cung cấp mô tả tình trạng."}</p><small>Thông tin do người bán cung cấp.</small></section>
            <section className={resaleStyles.transactionCard}><strong>Vị trí giao dịch</strong><b>{item.resolvedLocation?.name ?? "Điểm bán CoVie"}</b>{item.resolvedLocation?.addressText && <small>{item.resolvedLocation.addressText}</small>}</section>
            <section className={resaleStyles.resaleFulfillment}><strong>Giao nhận</strong><b>Xác nhận trong giỏ hàng</b><small>Hình thức nhận hàng được xác nhận theo điểm bán và địa chỉ của bạn.</small></section>
          </> : <>
            <section className={styles.merchant}><div><strong>{item.merchant?.name ?? "Người bán CoVie"}</strong>{completion(item) && <small>{completion(item)}</small>}</div>{item.merchant?.id ? <Link href={`/merchants/${item.merchant.id}`}>Xem shop</Link> : <button type="button" disabled>Xem shop</button>}</section>
            <section className={styles.shipping}><strong>Giao nhận</strong><b>Xác nhận trong giỏ hàng</b><small>Phí và thời gian giao do hệ thống xác nhận theo địa chỉ nhận.</small></section>
            <section className={styles.quantity}><strong>Số lượng</strong><div><button aria-label="Giảm số lượng" disabled={quantity <= 1 || busy} onClick={() => setQuantity(v => Math.max(1, v - 1))}><CoVieIcon name="minus" /></button><output aria-label="Số lượng">{quantity}</output><button aria-label="Tăng số lượng" disabled={!available || busy} onClick={() => setQuantity(v => v + 1)}><CoVieIcon name="plus" /></button></div></section>
            {item.description && <section className={styles.description}><strong>Mô tả</strong><p>{item.description}</p></section>}
          </>}
          {!available && <p className={styles.unavailable}>Hiện không khả dụng tại điểm bán này</p>}
          {message && <div className={styles.feedback} role="status"><span>{message}</span>{unauthorized && <Link href="/login">Đăng nhập</Link>}{cartId && <button onClick={() => router.push(`/cart/${cartId}`)}>Xem giỏ hàng</button>}</div>}
          <footer className={styles.actions}><button disabled={!available || busy} onClick={() => void add(false)}>{busy ? "Đang thêm…" : "Thêm vào giỏ"}</button><button disabled={!available || busy} onClick={() => void add(true)}>{service ? "Đặt dịch vụ" : "Mua ngay"}</button></footer>
        </div>
      </div>
    </div>
  </article>;
}
