"use client";

import { useContext, useEffect, useState } from "react";
import Image from "next/image";
import { ShoppingBag, Trash, Wrench } from "@phosphor-icons/react";
import type { components } from "@covie/api-client";
import { CoVieIcon } from "@/components/ui/covie-icon";
import { discoverCatalogItems } from "@/features/catalog/catalog-api";
import { BrowseLocationContext } from "@/features/location/browse-location-provider";
import { toDiscoveryLocation, type DiscoveryLocation } from "@/features/location/location-context";
import styles from "./cart-detail.module.css";

type CartItem = components["schemas"]["CartItemResponse"];

function formatMoney(amount: number) {
  return `${amount.toLocaleString("vi-VN")} ₫`;
}

export function CartItemRow({
  item,
  cartType,
  disabled = false,
  onQuantityChange,
  onRemove,
}: {
  item: CartItem;
  cartType?: "PRODUCT" | "SERVICE";
  disabled?: boolean;
  onQuantityChange(quantity: number): void;
  onRemove(): void;
}) {
  const quantity = item.quantity ?? 1;
  const unitPrice = item.priceSnapshot ?? 0;
  const isService = cartType === "SERVICE";
  const update = (next: number) => onQuantityChange(isService ? 1 : Math.max(1, next));

  const locationCtx = useContext(BrowseLocationContext);
  const location = locationCtx?.location;
  const [resolvedImageUrl, setResolvedImageUrl] = useState<string | null>(null);

  useEffect(() => {
    if (isService || !item.itemName) return;
    let cancelled = false;

    const discLocation: DiscoveryLocation = toDiscoveryLocation(location) ?? {
      kind: "coordinates",
      lat: 10.84,
      lng: 106.84,
    };

    discoverCatalogItems({
      location: discLocation,
      q: item.itemName,
    })
      .then((res) => {
        if (cancelled) return;
        const matched =
          res.page?.items?.find((p) => p.name?.toLowerCase() === item.itemName?.toLowerCase()) ??
          res.page?.items?.[0];
        if (matched?.primaryMedia?.url) {
          setResolvedImageUrl(matched.primaryMedia.url);
        }
      })
      .catch(() => {
        // Silently retain placeholder icon if discovery fails
      });

    return () => {
      cancelled = true;
    };
  }, [item.itemName, isService, location]);

  return (
    <li className={`${styles.item} ${disabled ? styles.itemDisabled : ""}`}>
      <div className={styles.thumbnail} aria-hidden="true">
        {resolvedImageUrl ? (
          <Image
            src={resolvedImageUrl}
            alt={item.itemName ?? "Ảnh sản phẩm"}
            width={110}
            height={110}
            unoptimized
            className={styles.thumbImage}
            onError={() => setResolvedImageUrl(null)}
          />
        ) : isService ? (
          <Wrench size={32} weight="duotone" className={styles.thumbPlaceholderIcon} />
        ) : (
          <ShoppingBag size={32} weight="duotone" className={styles.thumbPlaceholderIcon} />
        )}
      </div>
      <div className={styles.itemBody}>
        <div className={styles.itemHeader}>
          <strong className={styles.itemName}>
            {item.itemName ?? (isService ? "Dịch vụ" : "Sản phẩm")}
          </strong>
        </div>
        <span className={styles.variant}>
          {isService ? "Dịch vụ tại địa chỉ" : "Sản phẩm đã chọn"}
        </span>
        <div className={styles.prices}>
          <span>{formatMoney(unitPrice)}</span>
          <strong>{formatMoney(unitPrice * quantity)}</strong>
        </div>
        <div className={styles.actions}>
          {isService ? (
            <span aria-label="Số lượng" className={styles.fixedQuantityTag}>
              Số lượng: 1 (cố định)
            </span>
          ) : (
            <div className={styles.stepper}>
              <button
                type="button"
                aria-label="Giảm số lượng"
                disabled={disabled || quantity <= 1}
                onClick={() => update(quantity - 1)}
              >
                <CoVieIcon name="minus" className={styles.stepperIcon} />
              </button>
              <output aria-label="Số lượng">{quantity}</output>
              <button
                type="button"
                aria-label="Tăng số lượng"
                disabled={disabled}
                onClick={() => update(quantity + 1)}
              >
                <CoVieIcon name="plus" className={styles.stepperIcon} />
              </button>
            </div>
          )}
          <button
            type="button"
            className={styles.remove}
            disabled={disabled}
            onClick={onRemove}
          >
            <Trash size={15} weight="regular" className={styles.removeIcon} />
            <span>Xóa</span>
          </button>
        </div>
      </div>
    </li>
  );
}
