import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { cancelNeed, getMyNeed } = vi.hoisted(() => ({
  cancelNeed: vi.fn(),
  getMyNeed: vi.fn(),
}));

vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, cancelNeed, getMyNeed };
});
vi.mock("@/features/needs/need-response-list", () => ({ NeedResponseList: () => <div>Phản hồi</div> }));

import { NeedDetail } from "@/features/needs/need-detail";
import { CovieApiError } from "@/lib/api/api-error";

const activeNeed = {
  id: "need-1",
  type: "PRODUCT" as const,
  status: "ACTIVE" as const,
  categoryId: "category-1",
  title: "Cần mua rau củ",
  queryText: "rau củ",
  description: "Cần giao gần nhà",
  requestedQuantity: 2,
  locationSummary: { addressId: "address-1", areaId: "area-1", timezone: "Asia/Ho_Chi_Minh" },
  radiusMeters: 3000,
  expiresAt: "2026-09-17T12:00:00Z",
  responseCount: 1,
  fulfilledOrderId: "",
  allowedActions: ["CANCEL" as const],
  createdAt: "2026-09-16T12:00:00Z",
  closedAt: "",
};

describe("NEED-003 authoritative actions", () => {
  beforeEach(() => {
    cancelNeed.mockReset();
    getMyNeed.mockReset();
  });

  it("shows cancel only when returned by the detail contract and refreshes from the command response", async () => {
    getMyNeed.mockResolvedValueOnce(activeNeed);
    cancelNeed.mockResolvedValueOnce({ ...activeNeed, status: "CANCELLED", allowedActions: [] });
    render(<NeedDetail needId="need-1" />);

    await waitFor(() => expect(screen.getByRole("button", { name: "Hủy yêu cầu" })).toBeVisible());
    fireEvent.click(screen.getByRole("button", { name: "Hủy yêu cầu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));

    await waitFor(() => expect(cancelNeed).toHaveBeenCalledWith("need-1"));
    expect(screen.getByText("Đã hủy")).toBeVisible();
    expect(screen.queryByRole("button", { name: "Hủy yêu cầu" })).not.toBeInTheDocument();
  });

  it("does not infer cancellation from ACTIVE status when the server omits the action", async () => {
    getMyNeed.mockResolvedValueOnce({ ...activeNeed, allowedActions: [] });
    render(<NeedDetail needId="need-1" />);

    await waitFor(() => expect(screen.getByRole("heading", { name: "Cần mua rau củ" })).toBeVisible());
    expect(screen.queryByRole("button", { name: "Hủy yêu cầu" })).not.toBeInTheDocument();
  });

  it("keeps the server-authorized action retryable after a cancellation failure", async () => {
    getMyNeed.mockResolvedValueOnce(activeNeed);
    cancelNeed
      .mockRejectedValueOnce(new Error("Yêu cầu đã thay đổi. Vui lòng thử lại."))
      .mockResolvedValueOnce({ ...activeNeed, status: "CANCELLED", allowedActions: [] });
    render(<NeedDetail needId="need-1" />);

    fireEvent.click(await screen.findByRole("button", { name: "Hủy yêu cầu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể hủy yêu cầu. Vui lòng thử lại.");
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));

    await waitFor(() => expect(cancelNeed).toHaveBeenCalledTimes(2));
    expect(screen.getByText("Đã hủy")).toBeVisible();
  });

  it("does not expose server diagnostics on cancellation failure", async () => {
    getMyNeed.mockResolvedValueOnce(activeNeed);
    cancelNeed.mockRejectedValueOnce(new Error("SQL internal credentials fixture"));
    render(<NeedDetail needId="need-1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy yêu cầu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể hủy yêu cầu. Vui lòng thử lại.");
    expect(screen.queryByText(/SQL internal/)).not.toBeInTheDocument();
  });

  it("locks cancellation synchronously for repeated clicks in the same batch", async () => {
    getMyNeed.mockResolvedValueOnce(activeNeed);
    cancelNeed.mockReturnValueOnce(new Promise(() => {}));
    render(<NeedDetail needId="need-1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy yêu cầu" }));
    const button = screen.getByRole("button", { name: "Xác nhận hủy" });
    act(() => { button.click(); button.click(); });
    expect(cancelNeed).toHaveBeenCalledTimes(1);
    expect(button).toBeDisabled();
  });

  it("replaces stale actions with login to the same Need when cancellation returns 401", async () => {
    getMyNeed.mockResolvedValueOnce(activeNeed);
    cancelNeed.mockRejectedValueOnce(new CovieApiError(401, "AUTH_REQUIRED", "private detail"));
    render(<NeedDetail needId="need-1" />);
    fireEvent.click(await screen.findByRole("button", { name: "Hủy yêu cầu" }));
    fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));
    expect(await screen.findByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fneeds%2Fneed-1");
    expect(screen.queryByRole("button", { name: "Xác nhận hủy" })).not.toBeInTheDocument();
    expect(screen.queryByText("private detail")).not.toBeInTheDocument();
  });
});
