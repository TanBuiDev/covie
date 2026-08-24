import { describe, expect, it } from "vitest";
import { parseCheckoutSource } from "@/features/checkout/checkout-source";

describe("checkout source", () => {
  it("parses CART, DIRECT and NEED_REQUEST source identifiers", () => {
    expect(parseCheckoutSource(new URLSearchParams("source=CART&cartId=cart-1"))).toEqual({ type: "CART", cartId: "cart-1" });
    expect(parseCheckoutSource(new URLSearchParams("source=DIRECT&locationVariantId=lv-1&quantity=2"))).toEqual({ type: "DIRECT", locationVariantId: "lv-1", quantity: 2 });
    expect(parseCheckoutSource(new URLSearchParams("source=NEED_REQUEST&needRequestId=n-1&needResponseItemId=ri-1&quantity=1"))).toEqual({ type: "NEED_REQUEST", needRequestId: "n-1", needResponseItemId: "ri-1", quantity: 1 });
  });

  it("rejects incomplete and invalid quantity sources", () => {
    expect(parseCheckoutSource(new URLSearchParams("source=CART"))).toBeNull();
    expect(parseCheckoutSource(new URLSearchParams("source=DIRECT&locationVariantId=lv-1&quantity=0"))).toBeNull();
    expect(parseCheckoutSource(new URLSearchParams("source=UNKNOWN"))).toBeNull();
  });
});
