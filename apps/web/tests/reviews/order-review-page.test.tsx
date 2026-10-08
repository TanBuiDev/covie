import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { getOrder, navigation } = vi.hoisted(() => ({
  getOrder: vi.fn(),
  navigation: { back: vi.fn() },
}));

vi.mock("@/features/orders/order-api", () => ({ getOrder }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));

import { OrderReviewPage } from "@/features/reviews/order-review-page";
import { CovieApiError } from "@/lib/api/api-error";

function order(reason: "ELIGIBLE" | "ALREADY_REVIEWED" | "CREATION_DISABLED", eligible = reason === "ELIGIBLE") {
  return {
    id: "order-1",
    orderCode: "CV1024",
    status: "COMPLETED",
    merchant: { merchantId: "merchant-1" },
    items: [{
      id: "item-1",
      quantity: 1,
      snapshot: { itemName: "Rau củ theo mùa · Gói 1 kg" },
      reviewEligibility: { orderItemId: "authoritative-item-id", eligible, reason },
    }],
  };
}

describe("BUY-010 dedicated review route", () => {
  it("clears the previous review form immediately when the order changes", async () => {
    getOrder.mockResolvedValueOnce(order("ELIGIBLE"));
    const view=render(<OrderReviewPage orderId="order-1" />);
    await screen.findByRole("group",{name:"Đánh giá giao dịch"});
    getOrder.mockReturnValue(new Promise(()=>{}));
    view.rerender(<OrderReviewPage orderId="order-2" />);
    expect(screen.queryByRole("group",{name:"Đánh giá giao dịch"})).not.toBeInTheDocument();
    expect(screen.getByText("Đang tải đánh giá…")).toBeInTheDocument();
  });

  it("ignores a late read failure from the previous review order", async () => {
    let rejectOld!: (reason:unknown)=>void;
    getOrder.mockReturnValueOnce(new Promise((_,reject)=>{rejectOld=reject;}));
    const view=render(<OrderReviewPage orderId="order-1" />);
    await waitFor(()=>expect(getOrder).toHaveBeenCalledWith("order-1"));
    getOrder.mockRejectedValueOnce(new CovieApiError(401,"AUTH_REQUIRED","new"));
    view.rerender(<OrderReviewPage orderId="order-2" />);
    await screen.findByRole("link",{name:"Đăng nhập lại"});
    await act(async()=>{rejectOld(new CovieApiError(503,"ERROR","old"));});
    expect(screen.getByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Forders%2Forder-2%2Freview");
  });

  it("keeps the latest result when review retries overlap", async () => {
    getOrder.mockRejectedValueOnce(new CovieApiError(503,"ERROR","initial"));
    render(<OrderReviewPage orderId="order-1" />);
    const retry=await screen.findByRole("button",{name:"Thử lại"});
    let rejectEarlier!: (reason:unknown)=>void;
    getOrder.mockReturnValueOnce(new Promise((_,reject)=>{rejectEarlier=reject;}));
    getOrder.mockRejectedValueOnce(new CovieApiError(401,"AUTH_REQUIRED","new"));
    fireEvent.click(retry);fireEvent.click(retry);
    await screen.findByRole("link",{name:"Đăng nhập lại"});
    await act(async()=>{rejectEarlier(new CovieApiError(503,"ERROR","old"));});
    expect(screen.getByRole("link",{name:"Đăng nhập lại"})).toBeInTheDocument();
  });
  it("offers login to the same review route after an opaque401", async () => {
    getOrder.mockRejectedValue(new CovieApiError(401,"AUTH_REQUIRED","opaque"));
    render(<OrderReviewPage orderId="order-1" />);
    expect(await screen.findByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Forders%2Forder-1%2Freview");
    expect(screen.queryByText("opaque")).not.toBeInTheDocument();
  });

  it("keeps a503 as retry with safe copy", async () => {
    getOrder.mockRejectedValue(new CovieApiError(503,"ERROR","private detail"));
    render(<OrderReviewPage orderId="order-1" />);
    expect(await screen.findByRole("button",{name:"Thử lại"})).toBeInTheDocument();
    expect(screen.queryByText("private detail")).not.toBeInTheDocument();
  });
  beforeEach(() => {
    getOrder.mockReset();
    navigation.back.mockReset();
  });

  it("renders the authoritative eligible item in the frozen review-screen structure", async () => {
    getOrder.mockResolvedValue(order("ELIGIBLE"));
    render(<OrderReviewPage orderId="order-1" />);

    await waitFor(() => expect(screen.getAllByRole("heading", { name: "Đánh giá đơn", level: 1 })).toHaveLength(2));
    expect(screen.getByText("Rau củ theo mùa · Gói 1 kg")).toBeInTheDocument();
    expect(screen.getByText(/CV1024 · Người bán · Hoàn tất/)).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "Đánh giá giao dịch" })).toBeInTheDocument();
    expect(screen.getByText("Chọn từ 1 đến 5 sao")).toHaveClass("review-rating-hint");
    expect(screen.getByRole("link", { name: "Quay lại chi tiết đơn" })).toHaveAttribute("href", "/orders/order-1");
  });

  it.each([
    ["ALREADY_REVIEWED", "Bạn đã đánh giá nội dung này"],
    ["CREATION_DISABLED", "Hệ thống đánh giá đang tạm tắt"],
  ] as const)("renders the server-owned %s state without the form", async (reason, title) => {
    getOrder.mockResolvedValue(order(reason, false));
    render(<OrderReviewPage orderId="order-1" />);

    await waitFor(() => expect(screen.getByRole("heading", { name: title })).toBeInTheDocument());
    expect(screen.queryByRole("button", { name: "Gửi đánh giá" })).not.toBeInTheDocument();
  });
});
