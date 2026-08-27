import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CatalogCreateForm } from "../../features/catalog/catalog-create-form";

const { createCatalogItem, getCategoryDetail, push, refresh } = vi.hoisted(() => ({
  createCatalogItem: vi.fn(),
  getCategoryDetail: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push, refresh }),
}));

vi.mock("../../features/catalog/catalog-api", () => ({
  createCatalogItem,
  getCategoryDetail,
}));

afterEach(() => {
  cleanup();
  createCatalogItem.mockReset();
  getCategoryDetail.mockReset();
  push.mockReset();
  refresh.mockReset();
});

it("submits the default catalog variant with an idempotency key", async () => {
  getCategoryDetail.mockResolvedValueOnce({
    id: "00000000-0000-0000-0000-000000000002",
    name: "Đồ uống",
    catalogType: "PRODUCT",
    attributes: [],
  });
  createCatalogItem.mockResolvedValueOnce({ id: "00000000-0000-0000-0000-000000000003" });

  render(
    <CatalogCreateForm
      merchantId="00000000-0000-0000-0000-000000000001"
      categories={[{
        id: "00000000-0000-0000-0000-000000000002",
        name: "Đồ uống",
        catalogType: "PRODUCT",
      }]}
      locations={[{
        id: "00000000-0000-0000-0000-000000000004",
        name: "Điểm bán A",
        status: "ACTIVE",
        currency: "VND",
      }]}
    />,
  );

  fireEvent.change(screen.getByLabelText("Tên"), { target: { value: "Cà phê sữa" } });
  fireEvent.change(screen.getByLabelText("Danh mục"), { target: { value: "00000000-0000-0000-0000-000000000002" } });
  await waitFor(() => expect(getCategoryDetail).toHaveBeenCalledWith("00000000-0000-0000-0000-000000000002"));
  await waitFor(() => expect(screen.getByRole("button", { name: "Tạo nội dung" })).not.toBeDisabled());

  fireEvent.change(screen.getByLabelText("Điểm bán"), { target: { value: "00000000-0000-0000-0000-000000000004" } });
  fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "25000" } });
  fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

  await waitFor(() => expect(createCatalogItem).toHaveBeenCalledTimes(1));
  expect(createCatalogItem).toHaveBeenCalledWith(
    "00000000-0000-0000-0000-000000000001",
    expect.any(String),
    expect.objectContaining({
      type: "PRODUCT",
      listingKind: "STANDARD",
      categoryId: "00000000-0000-0000-0000-000000000002",
      name: "Cà phê sữa",
      options: [],
      variants: [],
      locationOffers: [{
        merchantLocationId: "00000000-0000-0000-0000-000000000004",
        variantClientKey: "__default__",
        price: 25000,
        currency: "VND",
        available: true,
        inventoryMode: "UNTRACKED",
        preparationTimeMinutes: 0,
      }],
    }),
  );
  expect(push).toHaveBeenCalledWith("/seller/00000000-0000-0000-0000-000000000001/catalog");
  expect(refresh).toHaveBeenCalledTimes(1);
});
