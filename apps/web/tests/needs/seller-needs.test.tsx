import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";

const { discoverMerchantNeeds, listNeedResponseCandidates, respondToNeed } = vi.hoisted(() => ({
  discoverMerchantNeeds: vi.fn(),
  listNeedResponseCandidates: vi.fn(),
  respondToNeed: vi.fn(),
}));
vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, discoverMerchantNeeds, listNeedResponseCandidates, respondToNeed };
});

afterEach(() => {
  cleanup();
  discoverMerchantNeeds.mockReset();
  listNeedResponseCandidates.mockReset();
  respondToNeed.mockReset();
});

const match = {
  need: {
    id: "need-1",
    type: "PRODUCT",
    title: "Cần mua máy lọc không khí",
    description: "Ưu tiên giao trong ngày",
    categoryId: "category",
    expiresAt: "2026-08-25T12:00:00Z",
  },
  resolvedLocation: { id: "location-1", name: "Chi nhánh Thủ Đức" },
  buyerArea: { label: "Thủ Đức" },
  distanceMeters: 1200,
  alreadyResponded: false,
  responseConstraints: { maxItems: 5 },
};

const locations = [{ id: "location-1", name: "Chi nhánh Thủ Đức", status: "ACTIVE" as const, currency: "VND" }];

const candidatePage = {
  items: [{
    locationVariantId: "offer-1",
    catalogItemId: "item-1",
    variantId: "variant-1",
    itemName: "Máy lọc không khí",
    variantName: "Màu trắng",
    currentPrice: { amount: 1_000_000, currency: "VND" },
    available: true,
    availability: { availableQuantity: 2 },
  }],
  hasMore: false,
  nextCursor: null,
  maxItems: 5,
};

it("renders only server-eligible response candidates for a matched need", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({
    items: [match],
    hasMore: false,
    nextCursor: null,
  });
  listNeedResponseCandidates.mockResolvedValueOnce(candidatePage);

  render(
    <MerchantNeedList
      merchantId="merchant-1"
      locations={locations}
    />,
  );

  await waitFor(() => expect(screen.getByText("Cần mua máy lọc không khí")).toBeInTheDocument());
  expect(screen.getByLabelText("Điểm bán đang phản hồi")).toHaveValue("location-1");
  expect(screen.getByText(/Thủ Đức · cách điểm bán 1,2 km/)).toBeInTheDocument();
  expect(screen.queryByRole("link", { name: "Tạo nhu cầu mới" })).not.toBeInTheDocument();
  expect(screen.queryByRole("group", { name: "Lọc nhu cầu theo trạng thái" })).not.toBeInTheDocument();
  expect(await screen.findByText(/Máy lọc không khí · Màu trắng · 1\.000\.000/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Phản hồi nhu cầu" })).toBeDisabled();
  expect(listNeedResponseCandidates).toHaveBeenCalledWith("need-1", "merchant-1", "location-1");
});

it("renders the authoritative empty candidate state", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({ items: [match], hasMore: false, nextCursor: null });
  listNeedResponseCandidates.mockResolvedValueOnce({ ...candidatePage, items: [] });

  render(<MerchantNeedList merchantId="merchant-1" locations={locations} />);

  expect(await screen.findByText("Chưa có nội dung phù hợp để chọn.")).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Phản hồi nhu cầu" })).toBeDisabled();
});

it("refreshes candidates after a stale submit conflict", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({ items: [match], hasMore: false, nextCursor: null });
  listNeedResponseCandidates
    .mockResolvedValueOnce(candidatePage)
    .mockResolvedValueOnce({ ...candidatePage, items: [] });
  respondToNeed.mockRejectedValueOnce(new Error("Nội dung vừa thay đổi, vui lòng chọn lại."));

  render(<MerchantNeedList merchantId="merchant-1" locations={locations} />);
  fireEvent.click(await screen.findByRole("checkbox", { name: /Máy lọc không khí/ }));
  fireEvent.click(screen.getByRole("button", { name: "Phản hồi nhu cầu" }));

  expect(await screen.findByText("Nội dung vừa thay đổi, vui lòng chọn lại.")).toBeInTheDocument();
  await waitFor(() => expect(listNeedResponseCandidates).toHaveBeenCalledTimes(2));
});

it("does not load candidates for an existing response", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({
    items: [{ ...match, alreadyResponded: true }],
    hasMore: false,
    nextCursor: null,
  });

  render(<MerchantNeedList merchantId="merchant-1" locations={locations} />);

  expect(await screen.findByText("Đã phản hồi")).toBeInTheDocument();
  expect(listNeedResponseCandidates).not.toHaveBeenCalled();
  expect(screen.queryByRole("button", { name: "Phản hồi nhu cầu" })).not.toBeInTheDocument();
});
