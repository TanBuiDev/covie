import type { components } from "@covie/api-client";
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CatalogCreateForm } from "./catalog-create-form";

type Category = components["schemas"]["CategorySummary"];
type CategoryDetail = components["schemas"]["CategoryDetail"];
type Location = components["schemas"]["MerchantLocationResponse"];

it("uses the mixed gallery with video ON while keeping caption authoring OFF", () => {
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
  try {
    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} />);
    expect(screen.getByLabelText("Ảnh và video Social")).toHaveAttribute("accept", expect.stringContaining("video/mp4"));
    expect(screen.queryByRole("textbox", { name: "Caption Social" })).toBeNull();
  } finally { vi.unstubAllEnvs(); }
});
type CreateCatalogItemRequest = components["schemas"]["CreateCatalogItemRequest"];

it("previews the current caption and reordered local images without uploading or creating", () => {
  const urls = vi.spyOn(URL, "createObjectURL").mockImplementation(file => `blob:${(file as File).name}`);
  render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} socialAuthoringEnabled />);
  fireEvent.change(screen.getByLabelText("Tên"), { target: { value: "Bánh mới" } });
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "  Caption nháp\n<script>văn bản</script>  " } });
  fireEvent.change(screen.getByLabelText("Ảnh Social"), { target: { files: [new File(["a"], "a.png", { type: "image/png" }), new File(["b"], "b.png", { type: "image/png" })] } });
  const preview = within(screen.getByRole("region", { name: "Xem trước bài đăng Social" }));
  expect(preview.getByText("Caption nháp <script>văn bản</script>")).toBeInTheDocument();
  expect(preview.getByAltText("Bánh mới — Ảnh 1/2")).toHaveAttribute("src", "blob:a.png");
  fireEvent.click(screen.getByRole("button", { name: "Làm bìa b.png" }));
  expect(preview.getByAltText("Bánh mới — Ảnh 1/2")).toHaveAttribute("src", "blob:b.png");
  expect(preview.queryByRole("link")).toBeNull();
  expect(mocks.uploadImage).not.toHaveBeenCalled();
  expect(mocks.createCatalogItem).not.toHaveBeenCalled();
  urls.mockRestore();
});

const mocks = vi.hoisted(() => ({
  createCatalogItem: vi.fn<(merchantId: string, idempotencyKey: string, input: CreateCatalogItemRequest) => Promise<unknown>>(),
  getCategoryDetail: vi.fn<(categoryId: string) => Promise<CategoryDetail>>(),
  lookupBarcode: vi.fn<(gtin: string) => Promise<unknown>>(),
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
  lookupBarcode: mocks.lookupBarcode,
}));

