import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
import { CatalogEditForm } from "./catalog-edit-form";

const mocks = vi.hoisted(() => ({ updateItem: vi.fn(), updateOffer: vi.fn(), transition: vi.fn(), refresh: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: mocks.refresh }) }));
vi.mock("./catalog-api", () => ({
  updateCatalogItem: mocks.updateItem,
  updateCatalogLocationOffer: mocks.updateOffer,
  transitionCatalogItem: mocks.transition,
}));

beforeEach(() => {
  Object.values(mocks).forEach((mock) => mock.mockReset());
  mocks.updateItem.mockResolvedValue(undefined);
  mocks.updateOffer.mockResolvedValue(undefined);
  mocks.transition.mockResolvedValue(undefined);
});

const item = {
  id: "item-1",
  type: "PRODUCT" as const,
  listingKind: "STANDARD" as const,
  categoryId: "category-1",
  name: "Máy lọc",
  description: "Mô tả",
  listingStatus: "DRAFT" as const,
  moderationStatus: "NOT_REQUIRED" as const,
  mediaIds: [],
  variants: [],
  locationOffers: [{
    id: "offer-1", merchantLocationId: "location-1", variantId: "variant-1", price: 1_000_000,
    currency: "VND", available: true, inventoryMode: "TRACKED" as const, preparationTimeMinutes: 30, version: 4,
  }],
  lifecycle: { canPublish: true, canPause: false, canActivate: false, canArchive: true },
  version: 7,
};

it("sends exact aggregate and offer versions on edits", async () => {
  render(<CatalogEditForm merchantId="merchant-1" item={item} />);
  fireEvent.change(screen.getByLabelText("Tên"), { target: { value: "Máy lọc mới" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
  await waitFor(() => expect(mocks.updateItem).toHaveBeenCalledWith("merchant-1", "item-1", 7, {
    name: "Máy lọc mới", description: "Mô tả",
  }));

  fireEvent.change(screen.getByLabelText("Giá"), { target: { value: "1200000" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu điểm bán" }));
  await waitFor(() => expect(mocks.updateOffer).toHaveBeenCalledWith("merchant-1", "item-1", "offer-1", 4, {
    price: 1_200_000, available: true, inventoryMode: "TRACKED", preparationTimeMinutes: 30,
  }));
});

it("renders only authoritative lifecycle actions", () => {
  render(<CatalogEditForm merchantId="merchant-1" item={item} />);
  expect(screen.getByRole("button", { name: "Đăng nội dung" })).toBeInTheDocument();
  expect(screen.queryByRole("button", { name: "Tạm dừng" })).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Lưu trữ" })).toBeInTheDocument();
});
