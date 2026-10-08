import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ReviewList } from "./review-list";
import { listMerchantReviews } from "./review-api";

vi.mock("./review-api", () => ({
  listMerchantReviews: vi.fn(),
  listCatalogReviews: vi.fn(),
}));

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((complete) => { resolve = complete; });
  return { promise, resolve };
}

describe("ReviewList asynchronous loading", () => {
  it("ignores a stale merchant response after the target changes", async () => {
    const first = deferred<{ items: Array<{ id: string; rating: number; comment: string }>; page: { nextCursor: undefined } }>();
    vi.mocked(listMerchantReviews)
      .mockReturnValueOnce(first.promise)
      .mockResolvedValueOnce({ items: [{ id: "new-review", rating: 5, comment: "Đánh giá mới" }], page: { nextCursor: undefined } });

    const { rerender } = render(<ReviewList targetId="merchant-old" targetType="MERCHANT" />);
    rerender(<ReviewList targetId="merchant-new" targetType="MERCHANT" />);

    expect(await screen.findByText("Đánh giá mới")).toBeInTheDocument();
    first.resolve({ items: [{ id: "old-review", rating: 1, comment: "Đánh giá cũ" }], page: { nextCursor: undefined } });

    expect(screen.queryByText("Đánh giá cũ")).not.toBeInTheDocument();
  });
});
