import { beforeEach, describe, expect, it, vi } from "vitest";

const post = vi.fn();

vi.mock("@/lib/api/api-client", () => ({
  createApiClient: () => ({ POST: post }),
}));

import { orderCommand } from "./order-api";

describe("orderCommand", () => {
  beforeEach(() => {
    post.mockReset();
  });

  it("preserves the generated API error after openapi-fetch consumed the response body", async () => {
    post.mockResolvedValue({
      error: {
        error: {
          code: "ORDER_STATE_CONFLICT",
          message: "Order state changed.",
          requestId: "request-order-conflict",
        },
      },
      response: new Response(null, { status: 409 }),
    });

    await expect(orderCommand("order-1", "REJECT", "intent-1", { reasonCode: "STALE" }))
      .rejects.toMatchObject({
        status: 409,
        code: "ORDER_STATE_CONFLICT",
        requestId: "request-order-conflict",
      });
  });
});
