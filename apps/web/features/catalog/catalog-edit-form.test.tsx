import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
  lookupBarcode: vi.fn(),
  fetchSocialComments: vi.fn(),
}));
const router = vi.hoisted(() => ({ refresh: vi.fn() }));

it("renders a READY video as a manual player in the non-editable Seller view", () => {
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
  try {
    const view = render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, allowedActions: [], media: [
      { id: "video", contentType: "video/mp4", status: "READY", url: "/api/v1/media/video/content" },
    ] }} etag={'"v4"'} categories={[]} />);
    expect(within(screen.getByRole("figure")).getByRole("button", { name: `Phát video ${item.name}` })).toBeInTheDocument();
    expect(view.container.querySelector('img[src*="/content"]')).toBeNull();
    expect(view.container.querySelector("video")).toBeNull();
  } finally { vi.unstubAllEnvs(); }
});

it("saves mixed ordering with video ON and caption OFF without sending a caption", async () => {
  vi.stubEnv("NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED", "true");
  try {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, media: [...item.media!,
      { id: "video", contentType: "video/mp4", status: "READY", url: "/api/v1/media/video/content", sortOrder: 1, primary: false },
    ] }} etag={'"v4"'} categories={[]} />);
    fireEvent.click(screen.getByRole("button", { name: "Đưa lên Video 2" }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledOnce());
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).toHaveProperty("mediaIds", ["video", "media-1"]);
    expect(api.updateCatalogItem.mock.calls[0]?.[3]).not.toHaveProperty("socialCaption");
  } finally { vi.unstubAllEnvs(); }
});

it.each([true, false])("renders only the supplied literal moderation reason with Social enabled=$0", socialAuthoringEnabled => {
  const reasonItem = { ...item, moderationStatus: "HIDDEN" as const, moderationReasonCode: "<script>Literal decision reason</script>" };
  const view = render(<CatalogEditForm merchantId="merchant-1" item={reasonItem} etag={'"v4"'} categories={[]} socialAuthoringEnabled={socialAuthoringEnabled} />);
  const reason = screen.queryByRole("note", { name: "Lý do kiểm duyệt" });
  if (socialAuthoringEnabled) {
    expect(reason).toHaveTextContent("<script>Literal decision reason</script>");
    expect(view.container.querySelector("script")).toBeNull();
  } else expect(reason).toBeNull();
});

it("keeps the actual caption draft when the existing comment API denies an unpublished item", async () => {
  api.fetchSocialComments.mockRejectedValue(new CovieApiError(404, "RESOURCE_NOT_FOUND", "Resource not found."));
  render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Unsaved Seller caption" } });
  const trigger = screen.getByRole("button", { name: "Xem bình luận" });
  fireEvent.click(trigger);
  expect(await screen.findByRole("alert")).toHaveTextContent("Không tải được bình luận.");
  fireEvent.click(screen.getByRole("button", { name: "Đóng" }));
  expect(trigger).toHaveFocus();
  expect(screen.getByRole("textbox", { name: "Caption" })).toHaveValue("Unsaved Seller caption");
  expect(api.fetchSocialComments).toHaveBeenCalledExactlyOnceWith("item-1");
  expect(api.updateCatalogItem).not.toHaveBeenCalled();
  expect(router.refresh).not.toHaveBeenCalled();
});

it.each([true, false])("shows the Seller comment entry only when Social authoring is enabled=$0", socialAuthoringEnabled => {
  render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled={socialAuthoringEnabled} />);
  const entry = screen.queryByRole("button", { name: "Xem bình luận" });
  if (socialAuthoringEnabled) expect(entry).toBeInTheDocument();
  else expect(entry).toBeNull();
  expect(screen.queryByRole("region", { name: "Bình luận sản phẩm" })).toBeNull();
});

