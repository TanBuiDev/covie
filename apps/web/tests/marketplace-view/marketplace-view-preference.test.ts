import { describe, expect, it } from "vitest";
import { MARKETPLACE_VIEW_STORAGE_KEY, parseMarketplaceViewMode, resolveMarketplaceViewMode } from "@/features/marketplace-view/marketplace-view-preference";

describe("marketplace view preference", () => {
  it("uses a dedicated storage key", () => {
    expect(MARKETPLACE_VIEW_STORAGE_KEY).toBe("covie:marketplace-view:v1");
  });
  it("accepts only supported modes", () => {
    expect(parseMarketplaceViewMode("social")).toBe("social");
    expect(parseMarketplaceViewMode("catalog")).toBe("catalog");
    expect(parseMarketplaceViewMode("grid")).toBeNull();
    expect(parseMarketplaceViewMode(null)).toBeNull();
  });
  it("prioritizes URL over saved preference and defaults", () => {
    expect(resolveMarketplaceViewMode("social", "catalog", "catalog")).toBe("social");
    expect(resolveMarketplaceViewMode(null, "social", "catalog")).toBe("social");
    expect(resolveMarketplaceViewMode("unknown", "corrupt", "catalog")).toBe("catalog");
  });
});
