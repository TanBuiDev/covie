"use client";

import { useContext, useEffect, useState } from "react";
import Link from "next/link";
import Image from "next/image";
import {
  ArrowRight,
  CookingPot,
  HouseLine,
  Info,
  Leaf,
  ShoppingBag,
  Storefront,
  Wrench,
} from "@phosphor-icons/react";
import type { components } from "@covie/api-client";
import { discoverCatalogItems } from "@/features/catalog/catalog-api";
import { BrowseLocationContext } from "@/features/location/browse-location-provider";
import { toDiscoveryLocation, type DiscoveryLocation } from "@/features/location/location-context";
import styles from "./cart-list.module.css";

type Cart = components["schemas"]["CartResponse"];
type CartItem = components["schemas"]["CartItemResponse"];
const formatMoney = (amount: number) => `${new Intl.NumberFormat("vi-VN").format(amount)} ₫`;

function CartItemPreviewThumb({ item, service }: { item: CartItem; service: boolean }) {
  const locationCtx = useContext(BrowseLocationContext);
  const location = locationCtx?.location;
  const [imageUrl, setImageUrl] = useState<string | null>(null);

  useEffect(() => {
    if (service || !item.itemName) return;
    let cancelled = false;
    const discLocation: DiscoveryLocation = toDiscoveryLocation(location) ?? {
      kind: "coordinates",
      lat: 10.84,
      lng: 106.84,
    };
    discoverCatalogItems({ location: discLocation, q: item.itemName })
      .then((res) => {
        if (cancelled) return;
        const matched =
          res.page?.items?.find((p) => p.name?.toLowerCase() === item.itemName?.toLowerCase()) ??
          res.page?.items?.[0];
        if (matched?.primaryMedia?.url) setImageUrl(matched.primaryMedia.url);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [item.itemName, service, location]);

  return (
    <span className={styles.previewThumb} title={item.itemName ?? ""}>
      {imageUrl ? (
        <Image
          src={imageUrl}
          alt={item.itemName ?? ""}
          width={28}
          height={28}
          unoptimized
          className={styles.previewThumbImage}
          onError={() => setImageUrl(null)}
        />
      ) : service ? (
        <Wrench size={14} weight="duotone" className={styles.previewThumbIcon} />
      ) : (
        <ShoppingBag size={14} weight="duotone" className={styles.previewThumbIcon} />
      )}
    </span>
  );
}

export function CartList({ initialCarts }: { initialCarts: Cart[] }) {
  if (!initialCarts.length) {
    return (
      <section className={styles.empty} aria-label="Giỏ hàng trống">
        <div className={styles.emptyIconWrap}>
          <ShoppingBag size={44} weight="duotone" className={styles.emptyIcon} />
        </div>
        <h2>Giỏ hàng của bạn đang trống</h2>
        <p>Khám phá sản phẩm, dịch vụ và đồ thanh lý gần bạn để bắt đầu.</p>
        <Link className={styles.emptyCta} href="/search">
          Khám phá gần bạn
        </Link>

        {/* Quick suggestions for empty state */}
        <div className={styles.emptySuggestions}>
          <span className={styles.emptySuggestionsTitle}>Gợi ý mua sắm nội khu:</span>
          <div className={styles.suggestionGrid}>
            <Link href="/search?category=fresh-food" className={styles.suggestionCard}>
              <Leaf size={18} weight="duotone" className={styles.suggestionIcon} />
              <span>Rau củ quả tươi</span>
            </Link>
            <Link href="/search?category=cooked-food" className={styles.suggestionCard}>
              <CookingPot size={18} weight="duotone" className={styles.suggestionIcon} />
              <span>Món ngon nhà làm</span>
            </Link>
            <Link href="/search?category=household" className={styles.suggestionCard}>
              <HouseLine size={18} weight="duotone" className={styles.suggestionIcon} />
              <span>Đồ dùng gia đình</span>
            </Link>
            <Link href="/search?category=services" className={styles.suggestionCard}>
              <Wrench size={18} weight="duotone" className={styles.suggestionIcon} />
              <span>Dịch vụ tiện ích</span>
            </Link>
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className={styles.content} aria-label="Giỏ hàng của bạn">
      <div className={styles.headerRow}>
        <div>
          <h2>Giỏ hàng của bạn</h2>
          <p className={styles.headerSubtitle}>
            Hiện có {initialCarts.length} giỏ hàng đang chờ xử lý từ các hàng xóm
          </p>
        </div>
      </div>

      {/* Community Multi-Cart Notice */}
      <div className={styles.multiCartBanner}>
        <Info size={20} weight="fill" className={styles.bannerIcon} />
        <div className={styles.bannerText}>
          <strong>Mua sắm nội khu cùng hàng xóm:</strong>
          <span>
            Mỗi giỏ hàng thuộc về một người bán riêng biệt trong khu đô thị. Vui lòng thanh toán theo từng giỏ để người bán chuẩn bị chu đáo và giao nhanh nhất.
          </span>
        </div>
      </div>

      <div className={styles.cards}>
        {initialCarts.map((cart) => {
          const service = cart.cartType === "SERVICE";
          const items = cart.items ?? [];
          const count = items.reduce((sum, item) => sum + (item.quantity ?? 0), 0);
          const total = items.reduce(
            (sum, item) => sum + (item.priceSnapshot ?? 0) * (item.quantity ?? 0),
            0,
          );
          const label = service ? "Dịch vụ" : "Sản phẩm";
          const previewItems = items.slice(0, 3);
          const remainingCount = items.length - previewItems.length;

          return (
            <article
              key={cart.id ?? `${cart.merchantLocationId}-${cart.cartType}`}
              className={`${styles.card} ${service ? styles.service : ""}`}
            >
              <div className={styles.cardHeader}>
                <div className={styles.shopBadge}>
                  {service ? (
                    <Wrench size={18} weight="duotone" className={styles.badgeIconService} />
                  ) : (
                    <Storefront size={18} weight="duotone" className={styles.badgeIconProduct} />
                  )}
                  <h3>Giỏ {label.toLocaleLowerCase("vi-VN")}</h3>
                </div>
                <span className={styles.type}>{label}</span>
              </div>

              {/* Items Preview Strip */}
              {previewItems.length > 0 && (
                <div className={styles.previewStrip}>
                  <div className={styles.previewThumbs}>
                    {previewItems.map((item, idx) => (
                      <CartItemPreviewThumb
                        key={item.id ?? idx}
                        item={item}
                        service={service}
                      />
                    ))}
                    {remainingCount > 0 && (
                      <span className={styles.previewRemaining}>+{remainingCount}</span>
                    )}
                  </div>
                  <span className={styles.previewSampleName}>
                    {previewItems[0]?.itemName}
                    {items.length > 1 ? ` và ${items.length - 1} mục khác` : ""}
                  </span>
                </div>
              )}

              <p>
                <span>
                  {count} {label.toLocaleLowerCase("vi-VN")}
                </span>
                <strong>{formatMoney(total)}</strong>
              </p>

              {cart.id && (
                <Link
                  className={styles.cardAction}
                  href={`/cart/${encodeURIComponent(cart.id)}`}
                >
                  <span>{service ? "Xem giỏ dịch vụ" : "Xem và thanh toán giỏ này"}</span>
                  <ArrowRight size={16} weight="bold" />
                </Link>
              )}
            </article>
          );
        })}
      </div>

      <p className={styles.note}>
        Mỗi giỏ được thanh toán riêng. Sản phẩm và dịch vụ được tách thành các giỏ khác nhau.
      </p>
    </section>
  );
}
