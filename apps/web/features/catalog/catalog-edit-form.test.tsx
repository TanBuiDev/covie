import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { CatalogEditForm } from "./catalog-edit-form";
import type { components } from "@covie/api-client";

const api = vi.hoisted(() => ({
  getCategoryDetail: vi.fn(),
  createCatalogVariant: vi.fn(),
  updateCatalogItem: vi.fn(),
  updateCatalogVariant: vi.fn(),
  deleteCatalogVariant: vi.fn(),
  updateCatalogLocationOffer: vi.fn(),
  runCatalogLifecycleAction: vi.fn(),
}));
const router = vi.hoisted(() => ({ refresh: vi.fn() }));

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("./catalog-api", () => api);

const item: components["schemas"]["CatalogItemManagementDetailResponse"] = {
  id: "item-1",
  type: "PRODUCT" as const,
  listingKind: "STANDARD" as const,
  categoryId: "category-1",
  name: "Cà phê hạt",
  description: "Rang vừa",
  listingStatus: "DRAFT" as const,
  moderationStatus: "NOT_REQUIRED" as const,
  version: 4,
  allowedActions: ["UPDATE", "PUBLISH", "ARCHIVE"],
  attributes: [{ categoryAttributeId: "attribute-1", attributeKey: "origin", valueText: "Đà Lạt" }],
  tags: ["arabica"],
  media: [{ id: "media-1", contentType: "image/png", status: "READY" as const, url: "https://media.test/coffee.png", sortOrder: 0, primary: true }],
  optionGroups: [],
  variants: [{ id: "variant-1", name: "Mặc định", defaultVariant: true, status: "ACTIVE", sortOrder: 0, selectedOptionIds: [], version: 2, allowedActions: ["UPDATE"] }],
  locationOffers: [{ id: "offer-1", merchantLocationId: "location-1", merchantLocationName: "Điểm chính", variantId: "variant-1", price: 45000, currency: "VND", available: true, inventoryMode: "UNTRACKED", preparationTimeMinutes: 10, version: 7, allowedActions: ["UPDATE"] }],
};

beforeEach(() => {
  for (const mock of Object.values(api)) mock.mockReset().mockResolvedValue(undefined);
  api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
    { id: "attribute-1", name: "Xuất xứ", attributeKey: "origin", dataType: "TEXT", required: false, options: [] },
  ] });
  router.refresh.mockReset();
});
afterEach(cleanup);

