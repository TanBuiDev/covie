import { expect, it, vi } from "vitest";

const post = vi.fn();

vi.mock("@/lib/api/api-client", () => ({
  createApiClient: () => ({ POST: post }),
}));

import { setStock } from "./inventory-api";

it("preserves the parsed reserved-stock error after the response body was consumed", async () => {
  post.mockResolvedValue({
    error: { error: { code: "INVENTORY_BELOW_RESERVED_QUANTITY", message: "Reserved stock." } },
    response: new Response(null, { status: 422 }),
  });

  await expect(setStock("merchant", "offer", 0, "intent"))
    .rejects.toMatchObject({ status: 422, code: "INVENTORY_BELOW_RESERVED_QUANTITY" });
});
