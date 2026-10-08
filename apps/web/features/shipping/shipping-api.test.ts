import { beforeEach, describe, expect, it, vi } from "vitest";

const patch = vi.fn();

vi.mock("@/lib/api/api-client", () => ({
  createApiClient: () => ({ PATCH: patch }),
}));

import { updateDeliveryCoverage, updateShippingPolicy } from "./shipping-api";

describe("shipping API errors", () => {
  beforeEach(() => {
    patch.mockReset();
    patch.mockResolvedValue({
      error: { error: { code: "RESOURCE_VERSION_CONFLICT", message: "Stale version." } },
      response: new Response(null, { status: 412 }),
    });
  });

  it.each([
    ["delivery coverage", () => updateDeliveryCoverage("merchant", "location", "coverage", 1, {
      coverageType: "PRODUCT_DELIVERY",
      radiusEnabled: true,
      radiusMeters: 3000,
      areaEnabled: false,
      locationAreaIds: [],
      status: "ACTIVE",
    })],
    ["shipping policy", () => updateShippingPolicy("merchant", "location", "policy", 1, {
      mode: "FIXED",
      baseFee: 25000,
      currencyCode: "VND",
      status: "ACTIVE",
      rules: [],
    })],
  ])("preserves a parsed conflict for %s", async (_name, request) => {
    await expect(request()).rejects.toMatchObject({ status: 412, code: "RESOURCE_VERSION_CONFLICT" });
  });
});
