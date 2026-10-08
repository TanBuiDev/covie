import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { BuyerReputationScreen } from "@/features/reputation/buyer-reputation-screen";
import { ProfileOverview } from "@/features/profile/profile-overview";

const { getMyReputation, navigation } = vi.hoisted(() => ({
  getMyReputation: vi.fn(),
  navigation: { back: vi.fn() },
}));

vi.mock("@/features/reputation/reputation-api", () => ({ getMyReputation }));
vi.mock("next/navigation", () => ({ useRouter: () => navigation }));

describe("BUY-009 Buyer reputation", () => {
  beforeEach(() => {
    getMyReputation.mockReset();
    navigation.back.mockReset();
  });

  it("renders the server aggregate as a transaction metric without review stars", async () => {
    getMyReputation.mockResolvedValue({
      eligibleOrders: 25,
      completedOrders: 24,
      completionRateBasisPoints: 9600,
      warning: false,
    });
    render(<BuyerReputationScreen />);

    expect(screen.getByRole("status")).toHaveTextContent("Đang tải uy tín");
    expect(await screen.findByText("96%")).toBeInTheDocument();
    expect(screen.getByText("24/25 giao dịch đủ điều kiện đã hoàn tất.")).toBeInTheDocument();
    expect(screen.queryByLabelText(/sao/i)).not.toBeInTheDocument();
  });

  it("handles a zero denominator and displays the server warning", async () => {
    getMyReputation.mockResolvedValue({ eligibleOrders: 0, completedOrders: 0, warning: true });
    render(<BuyerReputationScreen />);

    expect(await screen.findByText("Chưa có dữ liệu")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent(/tỷ lệ hoàn thành đang ở mức cần lưu ý/i);
  });

  it("offers a retry after a load failure", async () => {
    getMyReputation.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({ completionRateBasisPoints: 8750 });
    render(<BuyerReputationScreen />);

    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải uy tín");
    fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => expect(getMyReputation).toHaveBeenCalledTimes(2));
    expect(await screen.findByText("87,5%")).toBeInTheDocument();
  });

  it("links the account overview to the dedicated reputation route", () => {
    render(<ProfileOverview user={{ id: "user-1", displayName: "Minh" }} />);
    expect(screen.getByRole("link", { name: /Uy tín của tôi/ })).toHaveAttribute("href", "/me/reputation");
  });
});