vi.mock("@/features/media/direct-image-upload", () => ({
  uploadImage: mocks.uploadImage,
  validateImageFile: vi.fn(),
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
  mocks.lookupBarcode.mockReset();
  mocks.uploadImage.mockReset();
  mocks.push.mockReset();
  mocks.refresh.mockReset();
  mocks.createCatalogItem.mockResolvedValue({});
  mocks.uploadImage.mockResolvedValue({ mediaId: "media-1" });
});

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

describe("CatalogCreateForm Seller Mobile contracts", () => {
  it("orders the cover and retries only the failed Social upload while retaining the draft", async () => {
    vi.stubGlobal("URL", class extends URL { static override createObjectURL = vi.fn(() => "blob:social-" + crypto.randomUUID()); static override revokeObjectURL = vi.fn(); });
    mocks.getCategoryDetail.mockResolvedValue({ id: "cat-resale", attributes: [] });
    const first = new File(["a"], "a.png", { type: "image/png" });
    const cover = new File(["b"], "b.png", { type: "image/png" });
    mocks.uploadImage.mockResolvedValueOnce({ mediaId: "cover-id" }).mockRejectedValueOnce(new Error("network"))
      .mockResolvedValueOnce({ mediaId: "first-id" });
    mocks.createCatalogItem.mockRejectedValueOnce(new Error("Create response lost")).mockResolvedValueOnce({ id: "created" });
    const view = render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} socialAuthoringEnabled />);
    fillBaseFields({ categoryId: "cat-resale", name: "Giữ bản nháp" });
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "25000" } });
    fireEvent.change(screen.getByLabelText("Ảnh Social"), { target: { files: [first, cover] } });
    fireEvent.click(screen.getByRole("button", { name: "Làm bìa b.png" }));
    await waitFor(() => expect(screen.getByRole("button", { name: "Tạo nội dung" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));
    await screen.findByRole("alert");
    expect(mocks.createCatalogItem).not.toHaveBeenCalled();
    expect(screen.getByLabelText("Tên")).toHaveValue("Giữ bản nháp");
    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));
    await screen.findByText("Create response lost");
    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));
    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(2));
    expect(mocks.uploadImage.mock.calls.map(([file]) => file.name)).toEqual(["b.png", "a.png", "a.png"]);
    expect(firstCreateCall()[2].mediaIds).toEqual(["cover-id", "first-id"]);
    expect(mocks.createCatalogItem.mock.calls[1]).toEqual(mocks.createCatalogItem.mock.calls[0]);
    view.unmount();
    expect(URL.revokeObjectURL).toHaveBeenCalledTimes(2);
  });

  it("sends a separately authored social caption only when its feature is enabled", async () => {
    mocks.getCategoryDetail.mockResolvedValue({ id: "cat-resale", attributes: [] });
    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} socialAuthoringEnabled />);
    fillBaseFields({ categoryId: "cat-resale" });
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "25000" } });
    const caption = screen.getByRole("textbox", { name: "Caption" });
    fireEvent.change(caption, { target: { value: "  Caption riêng\nDòng tiếp theo  " } });
    fireEvent.change(screen.getByRole("textbox", { name: "Mô tả" }), { target: { value: "Mô tả thương mại" } });
    await waitFor(() => expect(mocks.getCategoryDetail).toHaveBeenCalledWith("cat-resale"));
    await waitFor(() => expect(screen.getByRole("button", { name: "Tạo nội dung" })).toBeEnabled());
    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));
    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalled());
    expect(firstCreateCall()[2]).toEqual(expect.objectContaining({ socialCaption: "Caption riêng\nDòng tiếp theo", description: "Mô tả thương mại" }));
  });

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
    await waitFor(() => expect(screen.getByRole("button", { name: "Tạo nội dung" })).toBeEnabled());

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
    // Category detail is set before its asynchronous loading flag is cleared.
    // A visible selector alone does not mean the submit button is enabled.
    await waitFor(() => expect(screen.getByRole("button", { name: "Tạo nội dung" })).toBeEnabled());

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
    vi.stubGlobal("URL", class extends URL { static override createObjectURL = vi.fn(() => "blob:test-preview"); static override revokeObjectURL = vi.fn(); });
    const file = new File(["image"], "catalog.png", { type: "image/png" });
    fireEvent.change(screen.getByLabelText("Ảnh catalog"), { target: { files: [file] } });

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Không thể tải ảnh lên. Thông tin đã nhập được giữ lại; hãy thử lại.");
    expect(screen.getByLabelText("Tên")).toHaveValue("Nồi chiên giữ nguyên");
    expect((screen.getByLabelText("Ảnh catalog") as HTMLInputElement).files).toHaveLength(1);
    expect(mocks.createCatalogItem).not.toHaveBeenCalled();
  });

  it("does not render barcode lookup when type is SERVICE", () => {
    render(<CatalogCreateForm merchantId="merchant-1" categories={serviceCategories} locations={locations} />);
    fireEvent.change(screen.getByLabelText("Loại"), { target: { value: "SERVICE" } });
    expect(screen.queryByLabelText("Mã vạch GTIN/EAN")).not.toBeInTheDocument();
  });

  it("renders barcode lookup for PRODUCT and applies suggested name into form submission and preview", async () => {
    mocks.getCategoryDetail.mockResolvedValue({
      id: "cat-resale",
      name: "Đồ gia dụng",
      catalogType: "PRODUCT",
      attributes: [],
    } satisfies CategoryDetail);

    mocks.lookupBarcode.mockResolvedValue({
      gtin: "8934673501354",
      gtin14: "08934673501354",
      productName: "Sữa TH true milk ít đường bịch 220ml",
      needsReview: false,
    });

    render(<CatalogCreateForm merchantId="merchant-1" categories={productCategories} locations={locations} />);

    // Barcode input is visible for PRODUCT
    expect(screen.getByLabelText("Mã vạch GTIN/EAN")).toBeInTheDocument();

    // Perform barcode lookup
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(mocks.lookupBarcode).toHaveBeenCalledWith("8934673501354");
    });

    // Apply suggested name
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));

    // Name input is updated
    expect(screen.getByLabelText("Tên")).toHaveValue("Sữa TH true milk ít đường bịch 220ml");

    // Preview title is updated
    expect(screen.getByText("Sữa TH true milk ít đường bịch 220ml")).toBeInTheDocument();

    // Complete remaining fields and submit
    fireEvent.change(screen.getByLabelText("Danh mục"), { target: { value: "cat-resale" } });
    fireEvent.change(screen.getByLabelText("Điểm bán"), { target: { value: "loc-1" } });
    await waitFor(() => expect(mocks.getCategoryDetail).toHaveBeenCalledWith("cat-resale"));
    fireEvent.change(screen.getByLabelText("Giá chính xác (VND)"), { target: { value: "15000" } });

    fireEvent.click(screen.getByRole("button", { name: "Tạo nội dung" }));

    await waitFor(() => expect(mocks.createCatalogItem).toHaveBeenCalledTimes(1));
    const body = firstCreateCall()[2];
    expect(body.name).toBe("Sữa TH true milk ít đường bịch 220ml");
  });
});
