export type MarketplaceViewMode = "social" | "catalog";

export const MARKETPLACE_VIEW_STORAGE_KEY = "covie:marketplace-view:v1";

export function parseMarketplaceViewMode(value: unknown): MarketplaceViewMode | null {
  return value === "social" || value === "catalog" ? value : null;
}

/** Explicit URL choice wins, then a validated saved preference, then the page default. */
export function resolveMarketplaceViewMode(
  urlValue: unknown,
  storedValue: unknown,
  defaultMode: MarketplaceViewMode,
): MarketplaceViewMode {
  return parseMarketplaceViewMode(urlValue) ?? parseMarketplaceViewMode(storedValue) ?? defaultMode;
}