it("retains a conflicting caption and guards the explicit stale-data reload", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  api.updateCatalogItem.mockRejectedValueOnce(new CovieApiError(409, "RESOURCE_VERSION_CONFLICT", "Conflict"));
  render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Nháp khi xung đột" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
  const reload = await screen.findByRole("button", { name: "Tải dữ liệu mới" });
  expect(confirm).not.toHaveBeenCalled();
  fireEvent.click(reload);
  expect(router.refresh).not.toHaveBeenCalled();
  expect(screen.getByRole("textbox", { name: "Caption" })).toHaveValue("Nháp khi xung đột");
  confirm.mockReturnValue(true);
  fireEvent.click(reload);
  expect(router.refresh).toHaveBeenCalledTimes(1);
  expect(confirm).toHaveBeenCalledTimes(2);
});

it("guards independent offer refresh but never blocks saving the current caption", async () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Caption nháp" } });
  fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
  expect(confirm).toHaveBeenCalledTimes(1);
  expect(api.updateCatalogLocationOffer).not.toHaveBeenCalled();
  expect(router.refresh).not.toHaveBeenCalled();
  expect(screen.getByRole("textbox", { name: "Caption" })).toHaveValue("Caption nháp");
  confirm.mockReturnValue(true);
  fireEvent.click(screen.getByRole("button", { name: "Lưu Điểm chính" }));
  await waitFor(() => expect(router.refresh).toHaveBeenCalledTimes(1));
  expect(api.updateCatalogLocationOffer).toHaveBeenCalledTimes(1);
  confirm.mockReturnValue(false);
  fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
  await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledWith("merchant-1", "item-1", '"v4"', { socialCaption: "Caption nháp" }));
  expect(confirm).toHaveBeenCalledTimes(2);
});

it("guards variant and lifecycle commands when only gallery changes are unsaved", () => {
  const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
  render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  fireEvent.click(screen.getByRole("button", { name: "Xóa Ảnh 1" }));
  fireEvent.click(screen.getByRole("button", { name: "Lưu biến thể Mặc định" }));
  fireEvent.click(screen.getByRole("button", { name: "Đăng bán" }));
  expect(confirm).toHaveBeenCalledTimes(2);
  expect(api.updateCatalogVariant).not.toHaveBeenCalled();
  expect(api.runCatalogLifecycleAction).not.toHaveBeenCalled();
  expect(router.refresh).not.toHaveBeenCalled();
});

it("previews only six READY images without truncating the editable legacy gallery", () => {
  const media = Array.from({ length: 8 }, (_, index) => ({ id: `image-${index}`, contentType: "image/png",
    status: index === 0 ? "PENDING_UPLOAD" as const : "READY" as const,
    url: index === 0 ? undefined : `https://media.test/${index}.png`, sortOrder: index, primary: index === 0 }));
  render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, media }} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  const preview = within(screen.getByRole("region", { name: "Xem trước bài đăng Social" }));
  expect(preview.getByText("Ảnh 1/6")).toBeInTheDocument();
  expect(preview.getByAltText("Cà phê hạt — Ảnh 1/6")).toHaveAttribute("src", "https://media.test/1.png");
  for (let index = 0; index < 5; index++) fireEvent.click(preview.getByRole("button", { name: "Ảnh tiếp theo" }));
  expect(preview.getByAltText("Cà phê hạt — Ảnh 6/6")).toHaveAttribute("src", "https://media.test/6.png");
  expect(screen.getByRole("button", { name: "Xóa Ảnh 8" })).toBeInTheDocument();
  expect(api.updateCatalogItem).not.toHaveBeenCalled();
});

it("previews confirmed caption then draft changes and undo without writing", () => {
  render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, socialCaption: "Caption đã lưu" }} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
  const preview = within(screen.getByRole("region", { name: "Xem trước bài đăng Social" }));
  expect(preview.getByText("Caption đã lưu")).toBeInTheDocument();
  expect(preview.getByAltText("Cà phê hạt — Ảnh 1/1")).toHaveAttribute("src", "https://media.test/coffee.png");
  fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Caption đang sửa" } });
  expect(preview.getByText("Caption đang sửa")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Hoàn nguyên caption" }));
  expect(preview.getByText("Caption đã lưu")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Xóa Ảnh 1" }));
  expect(preview.getByText("Chưa có ảnh")).toBeInTheDocument();
  expect(api.updateCatalogItem).not.toHaveBeenCalled();
  expect(preview.queryByRole("link")).toBeNull();
});

