import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SellerSocialComments } from "./seller-social-comments";

const api = vi.hoisted(() => ({ fetchSocialComments: vi.fn() }));
vi.mock("@/features/marketplace-view/social-api", async original => ({
  ...await original<typeof import("@/features/marketplace-view/social-api")>(), ...api,
}));
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it("loads the existing public thread only when opened and restores focus without losing the draft", async () => {
  api.fetchSocialComments.mockResolvedValue({ items: [{ id: "comment-1", itemId: "item-1", authorName: "Buyer",
    body: "<script>Literal buyer comment</script>", createdAt: "2026-10-15T00:00:00Z", own: false }], nextCursor: null });
  const view = render(<><input aria-label="Existing draft" defaultValue="Unsaved caption" /><SellerSocialComments itemId="item-1" /></>);
  expect(api.fetchSocialComments).not.toHaveBeenCalled();
  const trigger = screen.getByRole("button", { name: "Xem bình luận" });
  fireEvent.click(trigger);
  expect(await screen.findByText("<script>Literal buyer comment</script>")).toBeInTheDocument();
  expect(view.container.querySelector("script")).toBeNull();
  expect(api.fetchSocialComments).toHaveBeenCalledExactlyOnceWith("item-1");
  expect(screen.getByRole("button", { name: "Đóng" })).toHaveFocus();
  fireEvent.keyDown(screen.getByRole("region", { name: "Bình luận sản phẩm" }), { key: "Escape" });
  expect(screen.queryByRole("region", { name: "Bình luận sản phẩm" })).toBeNull();
  expect(trigger).toHaveFocus();
  expect(screen.getByRole("textbox", { name: "Existing draft" })).toHaveValue("Unsaved caption");
});

it("does not fabricate comments or link to an Admin/public detail route when the thread is unavailable", async () => {
  api.fetchSocialComments.mockRejectedValue({ status: 404 });
  render(<SellerSocialComments itemId="private-item" />);
  fireEvent.click(screen.getByRole("button", { name: "Xem bình luận" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bình luận.");
  expect(screen.queryByText("Chưa có bình luận nào.")).toBeNull();
  expect(screen.queryByRole("link")).toBeNull();
  await waitFor(() => expect(api.fetchSocialComments).toHaveBeenCalledExactlyOnceWith("private-item"));
});
