"use client";

import { Article, SquaresFour } from "@phosphor-icons/react";
import type { MarketplaceViewMode } from "./marketplace-view-preference";
import styles from "./marketplace-view.module.css";

export function MarketplaceViewToggle({
  mode,
  onModeChange,
}: {
  mode: MarketplaceViewMode;
  onModeChange: (next: MarketplaceViewMode) => void;
}) {
  return (
    <div className={styles.toggle} role="group" aria-label="Chế độ hiển thị sản phẩm">
      <button
        type="button"
        className={[styles.toggleButton, mode === "social" ? styles.toggleActive : ""].join(" ")}
        aria-pressed={mode === "social"}
        onClick={() => onModeChange("social")}
      >
        <Article size={18} aria-hidden="true" />
        <span>Khám phá</span>
      </button>
      <button
        type="button"
        className={[styles.toggleButton, mode === "catalog" ? styles.toggleActive : ""].join(" ")}
        aria-pressed={mode === "catalog"}
        onClick={() => onModeChange("catalog")}
      >
        <SquaresFour size={18} aria-hidden="true" />
        <span>Mua sắm nhanh</span>
      </button>
    </div>
  );
}