vi.mock("next/navigation", () => ({ useRouter: () => router }));
vi.mock("./catalog-api", () => api);
vi.mock("@/features/marketplace-view/social-api", async original => ({
  ...await original<typeof import("@/features/marketplace-view/social-api")>(), fetchSocialComments: api.fetchSocialComments,
}));

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
afterEach(() => { cleanup(); vi.restoreAllMocks(); });

describe("catalog edit form", () => {
  it("keeps a legacy Social gallery unchanged when adding over the limit is rejected", async () => {
    const media = Array.from({ length: 12 }, (_, index) => ({ id: "legacy-" + index, status: "READY" as const, sortOrder: index, primary: index === 0 }));
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, media }} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
    fireEvent.change(screen.getByLabelText("Ảnh Social"), { target: { files: [new File(["a"], "too-many.png", { type: "image/png" })] } });
    expect(screen.getByRole("alert")).toHaveTextContent("Gallery hiện có được giữ nguyên");
    expect(screen.getByText("12 / 10 ảnh")).toBeInTheDocument();
    fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Caption only" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledWith("merchant-1", "item-1", '"v4"', { socialCaption: "Caption only" }));
  });

  it("uses the selected existing Social cover order without resending commerce fields", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, media: [
      ...item.media!, { id: "media-2", status: "READY", sortOrder: 1, primary: false, url: "https://example.test/second.png" },
    ] }} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
    fireEvent.click(screen.getByRole("button", { name: "Làm bìa Ảnh 2" }));
    expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(false);
    fireEvent.click(screen.getByRole("button", { name: "Đưa xuống Ảnh 2" }));
    expect(window.dispatchEvent(new Event("beforeunload", { cancelable: true }))).toBe(true);
    const transfer = { setData: vi.fn() };
    fireEvent.dragStart(screen.getByRole("img", { name: "Ảnh 2" }).closest("li")!, { dataTransfer: transfer });
    fireEvent.drop(screen.getByRole("img", { name: "Ảnh 1" }).closest("li")!, { dataTransfer: transfer });
    expect(transfer.setData).toHaveBeenCalledWith("text/plain", "media-2");
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledWith("merchant-1", "item-1", '"v4"',
      { mediaIds: ["media-2", item.media![0]!.id] }));
  });

  it("edits and clears caption independently while preserving unsent commercial fields", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, socialCaption: "Caption đã lưu" }} etag={'"v4"'}
      categories={[]} socialAuthoringEnabled />);
    const caption = screen.getByRole("textbox", { name: "Caption" });
    expect(caption).toHaveValue("Caption đã lưu");
    fireEvent.click(screen.getByRole("button", { name: "Xóa caption" }));
    expect(caption).toHaveValue("");
    fireEvent.click(screen.getByRole("button", { name: "Hoàn nguyên caption" }));
    expect(caption).toHaveValue("Caption đã lưu");
    fireEvent.change(caption, { target: { value: "  Caption mới\nDòng hai  " } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenCalledWith("merchant-1", "item-1", '"v4"', { socialCaption: "Caption mới\nDòng hai" }));
    fireEvent.click(screen.getByRole("button", { name: "Xóa caption" }));
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await waitFor(() => expect(api.updateCatalogItem).toHaveBeenLastCalledWith("merchant-1", "item-1", '"v4"', { socialCaption: null }));
  });

  it("renders authoritative state and sends each resource's own version", async () => {
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[{ id: "category-1", name: "Đồ uống", catalogType: "PRODUCT" }]} />);
    expect(screen.getByText("Bản nháp")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Caption" })).not.toBeInTheDocument();
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
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, allowedActions: [], variants: [{ ...item.variants![0]!, allowedActions: [] }], locationOffers: [{ ...item.locationOffers![0]!, allowedActions: [] }] }} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
    expect(screen.queryByRole("button", { name: "Lưu thông tin" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Lưu Điểm chính" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Đăng bán" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Caption" })).not.toBeInTheDocument();
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
    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[]} socialAuthoringEnabled />);
    fireEvent.change(screen.getByRole("textbox", { name: "Caption" }), { target: { value: "Caption chưa lưu" } });
    fireEvent.change(screen.getByRole("textbox", { name: "Tên" }), { target: { value: "Tên chưa lưu" } });
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));
    await screen.findByText(/đã thay đổi ở nơi khác/i);
    expect(screen.getByRole("textbox", { name: "Caption" })).toHaveValue("Caption chưa lưu");
    expect(screen.getByRole("textbox", { name: "Caption" })).toBeDisabled();
    expect(screen.getByRole("textbox", { name: "Tên" })).toHaveValue("Tên chưa lưu");
    expect(screen.getByRole("button", { name: "Lưu thông tin" })).toBeDisabled();
    const confirmReload = vi.spyOn(window, "confirm").mockReturnValue(true);
    fireEvent.click(screen.getByRole("button", { name: "Tải dữ liệu mới" }));
    expect(confirmReload).toHaveBeenCalledTimes(1);
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
    await waitFor(() => expect(select).toHaveValue("roast-dark"));
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

  it("renders barcode lookup for PRODUCT and applies suggested name which sends in PATCH only upon saving", async () => {
    api.lookupBarcode.mockResolvedValue({
      gtin: "8934673501354",
      gtin14: "08934673501354",
      productName: "Cà phê Robusta Đắk Lắk 500g",
      needsReview: false,
    });

    render(<CatalogEditForm merchantId="merchant-1" item={item} etag={'"v4"'} categories={[{ id: "category-1", name: "Đồ uống", catalogType: "PRODUCT" }]} />);

    // Barcode input is visible
    expect(screen.getByLabelText("Mã vạch GTIN/EAN")).toBeInTheDocument();

    // Lookup barcode
    fireEvent.change(screen.getByLabelText("Mã vạch GTIN/EAN"), { target: { value: "8934673501354" } });
    fireEvent.click(screen.getByRole("button", { name: "Tra cứu" }));

    await waitFor(() => {
      expect(api.lookupBarcode).toHaveBeenCalledWith("8934673501354");
    });

    // Existing name is "Cà phê hạt", so applying triggers confirmation dialog
    fireEvent.click(screen.getByRole("button", { name: "Áp dụng tên gợi ý" }));
    expect(screen.getByText("Xác nhận thay thế tên sản phẩm")).toBeInTheDocument();

    // Confirm replacement
    fireEvent.click(screen.getByRole("button", { name: "Thay thế" }));

    // Input is updated to new name
    expect(screen.getByRole("textbox", { name: "Tên" })).toHaveValue("Cà phê Robusta Đắk Lắk 500g");

    // PATCH has NOT been called yet! (lookup & apply must not create mutations directly)
    expect(api.updateCatalogItem).not.toHaveBeenCalled();

    // Now click "Lưu thông tin"
    fireEvent.click(screen.getByRole("button", { name: "Lưu thông tin" }));

    await waitFor(() => {
      expect(api.updateCatalogItem).toHaveBeenCalledWith(
        "merchant-1",
        "item-1",
        '"v4"',
        expect.objectContaining({ name: "Cà phê Robusta Đắk Lắk 500g" }),
      );
    });
  });

  it("does not render barcode lookup for SERVICE edit form", () => {
    render(<CatalogEditForm merchantId="merchant-1" item={{ ...item, type: "SERVICE" }} etag={'"v4"'} categories={[]} />);
    expect(screen.queryByLabelText("Mã vạch GTIN/EAN")).not.toBeInTheDocument();
  });
});
