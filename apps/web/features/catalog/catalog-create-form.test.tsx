import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CatalogCreateForm } from "./catalog-create-form";

const mocks = vi.hoisted(() => ({
  createCatalogItem: vi.fn(),
  getCategoryDetail: vi.fn(),
  push: vi.fn(),
  refresh: vi.fn(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, refresh: mocks.refresh }),
}));

vi.mock("./catalog-api", () => ({
  createCatalogItem: mocks.createCatalogItem,
  getCategoryDetail: mocks.getCategoryDetail,
}));

vi.mock("@/features/media/direct-image-upload", () => ({
  uploadImage: vi.fn(),
}));

type Category = components["schemas"]["CategorySummary"];
type CategoryDetail = components["schemas"]["CategoryDetail"];
type Location = components["schemas"]["MerchantLocationResponse"];

const serviceCategories: Category[] = [{
  id: "cat-service",
  name: "Vệ sinh máy lạnh",
  catalogType: "SERVICE",
}];

const productCategories: Category[] = [{
  id: "cat-resale",
  name: "Đồ gia dụng",
  catalogType: "PRODUCT",
}];

const locations: Location[] = [{
  id: "loc-1",
  name: "Grand Park",
  addressText: "TP.HCM",
  serviceEnabled: true,
}];

function fillBaseFields({ categoryId, name = "Nội dung thử" }: { categoryId: string; name?: string }) {
  fireEvent.change(screen.getByLabelText("Tên"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("Danh mục"), { target: { value: categoryId } });
  fireEvent.change(screen.getByLabelText("Điểm bán"), { target: { value: "loc-1" } });
}

beforeEach(() => {
  mocks.createCatalogItem.mockReset();
  mocks.getCategoryDetail.mockReset();
  mocks.push.mockReset();
  mocks.refresh.mockReset();
  mocks.createCatalogItem.mockResolvedValue({});
});

describe("CatalogCreateForm Seller Mobile contracts", () => {
  it("creates one independently priced and available location offer per service variant", async () => {
    const detail: CategoryDetail = {
      id: "cat-service",
      name: "Vệ sinh máy lạnh",
      catalogType: "SERVICE",
      attributes: [],
    };
    mocks.getCategoryDetail.mockResolvedValue(detail);

    render(<CatalogCreateForm merchantId="merchant-1" categories={serviceCategories} locations={locations} />);

    fireEvent.change(screen.getByLabelText("Loại"), { target: { value: "SERVICE" } });
    fillBaseFields({ categoryId: "cat-service", name: "Vệ sinh máy lạnh" });

    await waitFor(() => expect(mocks.getCategoryDetail).toHaveBeenCalledWith("cat-service"));

    fireEvent.click(screen.getByLabelText("Dịch vụ có nhiều gói"));
    fireEvent.change(screen.getByLabelText("Tên lựa chọn"), { target: { value: "Số máy" } });
    fireEvent.change(screen.getByLabelText("Các giá trị"), { target: { value: "1 máy, 2 máy" } });

    const prices = screen.getAllByLabelText(/Giá chính xác \(VND\)/);
    expect(prices).toHaveLength(2);
    fireEvent.change(prices[0], { target: { value: "150000" } });
    fireEvent.change(prices[1], { target: { value: "280000" } });

    const availability = screen.getAllByLabelText("Đang nhận đơn");
    expect(availability).toHaveLength(2);
    fireEvent.click(availability[1]);

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(1));
    const [merchantId, idempotencyKey, body] = mocks.createCatalogItem.mock.calls[0];

    expect(merchantId).toBe("merchant-1");
    expect(idempotencyKey).toEqual(expect.any(String));
    expect(body).toMatchObject({
      type: "SERVICE",
      listingKind: "STANDARD",
      categoryId: "cat-service",
      name: "Vệ sinh máy lạnh",
      options: [{
        name: "Số máy",
        values: [{ value: "1 máy" }, { value: "2 máy" }],
      }],
      variants: [{ name: "1 máy" }, { name: "2 máy" }],
      locationOffers: [
        {
          merchantLocationId: "loc-1",
          price: 150000,
          currency: "VND",
          available: true,
          preparationTimeMinutes: 0,
        },
        {
          merchantLocationId: "loc-1",
          price: 280000,
          currency: "VND",
          available: false,
          preparationTimeMinutes: 0,
        },
      ],
    });
    expect(body.locationOffers[0].inventoryMode).toBeUndefined();
    expect(body.locationOffers[1].inventoryMode).toBeUndefined();
  });

  it("serializes resale attributes from the authoritative category schema and option ids", async () => {
    const detail: CategoryDetail = {
      id: "cat-resale",
      name: "Đồ gia dụng",
      catalogType: "PRODUCT",
      attributes: [
        {
          id: "attr-condition",
          name: "Tình trạng",
          attributeKey: "condition",
          dataType: "SINGLE_SELECT",
          required: true,
          options: [
            { id: "condition-good", value: "Đã sử dụng · Còn tốt" },
            { id: "condition-fair", value: "Đã sử dụng · Có dấu vết" },
          ],
        },
        {
          id: "attr-months",
          name: "Tháng đã sử dụng",
          attributeKey: "used_months",
          dataType: "NUMBER",
          required: false,
        },
        {
          id: "attr-warranty",
          name: "Còn bảo hành",
          attributeKey: "has_warranty",
          dataType: "BOOLEAN",
          required: false,
        },
        {
          id: "attr-accessories",
          name: "Phụ kiện kèm theo",
          attributeKey: "accessories",
          dataType: "MULTI_SELECT",
          required: false,
          options: [
            { id: "accessory-box", value: "Có hộp" },
            { id: "accessory-manual", value: "Có sách hướng dẫn" },
          ],
        },
      ],
    };
    mocks.getCategoryDetail.mockResolvedValue(detail);

    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} />);

    fireEvent.change(screen.getByLabelText("Kiểu listing"), { target: { value: "RESALE" } });
    fillBaseFields({ categoryId: "cat-resale", name: "Nồi chiên không dầu" });

    await waitFor(() => expect(screen.getByLabelText("Tình trạng")).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText("Tình trạng"), { target: { value: "condition-good" } });
    fireEvent.change(screen.getByLabelText("Tháng đã sử dụng"), { target: { value: "8" } });
    fireEvent.change(screen.getByLabelText("Còn bảo hành"), { target: { value: "true" } });
    fireEvent.click(screen.getByLabelText("Có hộp"));
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "500000" } });

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(1));
    const body = mocks.createCatalogItem.mock.calls[0][2];

    expect(body.listingKind).toBe("RESALE");
    expect(body.attributes).toEqual(expect.arrayContaining([
      { attributeKey: "condition", optionId: "condition-good" },
      { attributeKey: "used_months", valueNumber: 8 },
      { attributeKey: "has_warranty", valueBoolean: true },
      { attributeKey: "accessories", optionId: "accessory-box" },
    ]));
    expect(body.attributes).not.toContainEqual(expect.objectContaining({ optionId: "condition-fair" }));
  });

  it("does not invent a resale condition selector when the category has no attributes", async () => {
    mocks.getCategoryDetail.mockResolvedValue({
      id: "cat-resale",
      name: "Đồ gia dụng",
      catalogType: "PRODUCT",
      attributes: [],
    } satisfies CategoryDetail);

    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} />);

    fireEvent.change(screen.getByLabelText("Kiểu listing"), { target: { value: "RESALE" } });
    fireEvent.change(screen.getByLabelText("Danh mục"), { target: { value: "cat-resale" } });

    expect(await screen.findByText(/không tự tạo danh sách tình trạng thanh lý ở frontend/i)).toBeInTheDocument();
    expect(screen.queryByLabelText("Tình trạng")).not.toBeInTheDocument();
  });
});