describe("catalog edit form", () => {
  it("renders authoritative state and sends each resource's own version", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[{ id: "category-1", name: "Đồ uống", catalogType: "PRODUCT" }]} />);
    expect(screen.getByText("Bản nháp")).toBeInTheDocument();
    expect(screen.getByText("Không cần kiểm duyệt")).toBeInTheDocument();
    expect(screen.getByText("origin: Đà Lạt")).toBeInTheDocument();
    expect(screen.getByAltText("Cà phê hạt — ảnh 1")).toBeInTheDocument();

    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Cà phê mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledWith("merchant-1", "item-1", '"v4"', expect.objectContaining({ name: "Cà phê mới" })));

    fireEvent.change(screen.getByLabelText("Giá tại Điểm chính"), { target: { value: "47000" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
    await waitFor(() => expect(api.updateCatalogLocationOffer).toHaveBeenCalledWith("merchant-1", "item-1", "offer-1", 7, expect.objectContaining({ price: 47000 })));

    fireEvent.click(screen.getByRole("button", { name: "Đăng bán" }));
    await waitFor(() => expect(api.runCatalogLifecycleAction).toHaveBeenCalledWith("merchant-1", "item-1", "PUBLISH", '"v4"'));
  });

  it("mounts no edit control when the server returns no scoped action", () => {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, allowedActions: [], variants: [{ ...item.variants![0]!, allowedActions: [] }], locationOffers: [{ ...item.locationOffers![0]!, allowedActions: [] }] }} etag={'"v4"'} categories={[]} />);
    expect(screen.queryByRole("button", { name: "Lưu thông tin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu Điểm chính" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đăng bán" })).not.toBeInTheDocument();
  });

  it("requires archive confirmation and sends the item ETag only after approval", async () => {
    const confirm = vi.spyOn(window, "confirm").mockReturnValueOnce(false).mockReturnValueOnce(true);
    try {
      render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
      fireEvent.click(screen.getByRole("button", { name: "Lưu trữ" }));
      expect(api.runCatalogLifecycleAction).not.toHaveBeenCalled();
      fireEvent.click(screen.getByRole("button", { name: "Lưu trữ" }));
      await waitFor(() => expect(api.runCatalogLifecycleAction).toHaveBeenCalledWith("merchant-1", "item-1", "ARCHIVE", '"v4"'));
      expect(router.refresh).toHaveBeenCalledTimes(1);
    } finally {
      confirm.mockRestore();
    }
  });

  it("disables lifecycle submissions while a command is pending", async () => {
    let resolveCommand!: () => void;
    api.runCatalogLifecycleAction.mockReturnValue(new Promise<void>((resolve) => { resolveCommand = resolve; }));
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Đăng bán" }));
    expect(screen.getByRole("button", { name: "Đăng bán" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Đăng bán" }));
    expect(api.runCatalogLifecycleAction).toHaveBeenCalledTimes(1);
    resolveCommand();
    await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  });

  it("keeps ordinary failures retryable without clearing the seller's input", async () => {
    api.updateCatalogItem.mockRejectedValueOnce(new Error("Kết nối bị gián đoạn"));
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Tên chưa lưu" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await screen.findByText("Kết nối bị gián đoạn");
    expect(screen.getByRole("textbox", { name: "Tên" })).toHaveValue("Tên chưa lưu");
    expect(screen.getByRole("button", { name: "Lưu thông tin" })).toBeEnabled();
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(2));
    await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  });

  it("keeps unsaved input and blocks mutations after a version conflict until reload", async () => {
    api.updateCatalogItem.mockRejectedValueOnce(new CovieApiError(409, "RESOURCE_VERSION_CONFLICT", "stale"));
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Tên chưa lưu" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await screen.findByText(/đã thay đổi ở nơi khác/i);
    expect(screen.getByRole("textbox", { name: "Tên" })).toHaveValue("Tên chưa lưu");
    expect(screen.getByRole("button", { name: "Lưu thông tin" })).toBeDisabled();
    fireEvent.click(screen.getByRole("button", { name: "Tải dữ liệu mới" }));
    expect(router.refresh).toHaveBeenCalledTimes(1);
  });

  it("does not replace unchanged media and attributes when only the name changes", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, media: [{ ...item.media![0]!, status: "PENDING_UPLOAD", url: undefined }] }} etag={'"v4"'} categories={[{ id: "category-1", name: "Đồ uống", catalogType: "PRODUCT" }]} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Cà phê mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    const patch = api.updateCatalogItem.mock.calls[0]?.[3];
    expect(patch).toMatchObject({ name: "Cà phê mới" });
    expect(patch).not.toHaveProperty("mediaIds");
    expect(patch).not.toHaveProperty("attributes");
  });

  it("preserves an unset preparation time when only an offer price changes", async () => {
    const offer = { ...item.locationOffers![0]!, preparationTimeMinutes: undefined };
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, locationOffers: [offer] }} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByLabelText("Giá tại Điểm chính"), { target: { value: "47000" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
    await waitFor(() => expect(api.updateCatalogLocationOffer).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogLocationOffer.mock.calls[0]?.[4]).not.toHaveProperty("preparationTimeMinutes");
  });

  it("sends an explicit null when the seller clears an existing preparation time", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByLabelText("Thời gian chuẩn bị (phút)"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
    await waitFor(() => expect(api.updateCatalogLocationOffer).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogLocationOffer.mock.calls[0]?.[4]).toHaveProperty("preparationTimeMinutes", null);
  });

  it("sends an explicit empty SKU when the seller clears it", async () => {
    const variant = { ...item.variants![0]!, sku: "COFFEE-OLD" };
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, variants: [variant] }} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByLabelText("SKU"), { target: { value: "" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu biến thể Mặc định" }));
    await waitFor(() => expect(api.updateCatalogVariant).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogVariant.mock.calls[0]?.[4]).toHaveProperty("sku", "");
  });

  it("does not rewrite an unchanged boolean attribute during a name edit", async () => {
    const booleanAttribute = { categoryAttributeId: "attribute-bool", attributeKey: "organic", valueBoolean: true };
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-bool", name: "Hữu cơ", attributeKey: "organic", dataType: "BOOLEAN", required: false, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [booleanAttribute] }} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Cà phê mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).not.toHaveProperty("attributes");
  });

  it("edits a single-select attribute through the category's allowed options", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-roast", name: "Mức rang", attributeKey: "roast", dataType: "SINGLE_SELECT", required: true,
        options: [{ id: "roast-light", value: "Rang nhẹ" }, { id: "roast-dark", value: "Rang đậm" }] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [
      { categoryAttributeId: "attribute-roast", attributeKey: "roast", optionId: "roast-light" },
    ] }} etag={'"v4"'} categories={[]} />);
    const select = await screen.findByLabelText("Mức rang");
    expect(select).toHaveValue("roast-light");
    fireEvent.change(select, { target: { value: "roast-dark" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("attributes", [
      { attributeKey: "roast", optionId: "roast-dark" },
    ]);
  });

  it("lets the seller fill a required text attribute missing from the draft", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-brand", name: "Thương hiệu", attributeKey: "brand", dataType: "TEXT", required: true, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [] }} etag={'"v4"'} categories={[]} />);
    const input = await screen.findByLabelText("Thương hiệu");
    fireEvent.change(input, { target: { value: "CoVie" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("attributes", [
      { attributeKey: "brand", valueText: "CoVie" },
    ]);
  });

  it("edits a multi-select attribute without dropping its already selected option", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-color", name: "Màu", attributeKey: "color", dataType: "MULTI_SELECT", required: true,
        options: [{ id: "color-red", value: "Màu đỏ" }, { id: "color-blue", value: "Màu xanh" }] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [
      { categoryAttributeId: "attribute-color", attributeKey: "color", optionId: "color-red" },
    ] }} etag={'"v4"'} categories={[]} />);
    expect(await screen.findByRole("checkbox", { name: "Màu đỏ" })).toBeChecked();
    fireEvent.click(screen.getByRole("checkbox", { name: "Màu xanh" }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("attributes", [
      { attributeKey: "color", optionId: "color-red" },
      { attributeKey: "color", optionId: "color-blue" },
    ]);
  });

  it("updates a numeric attribute using its category-defined number type", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-weight", name: "Khối lượng", attributeKey: "weight", dataType: "NUMBER", required: true, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [
      { categoryAttributeId: "attribute-weight", attributeKey: "weight", valueNumber: 12 },
    ] }} etag={'"v4"'} categories={[]} />);
    const input = await screen.findByLabelText("Khối lượng");
    fireEvent.change(input, { target: { value: "15" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("attributes", [
      { attributeKey: "weight", valueNumber: 15 },
    ]);
  });

  it("fills a missing optional boolean attribute without inventing a default", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-organic", name: "Hữu cơ", attributeKey: "organic", dataType: "BOOLEAN", required: false, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [] }} etag={'"v4"'} categories={[]} />);
    const select = await screen.findByRole("combobox", { name: "Hữu cơ" });
    expect(select).toHaveTextContent("Không chọn");
    fireEvent.click(select);
    fireEvent.click(screen.getByRole("option", { name: /^Có$/ }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("attributes", [
      { attributeKey: "organic", valueBoolean: true },
    ]);
  });

  it("keeps ordinary item edits available when category definitions cannot load", async () => {
    api.getCategoryDetail.mockRejectedValue(new Error("network offline"));
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} />);
    await screen.findByText(/không tải được định nghĩa thuộc tính/i);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Cà phê mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).not.toHaveProperty("attributes");
  });

  it("allows a draft name edit while its required category attribute is still missing", async () => {
    api.getCategoryDetail.mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-brand", name: "Thương hiệu", attributeKey: "brand", dataType: "TEXT", required: true, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [] }} etag={'"v4"'} categories={[]} />);
    await screen.findByLabelText("Thương hiệu");
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Cà phê mới" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toMatchObject({ name: "Cà phê mới" });
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).not.toHaveProperty("attributes");
  });

  it("retries category-definition loading without clearing an unsaved item name", async () => {
    api.getCategoryDetail.mockRejectedValueOnce(new Error("network offline")).mockResolvedValue({ id: "category-1", attributes: [
      { id: "attribute-brand", name: "Thương hiệu", attributeKey: "brand", dataType: "TEXT", required: false, options: [] },
    ] });
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, attributes: [] }} etag={'"v4"'} categories={[]} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Tên chưa lưu" } });
    const retry = await screen.findByRole("button", { name: "Thử tải lại thuộc tính" });
    fireEvent.click(retry);
    expect(await screen.findByLabelText("Thương hiệu")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Tên" })).toHaveValue("Tên chưa lưu");
    expect(api.getCategoryDetail).toHaveBeenCalledTimes(2);
  });

  it("does not submit inventory mode when editing a Service offer", async () => {
    const serviceOffer = { ...item.locationOffers![0]!, inventoryMode: undefined };
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, type: "SERVICE", locationOffers: [serviceOffer] }} etag={'"v4"'} categories={[]} />);
    expect(screen.queryByLabelText("Quản lý tồn kho")).not.toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("Giá tại Điểm chính"), { target: { value: "47000" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
    await waitFor(() => expect(api.updateCatalogLocationOffer).toHaveBeenCalledTimes(1));
    expect(api.updateCatalogLocationOffer.mock.calls[0]?.[4]).not.toHaveProperty("inventoryMode");
  });
});
