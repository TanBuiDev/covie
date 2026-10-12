import { describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({ post: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => ({ POST: mocks.post }) }));

import { createReview } from "@/features/reviews/review-api";

describe("review API business errors", () => {
  it.each(["REVIEW_ALREADY_EXISTS", "REVIEW_CREATION_DISABLED"])("preserves parsed %s for the stale-review UI", async (code) => {
    const error = { error: { code, message: code, requestId: "review-error-test" } };
    const response = new Response(JSON.stringify(error), { status: 422 });
    // openapi-fetch parses the error body before returning the response to its caller.
    await response.json();
    mocks.post.mockResolvedValueOnce({ error, response });
    await expect(createReview({ orderItemId: "item", rating: 4 }, "key"))
      .rejects.toMatchObject({ code, status: 422, requestId: "review-error-test" });
  });
});
