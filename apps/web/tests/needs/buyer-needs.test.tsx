import { cleanup, render, screen, waitFor } from "@testing-library/react";
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
  expect(screen.getByText(/ACTIVE · 3 phản hồi/)).toBeInTheDocument();
  expect(screen.getByRole("link", { name: "Tạo nhu cầu mới" })).toHaveAttribute("href", "/needs/new");
});
