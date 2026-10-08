import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { OrderReviewSection } from "@/features/reviews/order-review-section";
import { submitReviewIntent } from "@/features/reviews/review-form";

const { createReview } = vi.hoisted(() => ({ createReview: vi.fn() }));
vi.mock("@/features/reviews/review-api", () => ({ createReview }));

describe("BUY-010 server-driven review eligibility", () => {
  beforeEach(() => createReview.mockReset());

  it("shows the form only when eligible and submits the server-returned orderItemId", async () => {
    createReview.mockResolvedValue({ id: "review-1" });
    render(<OrderReviewSection item={{ id: "row-id", reviewEligibility: { orderItemId: "authoritative-item-id", eligible: true, reason: "ELIGIBLE" } }} onChanged={vi.fn()} />);
    fireEvent.click(screen.getByRole("button", { name: "Gửi đánh giá" }));
    await waitFor(() => expect(createReview).toHaveBeenCalledWith(expect.objectContaining({ orderItemId: "authoritative-item-id" }), expect.any(String)));
  });

  it("links an eligible order detail to the dedicated review route", () => {
    render(<OrderReviewSection item={{ id: "item-1", reviewEligibility: { orderItemId: "item-1", eligible: true, reason: "ELIGIBLE" } }} orderId="order-1" onChanged={vi.fn()} />);
    expect(screen.getByRole("link", { name: "Đánh giá đơn" })).toHaveAttribute("href", "/orders/order-1/review");
    expect(screen.queryByRole("button", { name: "Gửi đánh giá" })).not.toBeInTheDocument();
  });

  it.each([
    ["ALREADY_REVIEWED", "Bạn đã đánh giá nội dung này"],
    ["CREATION_DISABLED", "Hệ thống đánh giá đang tạm tắt"],
    ["ORDER_NOT_COMPLETED", "Đánh giá sẽ mở khi đơn hoàn tất"],
    ["ITEM_NOT_REVIEWABLE", "Nội dung này không hỗ trợ đánh giá"],
  ] as const)("renders %s without inventing a client-side CTA", (reason, copy) => {
    render(<OrderReviewSection item={{ id: "item-1", reviewEligibility: { orderItemId: "item-1", eligible: false, reason } }} onChanged={vi.fn()} />);
    expect(screen.getByRole("heading", { name: copy })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Gửi đánh giá" })).not.toBeInTheDocument();
  });

  it.each(["REVIEW_ALREADY_EXISTS", "REVIEW_CREATION_DISABLED"])("refreshes order truth after stale submit code %s", async (code) => {
    const onChanged = vi.fn().mockResolvedValue(undefined);
    const staleSubmit = async () => { throw Object.assign(new Error(code), { code }); };
    await expect(submitReviewIntent({ orderItemId: "item-1", rating: 5 }, "intent", { onStale: onChanged }, staleSubmit)).resolves.toBe("stale");
    expect(onChanged).toHaveBeenCalledTimes(1);
  });

  it("refreshes the order after a successful review", async () => {
    const onChanged = vi.fn().mockResolvedValue(undefined);
    createReview.mockResolvedValue({ id: "review-1" });
    render(<OrderReviewSection item={{ id: "item-1", reviewEligibility: { orderItemId: "item-1", eligible: true, reason: "ELIGIBLE" } }} onChanged={onChanged} />);
    fireEvent.click(screen.getByRole("button", { name: "Gửi đánh giá" }));
    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1));
  });
});
