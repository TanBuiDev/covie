import { afterEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MarketplaceProductCard } from "@/features/marketplace-view/marketplace-product-card";
import { SocialFeedProvider } from "@/features/marketplace-view/social-feed";
import type { components } from "@covie/api-client";
import type { SocialSummary } from "@/features/marketplace-view/social-api";

const api = vi.hoisted(() => ({
  fetchSocialSummaries: vi.fn(),
  changeSocialReaction: vi.fn(),
  fetchSocialComments: vi.fn(),
  addSocialComment: vi.fn(),
  removeSocialComment: vi.fn(),
  reportSocialComment: vi.fn(),
  isSignInRequired: vi.fn(() => false),
  socialErrorMessage: vi.fn(() => "Không thể thực hiện. Kiểm tra kết nối và thử lại."),
}));
vi.mock("@/features/marketplace-view/social-api", () => api);

const item = {
  id: "5a1f42a1-96c4-43d6-af76-b8423e47d6cd",
  name: "Bánh mì CoVie", type: "PRODUCT" as const, listingKind: "STANDARD" as const,
  merchant: { name: "Cửa hàng CoVie" }, priceFrom: { amount: 20000, currency: "VND" },
} as components["schemas"]["CatalogItemSummaryResponse"];

function mount() {
  return render(<SocialFeedProvider itemIds={[item.id!]} enabled>
    <MarketplaceProductCard item={item} mode="social" />
  </SocialFeedProvider>);
}

afterEach(() => vi.clearAllMocks());

