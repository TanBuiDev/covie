export const PURCHASE_INTENT_STORAGE_KEY = "covie:purchase-intent:v1";
export const PURCHASE_INTENT_MAX_AGE_MS = 15 * 60 * 1000;

export type PurchaseIntent =
  | {
      version: 1;
      kind: "ADD_TO_CART";
      locationVariantId: string;
      quantity: number;
      createdAt: number;
    }
  | {
      version: 1;
      kind: "DIRECT_CHECKOUT";
      locationVariantId: string;
      quantity: number;
      createdAt: number;
    };

function isIntent(value: unknown): value is PurchaseIntent {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Partial<PurchaseIntent>;
  return candidate.version === 1
    && (candidate.kind === "ADD_TO_CART" || candidate.kind === "DIRECT_CHECKOUT")
    && typeof candidate.locationVariantId === "string"
    && candidate.locationVariantId.trim().length > 0
    && Number.isInteger(candidate.quantity)
    && Number(candidate.quantity) > 0
    && typeof candidate.createdAt === "number"
    && Number.isFinite(candidate.createdAt);
}

export function writePurchaseIntent(storage: Pick<Storage, "setItem">, intent: PurchaseIntent): void {
  storage.setItem(PURCHASE_INTENT_STORAGE_KEY, JSON.stringify(intent));
}

export function readPurchaseIntent(
  storage: Pick<Storage, "getItem" | "removeItem">,
  now = Date.now(),
): PurchaseIntent | null {
  const raw = storage.getItem(PURCHASE_INTENT_STORAGE_KEY);
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (!isIntent(parsed) || parsed.createdAt > now || now - parsed.createdAt > PURCHASE_INTENT_MAX_AGE_MS) {
      storage.removeItem(PURCHASE_INTENT_STORAGE_KEY);
      return null;
    }
    return parsed;
  } catch {
    storage.removeItem(PURCHASE_INTENT_STORAGE_KEY);
    return null;
  }
}

export function consumePurchaseIntent(
  storage: Pick<Storage, "getItem" | "removeItem">,
  now = Date.now(),
): PurchaseIntent | null {
  const intent = readPurchaseIntent(storage, now);
  storage.removeItem(PURCHASE_INTENT_STORAGE_KEY);
  return intent;
}

export function normalizeServiceIntent(intent: PurchaseIntent): PurchaseIntent {
  return { ...intent, quantity: 1 };
}

export function safeInternalReturnPath(value: string | null | undefined, fallback = "/"): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.includes("\\")) return fallback;
  let decoded = value;
  try {
    for (let i = 0; i < 2; i += 1) decoded = decodeURIComponent(decoded);
  } catch {
    return fallback;
  }
  if (!decoded.startsWith("/") || decoded.startsWith("//") || decoded.includes("\\")) return fallback;
  try {
    const target = new URL(decoded, "https://covie.invalid");
    return target.origin === "https://covie.invalid" ? `${target.pathname}${target.search}${target.hash}` : fallback;
  } catch {
    return fallback;
  }
}
