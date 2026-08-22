import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { expect, it, vi } from "vitest";
import { ReviewForm } from "@/features/reviews/review-form";
const { createReview } = vi.hoisted(() => ({ createReview: vi.fn() })); vi.mock("@/features/reviews/review-api", () => ({ createReview }));
it("submits one review while the request is in flight", async () => { createReview.mockResolvedValue({ id: "review" }); render(<ReviewForm orderItemId="item" />); const button=screen.getByRole("button",{name:"Gửi đánh giá"}); fireEvent.click(button); fireEvent.click(button); await waitFor(()=>expect(createReview).toHaveBeenCalledTimes(1)); expect(createReview.mock.calls[0][0]).toMatchObject({orderItemId:"item",rating:5}); expect(createReview.mock.calls[0][1]).toEqual(expect.any(String)); });