describe("Phase 2 social card", () => {
  it("loads server counts and confirms like/save only after API responds", async () => {
    api.fetchSocialSummaries.mockResolvedValue({
      [item.id!]: { itemId: item.id, likes: 2, comments: 1, liked: false, saved: false },
    });
    api.changeSocialReaction.mockImplementation(async (_id: string, kind: string, active: boolean) =>
      ({ itemId: item.id, likes: kind === "likes" && active ? 3 : 2,
        comments: 1, liked: kind === "likes" && active, saved: kind === "saves" && active }));
    mount();
    const like = await screen.findByRole("button", { name: "Thích Bánh mì CoVie" });
    await waitFor(() => expect(like).not.toBeDisabled());
    fireEvent.click(like);
    await waitFor(() => expect(screen.getByRole("button", { name: "Bỏ thích Bánh mì CoVie" }))
      .toHaveAttribute("aria-pressed", "true"));
    fireEvent.click(screen.getByRole("button", { name: "Lưu Bánh mì CoVie" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Bỏ lưu Bánh mì CoVie" }))
      .toHaveAttribute("aria-pressed", "true"));
  });

  it("lazily loads comments immediately after opening", async () => {
    api.fetchSocialSummaries.mockResolvedValue({
      [item.id!]: { itemId: item.id, likes: 0, comments: 1, liked: false, saved: false },
    });
    api.fetchSocialComments.mockResolvedValue({
      items: [{ id: "comment1", itemId: item.id, authorName: "Khách hàng",
        body: "Còn hàng không?", createdAt: "2026-10-14T12:00:00Z", own: false }], nextCursor: null,
    });
    mount();
    expect(api.fetchSocialComments).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole("button", { name: "Bình luận Bánh mì CoVie" }));
    expect(await screen.findByText("Còn hàng không?")).toBeInTheDocument();
    expect(api.fetchSocialComments).toHaveBeenCalledTimes(1);
  });

  it("synchronizes server-confirmed state across independent card sections", async () => {
    api.fetchSocialSummaries.mockResolvedValue({
      [item.id!]: { itemId: item.id, likes: 4, comments: 0, liked: false, saved: false },
    });
    api.changeSocialReaction.mockResolvedValue({ itemId: item.id,
      likes: 5, comments: 0, liked: true, saved: false });
    render(<>
      <SocialFeedProvider itemIds={[item.id!]} enabled>
        <MarketplaceProductCard item={item} mode="social" />
      </SocialFeedProvider>
      <SocialFeedProvider itemIds={[item.id!]} enabled>
        <MarketplaceProductCard item={item} mode="social" />
      </SocialFeedProvider>
    </>);
    const buttons = await screen.findAllByRole("button", { name: "Thích Bánh mì CoVie" });
    await waitFor(() => expect(buttons.every((button) => !button.hasAttribute("disabled"))).toBe(true));
    expect(buttons[0]).toBeDefined();
    fireEvent.click(buttons[0]!);
    await waitFor(() => expect(screen.getAllByRole("button", { name: "Bỏ thích Bánh mì CoVie" })).toHaveLength(2));
  });

  it("allows reporting a foreign comment only after the server confirms the report", async () => {
    api.fetchSocialSummaries.mockResolvedValue({
      [item.id!]: { itemId: item.id, likes: 0, comments: 1, liked: false, saved: false },
    });
    api.fetchSocialComments.mockResolvedValue({
      items: [{ id: "comment-report", itemId: item.id, authorName: "Người dùng",
        body: "Không hợp lệ", createdAt: "2026-10-14T12:00:00Z", own: false }], nextCursor: null,
    });
    api.reportSocialComment.mockResolvedValue(undefined);
    mount();
    fireEvent.click(screen.getByRole("button", { name: "Bình luận Bánh mì CoVie" }));
    const report = await screen.findByRole("button", { name: "Báo cáo bình luận" });
    fireEvent.click(report);
    expect(await screen.findByText("Đã gửi báo cáo bình luận tới đội ngũ kiểm duyệt."))
      .toBeInTheDocument();
    expect(api.reportSocialComment).toHaveBeenCalledWith("comment-report");
  });

  it("does not fabricate likes after an unsuccessful server mutation", async () => {
    api.fetchSocialSummaries.mockResolvedValue({
      [item.id!]: { itemId: item.id, likes: 2, comments: 0, liked: false, saved: false },
    });
    api.changeSocialReaction.mockRejectedValue(new Error("offline"));
    mount();
    const like = await screen.findByRole("button", { name: "Thích Bánh mì CoVie" });
    await waitFor(() => expect(like).not.toBeDisabled());
    fireEvent.click(like);
    expect(await screen.findByText("Không thể thực hiện. Kiểm tra kết nối và thử lại.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Thích Bánh mì CoVie" }))
      .toHaveAttribute("aria-pressed", "false");
  });

  it.each([false, true])("keeps confirmed Like across older focus reads without resurrecting omitted items: omitted=$0", async omitted => {
    const visible = vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    const now = vi.spyOn(Date, "now");
    try {
      const initial: SocialSummary = { itemId: item.id!, likes: 4, comments: 0, liked: false, saved: false };
      const confirmed: SocialSummary = { ...initial, likes: 5, liked: true };
      const initialMap = { [item.id!]: initial };
      let finishLike!: (summary: SocialSummary) => void;
      const olderReads: Array<(summary: Record<string, SocialSummary>) => void> = [];
      api.fetchSocialSummaries.mockResolvedValueOnce(initialMap).mockResolvedValueOnce(initialMap)
        .mockImplementation(() => new Promise(resolve => olderReads.push(resolve)));
      api.changeSocialReaction.mockImplementation(() => new Promise(resolve => { finishLike = resolve; }));
      render(<>
        <SocialFeedProvider itemIds={[item.id!]} enabled><MarketplaceProductCard item={item} mode="social" /></SocialFeedProvider>
        <SocialFeedProvider itemIds={[item.id!]} enabled><MarketplaceProductCard item={item} mode="social" /></SocialFeedProvider>
      </>);
      const likes = await screen.findAllByRole("button", { name: "Thích Bánh mì CoVie" });
      await waitFor(() => expect(likes.every(button => !button.hasAttribute("disabled"))).toBe(true));
      fireEvent.click(likes[0]!);
      fireEvent(window, new Event("focus"));
      await waitFor(() => expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(4));
      await act(async () => finishLike(confirmed));
      await act(async () => olderReads.forEach(resolve => resolve(omitted ? {} : initialMap)));
      if (omitted) {
        await waitFor(() => {
          const unavailable = screen.getAllByRole("button", { name: "Thích Bánh mì CoVie" });
          expect(unavailable).toHaveLength(2);
          for (const button of unavailable) expect(button).toBeDisabled();
        });
        expect(api.changeSocialReaction).toHaveBeenCalledTimes(1);
        return;
      }
      await waitFor(() => expect(screen.getAllByRole("button", { name: "Bỏ thích Bánh mì CoVie" })).toHaveLength(2));
      for (const button of screen.getAllByRole("button", { name: "Bỏ thích Bánh mì CoVie" })) {
        expect(button).toHaveAttribute("aria-pressed", "true");
        expect(button).toHaveTextContent("5");
      }
      expect(api.changeSocialReaction).toHaveBeenCalledTimes(1);
      api.fetchSocialSummaries.mockResolvedValue({ [item.id!]: { ...confirmed, likes: 6 } });
      now.mockReturnValue(Date.now() + 31000);
      fireEvent(window, new Event("focus"));
      await waitFor(() => expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(6));
      await waitFor(() => {
        for (const button of screen.getAllByRole("button", { name: "Bỏ thích Bánh mì CoVie" })) expect(button).toHaveTextContent("6");
      });
    } finally {
      visible.mockRestore();
      now.mockRestore();
    }
  });
});
