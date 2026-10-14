import { afterEach, describe, expect, it, vi } from "vitest";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MarketplaceProductCard } from "@/features/marketplace-view/marketplace-product-card";
import { SocialFeedProvider } from "@/features/marketplace-view/social-feed";
import type { components } from "@covie/api-client";

const api = vi.hoisted(() => ({
  fetchSocialSummaries: vi.fn(),
  changeSocialReaction: vi.fn(),
  fetchSocialComments: vi.fn(),
  addSocialComment: vi.fn(),
  removeSocialComment: vi.fn(),
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
    fireEvent.click(buttons[0]);
    await waitFor(() => expect(screen.getAllByRole("button", { name: "Bỏ thích Bánh mì CoVie" })).toHaveLength(2));
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
});
