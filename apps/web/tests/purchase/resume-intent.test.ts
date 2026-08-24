import { describe, expect, it } from "vitest";
import { PURCHASE_INTENT_STORAGE_KEY, consumePurchaseIntent, readPurchaseIntent, safeInternalReturnPath, writePurchaseIntent } from "@/features/purchase/purchase-intent";

function memoryStorage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => values.set(key, value),
    removeItem: (key: string) => values.delete(key),
    raw: values,
  };
}

describe("purchase intent", () => {
  it("expires after fifteen minutes and is consumed once", () => {
    const storage = memoryStorage();
    writePurchaseIntent(storage, { version: 1, kind: "DIRECT_CHECKOUT", locationVariantId: "lv-1", quantity: 2, createdAt: 1_000 });
    expect(readPurchaseIntent(storage, 1_000 + 14 * 60_000)).not.toBeNull();
    expect(consumePurchaseIntent(storage, 1_000 + 14 * 60_000)?.locationVariantId).toBe("lv-1");
    expect(storage.raw.has(PURCHASE_INTENT_STORAGE_KEY)).toBe(false);

    writePurchaseIntent(storage, { version: 1, kind: "ADD_TO_CART", locationVariantId: "lv-2", quantity: 1, createdAt: 1_000 });
    expect(readPurchaseIntent(storage, 1_000 + 16 * 60_000)).toBeNull();
  });

  it("rejects external or encoded external return paths", () => {
    expect(safeInternalReturnPath("https://evil.example")).toBe("/");
    expect(safeInternalReturnPath("//evil.example")).toBe("/");
    expect(safeInternalReturnPath("/%2f%2fevil.example")).toBe("/");
    expect(safeInternalReturnPath("/orders/123?from=login")).toBe("/orders/123?from=login");
  });
});
