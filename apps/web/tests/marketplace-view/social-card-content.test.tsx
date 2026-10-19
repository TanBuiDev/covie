import { afterEach, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MarketplaceProductCard } from "@/features/marketplace-view/marketplace-product-card";
import { SocialFeedProvider } from "@/features/marketplace-view/social-feed";
import type { components } from "@covie/api-client";
import { CovieApiError } from "@/lib/api/api-error";
import styles from "@/features/marketplace-view/marketplace-view.module.css";

const api = vi.hoisted(() => ({ fetchSocialSummaries: vi.fn(), fetchSocialPreviews: vi.fn(), changeSocialReaction: vi.fn() }));
vi.mock("@/features/marketplace-view/social-api", async (original) => ({
  ...await original<typeof import("@/features/marketplace-view/social-api")>(), ...api,
}));
const item = { id: "item", name: "Bánh mì", type: "PRODUCT", merchant: { name: "CoVie" },
  primaryMedia: { url: "https://media.test/legacy.jpg" } } as components["schemas"]["CatalogItemSummaryResponse"];
const media = [0, 1].map((index) => ({ id: `media-${index}`, mimeType: "image/jpeg",
  url: `https://media.test/${index}.jpg`, sortOrder: index, isCover: index === 0 }));
function mount(caption = "Caption\n<script>literal</script>", images = media) {
  api.fetchSocialSummaries.mockResolvedValue({});
  api.fetchSocialPreviews.mockResolvedValue({ item: { itemId: "item", socialCaption: caption, media: images } });
  return render(<SocialFeedProvider itemIds={["item"]} enabled previewsEnabled>
    <MarketplaceProductCard item={item} mode="social" />
  </SocialFeedProvider>);
}
afterEach(() => vi.resetAllMocks());

it("reserves anonymous gallery and text space while authoritative previews load", () => {
  api.fetchSocialSummaries.mockResolvedValue({});
  api.fetchSocialPreviews.mockImplementation(() => new Promise(() => undefined));
  render(<SocialFeedProvider itemIds={["item"]} enabled previewsEnabled>
    <MarketplaceProductCard item={item} mode="social" />
  </SocialFeedProvider>);
  const card = screen.getByRole("status").closest("article");
  expect(card?.className.split(" ")).toContain(styles.social);
  expect(card?.querySelector(`.${styles.media}`)).toHaveAttribute("aria-hidden", "true");
  expect(card?.querySelector(`.${styles.previewLoadingBody}`)).toHaveAttribute("aria-hidden", "true");
  expect(card?.querySelector("img,a,video")).toBeNull();
  expect(card).not.toHaveTextContent("Bánh mì");
});

it("revalidates duplicate Social previews after a reaction confirms the item is unavailable", async () => {
  const preview = { item: { itemId: "item", socialCaption: "Caption before moderation", media } };
  const pending: Array<(value: Record<string, never>) => void> = [];
  api.fetchSocialSummaries.mockResolvedValue({ item: { itemId: "item", likes: 1, comments: 0, liked: false, saved: false } });
  api.fetchSocialPreviews.mockResolvedValueOnce(preview).mockResolvedValueOnce(preview)
    .mockImplementation(() => new Promise(resolve => pending.push(resolve)));
  api.changeSocialReaction.mockRejectedValue(new CovieApiError(404, "RESOURCE_NOT_FOUND", "Resource not found."));
  const view = render(<>
    <SocialFeedProvider itemIds={["item"]} enabled previewsEnabled><MarketplaceProductCard item={item} mode="social" /></SocialFeedProvider>
    <SocialFeedProvider itemIds={["item"]} enabled previewsEnabled><MarketplaceProductCard item={item} mode="social" /></SocialFeedProvider>
  </>);
  await waitFor(() => expect(screen.getAllByText("Caption before moderation")).toHaveLength(2));
  const like = screen.getAllByRole("button", { name: "Thích Bánh mì" })[0]!;
  await waitFor(() => expect(like).not.toBeDisabled());
  fireEvent.click(like);
  await waitFor(() => expect(api.fetchSocialPreviews).toHaveBeenCalledTimes(4));
  expect(api.changeSocialReaction).toHaveBeenCalledTimes(1);
  expect(screen.queryByText("Caption before moderation")).toBeNull();
  expect(screen.queryByRole("img")).toBeNull();
  await act(async () => pending.forEach(resolve => resolve({})));
  await waitFor(() => expect(view.container.querySelector("article")).toBeNull());
  expect(api.fetchSocialSummaries).toHaveBeenCalledTimes(2);
});

it.each([
  { previewsEnabled: true, status: 429, message: "Bạn thao tác quá nhanh. Hãy thử lại sau." },
  { previewsEnabled: false, status: 404, message: "Sản phẩm hoặc nội dung này hiện không còn khả dụng." },
])("preserves existing content for status $status with previewsEnabled=$previewsEnabled", async ({ previewsEnabled, status, message }) => {
  api.fetchSocialSummaries.mockResolvedValue({ item: { itemId: "item", likes: 1, comments: 0, liked: false, saved: false } });
  api.fetchSocialPreviews.mockResolvedValue({ item: { itemId: "item", socialCaption: "Still visible caption", media } });
  api.changeSocialReaction.mockRejectedValue(new CovieApiError(status, "HTTP_ERROR", "Request failed."));
  render(<SocialFeedProvider itemIds={["item"]} enabled previewsEnabled={previewsEnabled}>
    <MarketplaceProductCard item={item} mode="social" />
  </SocialFeedProvider>);
  const like = await screen.findByRole("button", { name: "Thích Bánh mì" });
  await waitFor(() => expect(like).not.toBeDisabled());
  fireEvent.click(like);
  expect(await screen.findByText(message)).toBeInTheDocument();
  if (previewsEnabled) expect(screen.getByText("Still visible caption")).toBeInTheDocument();
  else expect(screen.getByAltText("Bánh mì")).toHaveAttribute("src", "https://media.test/legacy.jpg");
  expect(api.fetchSocialPreviews).toHaveBeenCalledTimes(previewsEnabled ? 1 : 0);
});

