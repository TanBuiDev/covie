"use client";

import {
  Basket,
  ForkKnife,
  HouseLine,
  Recycle,
  Storefront,
  Wrench,
  type IconWeight,
} from "@phosphor-icons/react";
import styles from "./category-icon.module.css";

export type CategoryThemeKey = "food" | "grocery" | "home" | "service" | "resale" | "default";

export type CategoryIdentity = {
  slug?: string | null;
  name?: string | null;
  catalogType?: string | null;
};

export function resolveCategoryTheme(category: CategoryIdentity): {
  themeKey: CategoryThemeKey;
  label: string;
} {
  const key = `${category.slug ?? ""} ${category.name ?? ""}`.toLowerCase();
  if (key.includes("an") || key.includes("food") || key.includes("com") || key.includes("drink") || key.includes("uong")) {
    return { themeKey: "food", label: "Ăn uống" };
  }
  if (key.includes("tap") || key.includes("hoa") || key.includes("grocery") || key.includes("sieu")) {
    return { themeKey: "grocery", label: "Tạp hóa" };
  }
  if (key.includes("gia") || key.includes("dung") || key.includes("home") || key.includes("nha") || key.includes("ca nhan")) {
    return { themeKey: "home", label: "Nhà cửa & cá nhân" };
  }
  if (key.includes("vu") || key.includes("service") || key.includes("sua") || category.catalogType === "SERVICE") {
    return { themeKey: "service", label: "Dịch vụ" };
  }
  if (key.includes("thanh") || key.includes("ly") || key.includes("resale") || key.includes("cu")) {
    return { themeKey: "resale", label: "Đồ thanh lý" };
  }
  return { themeKey: "default", label: category.name ?? "Danh mục" };
}

export function CategoryIcon({
  category,
  size = 28,
  weight = "duotone",
  className,
}: {
  category: CategoryIdentity;
  size?: number;
  weight?: IconWeight;
  className?: string;
}) {
  const { themeKey } = resolveCategoryTheme(category);
  switch (themeKey) {
    case "food":
      return <ForkKnife size={size} weight={weight} className={className} aria-hidden="true" />;
    case "grocery":
      return <Basket size={size} weight={weight} className={className} aria-hidden="true" />;
    case "home":
      return <HouseLine size={size} weight={weight} className={className} aria-hidden="true" />;
    case "service":
      return <Wrench size={size} weight={weight} className={className} aria-hidden="true" />;
    case "resale":
      return <Recycle size={size} weight={weight} className={className} aria-hidden="true" />;
    default:
      return <Storefront size={size} weight={weight} className={className} aria-hidden="true" />;
  }
}

export function CategoryBadge({
  category,
  size = "medium",
  weight = "duotone",
  className = "",
}: {
  category: CategoryIdentity;
  size?: "small" | "medium" | "large";
  weight?: IconWeight;
  className?: string;
}) {
  const { themeKey } = resolveCategoryTheme(category);
  const themeClass = styles[`${themeKey}Theme`] ?? styles.defaultTheme;
  const sizeClass = size === "small" ? styles.badgeSmall : size === "large" ? styles.badgeLarge : "";
  const iconPixelSize = size === "small" ? 20 : size === "large" ? 34 : 26;

  return (
    <span className={`${styles.badge} ${themeClass} ${sizeClass} ${className}`} aria-hidden="true">
      <CategoryIcon category={category} size={iconPixelSize} weight={weight} />
    </span>
  );
}
