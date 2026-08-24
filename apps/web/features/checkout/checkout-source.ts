export type CheckoutUiSource =
  | { type: "CART"; cartId: string }
  | { type: "DIRECT"; locationVariantId: string; quantity: number }
  | { type: "NEED_REQUEST"; needRequestId: string; needResponseItemId: string; quantity: number };

type ParamValue = string | string[] | undefined;
type ParamSource = URLSearchParams | Record<string, ParamValue>;

function read(params: ParamSource, key: string): string | undefined {
  if (params instanceof URLSearchParams) return params.get(key) ?? undefined;
  const value = params[key];
  return Array.isArray(value) ? value[0] : value;
}

function positiveQuantity(value: string | undefined): number | null {
  if (!value) return 1;
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

function id(value: string | undefined): string | null {
  const normalized = value?.trim();
  return normalized ? normalized : null;
}

export function parseCheckoutSource(params: ParamSource): CheckoutUiSource | null {
  const source = read(params, "source");
  if (source === "CART") {
    const cartId = id(read(params, "cartId"));
    return cartId ? { type: "CART", cartId } : null;
  }

  if (source === "DIRECT") {
    const locationVariantId = id(read(params, "locationVariantId"));
    const quantity = positiveQuantity(read(params, "quantity"));
    return locationVariantId && quantity ? { type: "DIRECT", locationVariantId, quantity } : null;
  }

  if (source === "NEED_REQUEST") {
    const needRequestId = id(read(params, "needRequestId"));
    const needResponseItemId = id(read(params, "needResponseItemId"));
    const quantity = positiveQuantity(read(params, "quantity"));
    return needRequestId && needResponseItemId && quantity
      ? { type: "NEED_REQUEST", needRequestId, needResponseItemId, quantity }
      : null;
  }

  return null;
}
