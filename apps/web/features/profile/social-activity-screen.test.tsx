import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { SocialActivityScreen } from "./social-activity-screen";
import { CovieApiError } from "@/lib/api/api-error";

const api = vi.hoisted(() => ({ fetchMySocialActivity: vi.fn() }));
vi.mock("./social-activity-api", () => api);
beforeEach(() => vi.resetAllMocks());

it("renders literal comments, distinct comments on one item, URL tabs and append retry", async () => {
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [{ itemId: "item-1", itemName: "Bánh mới", merchantName: "Bếp An", commentId: "c1", commentExcerpt: "<script>literal</script>\nDòng hai", activityAt: "2026-10-15T00:00:00Z", itemAvailable: true }], nextCursor: "next" })
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValueOnce({ items: [{ itemId: "item-1", itemName: "Bánh mới", commentId: "c2", commentExcerpt: "Bình luận thứ hai", itemAvailable: true }] });
  const view = render(<SocialActivityScreen accountId="buyer" type="COMMENTED" />);
  expect(screen.getByRole("status")).toHaveTextContent("Đang tải");
  expect(await screen.findByText("<script>literal</script> Dòng hai")).toBeVisible();
  expect(view.container.querySelector("script")).toBeNull();
  expect(screen.getByRole("tab", { name: "Đã lưu" })).toHaveAttribute("href", "/me/activity?type=SAVED");
  const selected = screen.getByRole("tab", { name: "Bình luận" });
  expect(selected).toHaveAttribute("aria-selected", "true");
  fireEvent.keyDown(selected, { key: "ArrowRight" });
  expect(screen.getByRole("tab", { name: "Đã thích" })).toHaveFocus();
  expect(screen.getByRole("link", { name: "Bánh mới" })).toHaveAttribute("href", "/items/item-1");
  fireEvent.click(screen.getByRole("button", { name: "Xem thêm" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải hoạt động");
  expect(screen.getByText("<script>literal</script> Dòng hai")).toBeVisible();
  fireEvent.click(screen.getByRole("button", { name: "Thử lại" }));
  expect(await screen.findByText("Bình luận thứ hai")).toBeVisible();
  expect(screen.getAllByRole("link", { name: "Bánh mới" })).toHaveLength(2);
  expect(api.fetchMySocialActivity.mock.calls[2]?.slice(0, 2)).toEqual(["COMMENTED", "next"]);
});

it("has empty and unauthorized states without retaining private rows", async () => {
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [] });
  const view = render(<SocialActivityScreen accountId="buyer" type="LIKED" />);
  expect(await screen.findByText("Bạn chưa thích nội dung nào.")).toBeVisible();
  api.fetchMySocialActivity.mockRejectedValueOnce(new CovieApiError(403, "FORBIDDEN", "Denied"));
  view.rerender(<SocialActivityScreen accountId="other" type="LIKED" />);
  expect(await screen.findByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fme%2Factivity%3Ftype%3DLIKED");
  expect(screen.queryByText("Bạn chưa thích nội dung nào.")).toBeNull();
  await waitFor(() => expect(api.fetchMySocialActivity).toHaveBeenCalledTimes(2));
});

it("falls back after a broken thumbnail and keeps unavailable content generic", async () => {
  api.fetchMySocialActivity.mockResolvedValue({ items: [
    { itemId: "visible", itemName: "Ảnh lỗi", coverUrl: "https://media.test.covie/broken.jpg", itemAvailable: true },
    { itemId: "hidden", itemName: "Restricted name", merchantName: "Restricted merchant", commentExcerpt: "Restricted body", commentId: "hidden-comment", coverUrl: "https://media.test.covie/hidden.jpg", itemAvailable: false },
  ] });
  const view = render(<SocialActivityScreen accountId="buyer" type="COMMENTED" />);
  expect(await screen.findByText("Nội dung hiện không khả dụng")).toBeVisible();
  expect(view.container.querySelectorAll("img")).toHaveLength(1);
  fireEvent.error(view.container.querySelector("img")!);
  expect(view.container.querySelector("img")).toBeNull();
  expect(view.container.textContent).not.toMatch(/Restricted/);
  expect(view.container.querySelector('a[href="/items/hidden"]')).toBeNull();
});
