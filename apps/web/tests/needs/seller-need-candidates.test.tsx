import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { MerchantNeedList } from "@/features/needs/merchant-need-list";
import { MerchantNeedResponseForm } from "@/features/needs/merchant-need-response-form";

const { discoverMerchantNeeds, getMerchantNeedResponse, listEligibleNeedItems, respondToNeed } = vi.hoisted(() => ({
  discoverMerchantNeeds: vi.fn(),
  getMerchantNeedResponse: vi.fn(),
  listEligibleNeedItems: vi.fn(),
  respondToNeed: vi.fn(),
}));
vi.mock("@/features/needs/need-api", async () => {
  const actual = await vi.importActual<typeof import("@/features/needs/need-api")>("@/features/needs/need-api");
  return { ...actual, discoverMerchantNeeds, getMerchantNeedResponse, listEligibleNeedItems, respondToNeed };
});

const locations = [
  { id: "location-1", name: "Điểm bán 1", status: "ACTIVE", currency: "VND" },
  { id: "location-2", name: "Điểm bán 2", status: "ACTIVE", currency: "VND" },
] as const;
const matches = ["need-1", "need-2"].map((id) => ({
  need: { id, type: "PRODUCT", title: `Cần ${id}`, description: "", categoryId: "category-1", expiresAt: "2099-01-01T00:00:00Z" },
  resolvedLocation: { id: "location-1", name: "Điểm bán 1" },
  buyerArea: { label: "Thủ Đức" },
  distanceMeters: 850,
  alreadyResponded: false,
  responseConstraints: { maxItems: 3 },
}));
const offer = (id: string, name = id) => ({
  locationVariantId: id, catalogItemId: `item-${id}`, variantId: `variant-${id}`,
  catalogItemName: name, variantName: "Loại chuẩn", currentPrice: { amount: 18000, currency: "VND" }, available: true,
});
const eligiblePage = (ids: string[], nextCursor: string | null = null, maxResponseItems = 3) => ({
  items: ids.map((id) => offer(id, `Nội dung ${id}`)), hasMore: Boolean(nextCursor), nextCursor, maxResponseItems,
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}
function mount() {
  return render(<MerchantNeedList merchantId="merchant-1" locations={[...locations]} />);
}

beforeEach(() => {
  discoverMerchantNeeds.mockResolvedValue({ items: matches, hasMore: false, nextCursor: null });
  respondToNeed.mockResolvedValue({ id: "response-1", items: [] });
});
afterEach(() => {
  cleanup();
  discoverMerchantNeeds.mockReset();
  getMerchantNeedResponse.mockReset();
  listEligibleNeedItems.mockReset();
  respondToNeed.mockReset();
});

it("loads the merchant-owned response instead of offering a duplicate submission", async () => {
  discoverMerchantNeeds.mockResolvedValueOnce({
    items: [{ ...matches[0], alreadyResponded: true }], hasMore: false, nextCursor: null,
  });
  getMerchantNeedResponse.mockResolvedValueOnce({
    id: "response-1",
    needRequestId: "need-1",
    merchantId: "merchant-1",
    merchantName: "Chợ Xanh",
    merchantLocationId: "location-1",
    merchantLocationName: "Điểm bán 1",
    distanceMeters: 850,
    status: "ACTIVE",
    note: "Có thể giao trong chiều nay.",
    version: 0,
    createdAt: "2026-09-18T08:00:00Z",
    items: [{
      id: "response-item-1",
      locationVariantId: "offer-1",
      catalogItemId: "item-1",
      catalogItemName: "Rau củ theo mùa",
      variantId: "variant-1",
      priceAtResponse: { amount: 85000, currency: "VND" },
      currentPrice: { amount: 85000, currency: "VND" },
      available: true,
      actionable: true,
    }],
  });

  mount();
  fireEvent.click(await screen.findByRole("button", { name: /Xem phản hồi need-1/ }));

  expect(getMerchantNeedResponse).toHaveBeenCalledWith("merchant-1", "need-1", "location-1");
  expect(await screen.findByRole("region", { name: "Phản hồi đã gửi" })).toBeInTheDocument();
  expect(screen.getByText("Phản hồi đang hoạt động")).toBeInTheDocument();
  expect(screen.getByText("Rau củ theo mùa")).toBeInTheDocument();
  expect(screen.getByText("85.000 đ")).toBeInTheDocument();
  expect(screen.getByText("Có thể giao trong chiều nay.")).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Phản hồi nhu cầu" })).not.toBeInTheDocument();
});

it("loads only the selected Need/location before exposing server-labelled offers and posts only returned IDs", async () => {
  const pending = deferred<ReturnType<typeof eligiblePage>>();
  listEligibleNeedItems.mockReturnValueOnce(pending.promise);
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  expect(listEligibleNeedItems).toHaveBeenCalledWith("merchant-1", "need-1", "location-1", undefined);
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  pending.resolve(eligiblePage(["offer-1"]));
  fireEvent.click(await screen.findByRole("checkbox", { name: /Nội dung offer-1/ }));
  expect(screen.getByText(/18.000/)).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Phản hồi nhu cầu" }));
  await waitFor(() => expect(respondToNeed).toHaveBeenCalledWith(
    "need-1", { merchantLocationId: "location-1", locationVariantIds: ["offer-1"], note: undefined }, expect.any(String),
  ));
});

it("honors the page's server maximum instead of the discovery summary", async () => {
  listEligibleNeedItems.mockResolvedValueOnce(eligiblePage(["offer-1", "offer-2"], null, 1));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  fireEvent.click(await screen.findByRole("checkbox", { name: /Nội dung offer-1/ }));
  expect(screen.getByRole("checkbox", { name: /Nội dung offer-2/ })).toBeDisabled();
  expect(screen.getByText(/Đã chọn 1\/1/)).toBeInTheDocument();
});

it("shows an empty state and retries a failed candidate read without displaying checkboxes", async () => {
  listEligibleNeedItems.mockRejectedValueOnce(new Error("Tạm thời không tải được"))
    .mockResolvedValueOnce(eligiblePage([]));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  expect(await screen.findByText("Tạm thời không tải được")).toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Thử tải lại nội dung" }));
  expect(await screen.findByText("Chưa có nội dung phù hợp để chọn.")).toBeInTheDocument();
  expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
});

it("loads a cursor page for the same Need/location and retains only server-returned options", async () => {
  listEligibleNeedItems.mockResolvedValueOnce(eligiblePage(["offer-1"], "cursor-1"))
    .mockResolvedValueOnce(eligiblePage(["offer-2"]));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  fireEvent.click(await screen.findByRole("button", { name: "Tải thêm nội dung" }));
  expect(listEligibleNeedItems).toHaveBeenLastCalledWith("merchant-1", "need-1", "location-1", "cursor-1");
  expect(await screen.findByRole("checkbox", { name: /Nội dung offer-2/ })).toBeInTheDocument();
  expect(screen.getAllByRole("checkbox")).toHaveLength(2);
});

it("drops a stale candidate page after switching locations", async () => {
  const stale = deferred<ReturnType<typeof eligiblePage>>();
  listEligibleNeedItems.mockReturnValueOnce(stale.promise);
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  fireEvent.change(screen.getByLabelText("Điểm bán đang phản hồi"), { target: { value: "location-2" } });
  stale.resolve(eligiblePage(["stale-offer"]));
  await waitFor(() => expect(discoverMerchantNeeds).toHaveBeenLastCalledWith("merchant-1", "location-2"));
  expect(screen.queryByRole("checkbox", { name: /stale-offer/ })).not.toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Phản hồi nhu cầu" })).not.toBeInTheDocument();
});

it("drops the first Need's stale candidates after selecting a second Need", async () => {
  const stale = deferred<ReturnType<typeof eligiblePage>>();
  listEligibleNeedItems.mockReturnValueOnce(stale.promise).mockResolvedValueOnce(eligiblePage(["offer-2"]));
  mount();
  fireEvent.click(await screen.findByRole("button", { name: "Phản hồi need-1" }));
  fireEvent.click(screen.getByRole("button", { name: "Phản hồi need-2" }));
  stale.resolve(eligiblePage(["stale-offer"]));
  expect(await screen.findByRole("checkbox", { name: /Nội dung offer-2/ })).toBeInTheDocument();
  expect(screen.queryByRole("checkbox", { name: /stale-offer/ })).not.toBeInTheDocument();
});

it("does not revive a selected ID when a changed candidate set later contains that ID again", () => {
  const props = { needId: "need-1", merchantLocationId: "location-1", maxItems: 3 };
  const view = render(<MerchantNeedResponseForm {...props} offers={[offer("offer-1")]} />);
  fireEvent.click(screen.getByRole("checkbox", { name: /offer-1/ }));
  expect(screen.getByText(/Đã chọn 1\/3/)).toBeInTheDocument();
  view.rerender(<MerchantNeedResponseForm {...props} offers={[offer("offer-2")]} />);
  expect(screen.getByText(/Đã chọn 0\/3/)).toBeInTheDocument();
  view.rerender(<MerchantNeedResponseForm {...props} offers={[offer("offer-1")]} />);
  expect(screen.getByRole("checkbox", { name: /offer-1/ })).not.toBeChecked();
  expect(screen.getByRole("button", { name: "Phản hồi nhu cầu" })).toBeDisabled();
});
