import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { NeedList } from "@/features/needs/need-list";

const { listMyNeeds } = vi.hoisted(() => ({ listMyNeeds: vi.fn() }));
vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, listMyNeeds };
});

afterEach(() => {
  cleanup();
  listMyNeeds.mockReset();
});

it("renders buyer need status and response count", async () => {
  listMyNeeds.mockResolvedValueOnce({
    items: [{
      id: "need-1",
      type: "PRODUCT",
      status: "ACTIVE",
      categoryId: "category",
      title: "Cần mua máy lọc không khí",
      radiusMeters: 5000,
      expiresAt: "2026-08-25T12:00:00Z",
      responseCount: 3,
      createdAt: "2026-08-24T12:00:00Z",
    }],
    hasMore: false,
    nextCursor: null,
  });

  render(<NeedList />);

  await waitFor(() => expect(screen.getByText("Cần mua máy lọc không khí")).toBeInTheDocument());
  const card = screen.getByRole("article");
  expect(within(card).getByText("Đang tìm")).toBeInTheDocument();
  expect(within(card).getByText("3 phản hồi")).toBeInTheDocument();
  expect(screen.queryByLabelText("Điểm bán đang phản hồi")).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Phản hồi nhu cầu" })).not.toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Tạo nhu cầu mới" })).toHaveAttribute("href", "/needs/new");
});

it("does not let an older status response overwrite the selected segment", async () => {
  let resolveActive: ((value: { items: never[]; hasMore: boolean; nextCursor: null }) => void) | undefined;
  const activePage = new Promise<{ items: never[]; hasMore: boolean; nextCursor: null }>((resolve) => { resolveActive = resolve; });
  listMyNeeds
    .mockReturnValueOnce(activePage)
    .mockResolvedValueOnce({
      items: [{
        id: "need-fulfilled",
        type: "PRODUCT",
        status: "FULFILLED",
        categoryId: "category",
        title: "Nhu cầu đã tìm được",
        radiusMeters: 2000,
        expiresAt: "2026-08-25T12:00:00Z",
        responseCount: 1,
        createdAt: "2026-08-24T12:00:00Z",
      }],
      hasMore: false,
      nextCursor: null,
    });

  render(<NeedList />);
  await waitFor(() => expect(listMyNeeds).toHaveBeenCalledWith("ACTIVE", undefined));
  fireEvent.click(screen.getByRole("button", { name: "Đã tìm được" }));

  expect(await screen.findByText("Nhu cầu đã tìm được")).toBeInTheDocument();
  await act(async () => { resolveActive?.({ items: [], hasMore: false, nextCursor: null }); });
  expect(screen.getByText("Nhu cầu đã tìm được")).toBeInTheDocument();
  expect(screen.queryByText("Chưa có nhu cầu phù hợp")).not.toBeInTheDocument();
});
