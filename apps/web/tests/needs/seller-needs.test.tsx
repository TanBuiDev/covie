import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";

const { discoverMerchantNeeds } = vi.hoisted(() => ({ discoverMerchantNeeds: vi.fn() }));
vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, discoverMerchantNeeds };
});

afterEach(() => {
  cleanup();
  discoverMerchantNeeds.mockReset();
});

it("renders a matched need without inventing an ineligible response action", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({
    items: [{
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
    }],
    hasMore: false,
    nextCursor: null,
  });

  render(
    <MerchantNeedList
      merchantId="merchant-1"
      locations={[{ id: "location-1", name: "Chi nhánh Thủ Đức", status: "ACTIVE", currency: "VND" }]}
    />,
  );

  await waitFor(() => expect(screen.getByText("Cần mua máy lọc không khí")).toBeInTheDocument());
  expect(screen.getByLabelText("Điểm bán đang phản hồi")).toHaveValue("location-1");
  expect(screen.getByText(/Thủ Đức · cách điểm bán 1,2 km/)).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Phản hồi nhu cầu" })).not.toBeInTheDocument();
  expect(screen.getByText(/Hệ thống chưa cung cấp danh sách mục phù hợp/)).toBeInTheDocument();
});
