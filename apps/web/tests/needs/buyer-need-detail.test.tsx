import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { NeedDetail } from "@/features/needs/need-detail";

const { cancelNeed, getMyNeed, listNeedAddresses, listNeedResponses } = vi.hoisted(() => ({
  cancelNeed: vi.fn(),
  getMyNeed: vi.fn(),
  listNeedAddresses: vi.fn(),
  listNeedResponses: vi.fn(),
}));

vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, cancelNeed, getMyNeed, listNeedAddresses, listNeedResponses };
});

afterEach(() => {
  cleanup();
  getMyNeed.mockReset();
  listNeedAddresses.mockReset();
  listNeedResponses.mockReset();
  cancelNeed.mockReset();
});

function arrangeNeedResponse(actionable: boolean, canCancel = true) {
  getMyNeed.mockResolvedValueOnce({
    id: "need-1",
    type: "PRODUCT",
    status: "ACTIVE",
    categoryId: "category-1",
    title: "Cần mua máy lọc không khí",
    queryText: "máy lọc không khí",
    description: "Ưu tiên giao trong ngày",
    requestedQuantity: 1,
    locationSummary: { addressId: "address-1", areaId: "area-1", timezone: "Asia/Ho_Chi_Minh" },
    radiusMeters: 5000,
    expiresAt: "2026-08-29T12:00:00Z",
    responseCount: 1,
    fulfilledOrderId: "",
    canCancel,
    createdAt: "2026-08-28T12:00:00Z",
    closedAt: "",
  });
  listNeedAddresses.mockResolvedValueOnce([{
    id: "address-1",
    label: "Nhà",
    addressText: "1 CoVie",
    defaultAddress: true,
  }]);
  listNeedResponses.mockResolvedValueOnce({
    items: [{
      id: "response-1",
      needRequestId: "need-1",
      merchantId: "merchant-1",
      merchantName: "Cửa hàng CoVie",
      merchantLocationId: "location-1",
      merchantLocationName: "Chi nhánh Thủ Đức",
      distanceMeters: 1200,
      status: "ACTIVE",
      note: "Có thể giao hôm nay",
      version: 1,
      createdAt: "2026-08-28T12:00:00Z",
      items: [{
        id: "response-item-1",
        locationVariantId: "location-variant-1",
        catalogItemId: "catalog-item-1",
        variantId: "variant-1",
        priceAtResponse: { amount: 1_000_000, currency: "VND" },
        currentPrice: { amount: 1_000_000, currency: "VND" },
        available: true,
        actionable,
      }],
    }],
    hasMore: false,
    nextCursor: null,
  });
}

it("shows need cancellation only from explicit backend eligibility", async () => {
  arrangeNeedResponse(false);
  cancelNeed.mockResolvedValueOnce({});

  render(<NeedDetail needId="need-1" />);

  await waitFor(() => expect(screen.getByRole("heading", { name: "Cần mua máy lọc không khí" })).toBeInTheDocument());
  expect(screen.getByRole("button", { name: /hủy nhu cầu/i })).toBeInTheDocument();
  expect(await screen.findByText("Không còn khả dụng")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Nhận tại cửa hàng" })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Giao đến địa chỉ" })).not.toBeInTheDocument();
});

it("shows response checkout actions only when the backend marks the item actionable", async () => {
  arrangeNeedResponse(true);

  render(<NeedDetail needId="need-1" />);

  expect(await screen.findByRole("button", { name: "Nhận tại cửa hàng" })).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Giao đến địa chỉ" })).toBeEnabled();
  expect(screen.queryByText("Không còn khả dụng")).not.toBeInTheDocument();
});

it("hides cancellation when canCancel is false", async () => {
  arrangeNeedResponse(false, false);
  render(<NeedDetail needId="need-1" />);
  await screen.findByRole("heading", { name: "Cần mua máy lọc không khí" });
  expect(screen.queryByRole("button", { name: /hủy nhu cầu/i })).not.toBeInTheDocument();
});

it("refreshes the authoritative need after a cancellation conflict", async () => {
  arrangeNeedResponse(false);
  cancelNeed.mockRejectedValueOnce(new Error("Nhu cầu không còn ở trạng thái có thể hủy."));
  getMyNeed.mockResolvedValueOnce({
    id: "need-1", type: "PRODUCT", status: "EXPIRED", categoryId: "category-1", title: "Cần mua máy lọc không khí",
    queryText: "máy lọc không khí", description: "", requestedQuantity: 1,
    locationSummary: { addressId: "address-1", areaId: "area-1", timezone: "Asia/Ho_Chi_Minh" },
    radiusMeters: 5000, expiresAt: "2026-08-29T12:00:00Z", responseCount: 1, fulfilledOrderId: "",
    canCancel: false, createdAt: "2026-08-28T12:00:00Z", closedAt: "",
  });
  render(<NeedDetail needId="need-1" />);
  fireEvent.click(await screen.findByRole("button", { name: "Hủy nhu cầu" }));
  fireEvent.click(screen.getByRole("button", { name: "Xác nhận hủy" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("không còn ở trạng thái");
  await waitFor(() => expect(screen.queryByRole("button", { name: "Hủy nhu cầu" })).not.toBeInTheDocument());
});
