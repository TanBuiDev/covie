import type { components } from "@covie/api-client";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { CatalogCreateForm } from "./catalog-create-form";

type Category = components["schemas"]["CategorySummary"];
type CategoryDetail = components["schemas"]["CategoryDetail"];
type Location = components["schemas"]["MerchantLocationResponse"];
type CreateCatalogItemRequest = components["schemas"]["CreateCatalogItemRequest"];

const mocks = vi.hoisted(() => ({
  createCatalogItem: vi.fn<(merchantId: string, idempotencyKey: string, input: CreateCatalogItemRequest) => Promise<unknown>>(),
  getCategoryDetail: vi.fn<(categoryId: string) => Promise<CategoryDetail>>(),
  uploadImage: vi.fn<(file: File, purpose: "CATALOG_IMAGE") => Promise<{ mediaId?: string }>>(),
  push: vi.fn<(href: string) => void>(),
  refresh: vi.fn<() => void>(),
}));

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: mocks.push, refresh: mocks.refresh }),
}));

vi.mock("./catalog-api", () => ({
  createCatalogItem: mocks.createCatalogItem,
  getCategoryDetail: mocks.getCategoryDetail,
}));

vi.mock("@/features/media/direct-image-upload", () => ({
  uploadImage: mocks.uploadImage,
}));

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

function requireElements(elements: HTMLElement[], expected: number) {
  expect(elements).toHaveLength(expected);
  if (elements.length !== expected) throw new Error(`Expected ${expected} elements, received ${elements.length}.`);
  return elements;
}

function firstCreateCall() {
  const call = mocks.createCatalogItem.mock.calls[0];
  expect(call).toBeDefined();
  if (!call) throw new Error("Expected createCatalogItem to be called.");
  return call;
}

beforeEach(() => {
  mocks.createCatalogItem.mockReset();
  mocks.getCategoryDetail.mockReset();
  mocks.uploadImage.mockReset();
  mocks.push.mockReset();
  mocks.refresh.mockReset();
  mocks.createCatalogItem.mockResolvedValue({});
  mocks.uploadImage.mockResolvedValue({ mediaId: "media-1" });
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

    const prices = requireElements(screen.getAllByLabelText(/Giá chính xác \(VND\)/), 2);
    const firstPrice = prices[0];
    const secondPrice = prices[1];
    if (!firstPrice || !secondPrice) throw new Error("Expected two service variant price inputs.");
    fireEvent.change(firstPrice, { target: { value: "150000" } });
    fireEvent.change(secondPrice, { target: { value: "280000" } });

    const availability = requireElements(screen.getAllByLabelText("Đang nhận đơn"), 2);
    const secondAvailability = availability[1];
    if (!secondAvailability) throw new Error("Expected second service variant availability control.");
    fireEvent.click(secondAvailability);

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(1));
    const [merchantId, idempotencyKey, body] = firstCreateCall();

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
    expect(body.locationOffers?.map((offer) => offer.inventoryMode)).toEqual([undefined, undefined]);
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

    fireEvent.change(screen.getByLabelText("Loại sản phẩm"), { target: { value: "RESALE" } });
    fillBaseFields({ categoryId: "cat-resale", name: "Nồi chiên không dầu" });

    await waitFor(() => expect(screen.getByLabelText(/^Tình trạng/)).toBeInTheDocument());

    fireEvent.change(screen.getByLabelText(/^Tình trạng/), { target: { value: "condition-good" } });
    fireEvent.change(screen.getByLabelText("Tháng đã sử dụng"), { target: { value: "8" } });
    fireEvent.change(screen.getByLabelText("Còn bảo hành"), { target: { value: "true" } });
    fireEvent.click(screen.getByLabelText("Có hộp"));
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "500000" } });

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(1));
    const body = firstCreateCall()[2];

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

    fireEvent.change(screen.getByLabelText("Loại sản phẩm"), { target: { value: "RESALE" } });
    fireEvent.change(screen.getByLabelText("Danh mục"), { target: { value: "cat-resale" } });

    expect(await screen.findByText("Danh mục này chưa có tùy chọn tình trạng để chọn.")).toBeInTheDocument();
    expect(screen.queryByLabelText(/^Tình trạng/)).not.toBeInTheDocument();
  });

  it("keeps entered data and shows retryable copy when image upload fails", async () => {
    mocks.getCategoryDetail.mockResolvedValue({
      id: "cat-resale",
      name: "Đồ gia dụng",
      catalogType: "PRODUCT",
      attributes: [],
    } satisfies CategoryDetail);
    mocks.uploadImage.mockRejectedValueOnce(new Error("OBJECT_UPLOAD_FAILED"));

    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} />);
    fillBaseFields({ categoryId: "cat-resale", name: "Nồi chiên giữ nguyên" });
    await waitFor(() => expect(mocks.getCategoryDetail).toHaveBeenCalledWith("cat-resale"));
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "500000" } });
    const file = new File(["image"], "catalog.png", { type: "image/png" });
    fireEvent.change(screen.getByLabelText("Ảnh catalog"), { target: { files: [file] } });

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải ảnh lên. Thông tin đã nhập được giữ lại; hãy thử lại.");
    expect(screen.getByLabelText("Tên")).toHaveValue("Nồi chiên giữ nguyên");
    expect(screen.getByText("Đã chọn 1 ảnh.")).toBeInTheDocument();
    expect(mocks.createCatalogItem).not.toHaveBeenCalled();
  });
});