it("renders caption as literal text and navigates gallery by controls and keyboard without a nested link", async () => {
  const view = mount();
  expect(await screen.findByText("Caption <script>literal</script>")).toBeInTheDocument();
  expect(view.container.querySelector("script")).toBeNull();
  expect(screen.getByAltText("Bánh mì — Ảnh 1/2")).toHaveAttribute("src", "https://media.test/0.jpg");
  const next = screen.getByRole("button", { name: "Ảnh tiếp theo" });
  expect(next.closest("a")).toBeNull();
  fireEvent.click(next);
  expect(screen.getByAltText("Bánh mì — Ảnh 2/2")).toBeInTheDocument();
  fireEvent.keyDown(screen.getByRole("region", { name: "Ảnh Bánh mì" }), { key: "ArrowLeft" });
  expect(screen.getByAltText("Bánh mì — Ảnh 1/2")).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Xem Bánh mì" })).toHaveAttribute("href", "/items/item");
});

it("expands long caption, collapses it and keeps image failures local to the selected image", async () => {
  mount("Nội dung ".repeat(70));
  const expand = await screen.findByRole("button", { name: "Xem thêm nội dung" });
  expect(expand).toHaveAttribute("aria-expanded", "false");
  fireEvent.click(expand);
  const collapse = screen.getByRole("button", { name: "Thu gọn nội dung" });
  expect(collapse).toHaveAttribute("aria-expanded", "true");
  fireEvent.click(collapse);
  fireEvent.error(screen.getByAltText("Bánh mì — Ảnh 1/2"));
  expect(screen.getByText("Không tải được ảnh")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Ảnh tiếp theo" }));
  expect(screen.getByAltText("Bánh mì — Ảnh 2/2")).toBeInTheDocument();
});

it("does not render slider controls for one image or fabricate a cover for an empty gallery", async () => {
  const view = mount("", media.slice(0, 1));
  await screen.findByAltText("Bánh mì — Ảnh 1/1");
  expect(screen.queryByRole("button", { name: "Ảnh tiếp theo" })).toBeNull();
  expect(screen.queryByRole("button", { name: "Xem thêm nội dung" })).toBeNull();
  view.unmount();
  mount("", []);
  expect(await screen.findByText("Chưa có ảnh")).toBeInTheDocument();
  expect(screen.queryByRole("img")).toBeNull();
});

it("keeps Catalog on its old cover without fetching preview", async () => {
  render(<SocialFeedProvider itemIds={["item"]} enabled={false} previewsEnabled>
    <MarketplaceProductCard item={item} mode="catalog" />
  </SocialFeedProvider>);
  expect(screen.getByAltText("Bánh mì")).toHaveAttribute("src", "https://media.test/legacy.jpg");
  expect(api.fetchSocialPreviews).not.toHaveBeenCalled();
});

it("changes image only for a horizontal swipe and cancels its incidental detail click", async () => {
  mount();
  await screen.findByAltText("Bánh mì — Ảnh 1/2");
  const gallery = screen.getByRole("region", { name: "Ảnh Bánh mì" });
  fireEvent.touchStart(gallery, { touches: [{ clientX: 200, clientY: 100 }] });
  fireEvent.touchEnd(gallery, { changedTouches: [{ clientX: 180, clientY: 230 }] });
  expect(screen.getByAltText("Bánh mì — Ảnh 1/2")).toBeInTheDocument();
  fireEvent.touchStart(gallery, { touches: [{ clientX: 200, clientY: 100 }] });
  fireEvent.touchEnd(gallery, { changedTouches: [{ clientX: 80, clientY: 110 }] });
  expect(screen.getByAltText("Bánh mì — Ảnh 2/2")).toBeInTheDocument();
  expect(screen.getByText("Sản phẩm")).toBeInTheDocument();
  expect(fireEvent.click(screen.getByRole("link", { name: "Xem Bánh mì" }))).toBe(false);
});

it("offers preview retry and does not expose a stale card when the authoritative batch omits it", async () => {
  api.fetchSocialSummaries.mockResolvedValue({});
  api.fetchSocialPreviews.mockRejectedValueOnce(new Error("offline")).mockResolvedValueOnce({});
  const view = render(<SocialFeedProvider itemIds={["item"]} enabled previewsEnabled>
    <MarketplaceProductCard item={item} mode="social" />
  </SocialFeedProvider>);
  const retry = await screen.findByRole("button", { name: "Thử lại nội dung bài đăng" });
  expect(screen.queryByAltText("Bánh mì")).toBeNull();
  fireEvent.click(retry);
  await waitFor(() => expect(view.container.querySelector("article")).toBeNull());
  expect(screen.queryByText("Bánh mì")).toBeNull();
});
