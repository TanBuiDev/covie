"use client";

import type { components } from "@covie/api-client";
import Image from "next/image";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  Archive,
  ArrowsClockwise,
  CheckCircle,
  CloudArrowUp,
  FloppyDisk,
  Images,
  Package,
  Pause,
  PlusCircle,
  Storefront,
  Trash,
  Wrench,
} from "@phosphor-icons/react";
import { CovieApiError } from "@/lib/api/api-error";
import { uploadImage } from "@/features/media/direct-image-upload";
import {
  createCatalogVariant,
  deleteCatalogVariant,
  getCategoryDetail,
  runCatalogLifecycleAction,
  updateCatalogItem,
  updateCatalogLocationOffer,
  updateCatalogVariant,
  type CatalogLifecycleAction,
} from "./catalog-api";

type Detail = components["schemas"]["CatalogItemManagementDetailResponse"];
type Category = components["schemas"]["CategorySummary"];
type CategoryDetail = components["schemas"]["CategoryDetail"];
type AttributeDefinition = components["schemas"]["AttributeDefinition"];
type AttributeRequest = components["schemas"]["AttributeValueRequest"];
type ItemAction = NonNullable<Detail["allowedActions"]>[number];

const lifecycleLabels: Record<CatalogLifecycleAction, string> = {
  PUBLISH: "Đăng bán",
  PAUSE: "Tạm dừng",
  ACTIVATE: "Hiển thị lại",
  ARCHIVE: "Lưu trữ",
};

function listingLabel(value?: Detail["listingStatus"]) {
  switch (value) {
    case "DRAFT": return "Bản nháp";
    case "ACTIVE": return "Đang hiển thị";
    case "PAUSED": return "Tạm dừng";
    case "DELETED": return "Đã lưu trữ";
    default: return "Chưa rõ";
  }
}

function moderationLabel(value?: Detail["moderationStatus"]) {
  switch (value) {
    case "NOT_REQUIRED": return "Không cần kiểm duyệt";
    case "PENDING": return "Đang chờ kiểm duyệt";
    case "APPROVED": return "Đã duyệt";
    case "REJECTED": return "Bị từ chối";
    case "HIDDEN": return "Đã ẩn";
    default: return "Chưa rõ";
  }
}

function attributeValue(attribute: NonNullable<Detail["attributes"]>[number]) {
  if (attribute.valueText != null) return attribute.valueText;
  if (attribute.valueNumber != null) return String(attribute.valueNumber);
  if (attribute.valueBoolean != null) return attribute.valueBoolean ? "Có" : "Không";
  return attribute.optionId ?? "Chưa có giá trị";
}

function attributeFieldName(attributeKey: string) {
  return `attribute:${attributeKey}`;
}

function currentAttributeRequests(attributes: Detail["attributes"]): AttributeRequest[] {
  const values: AttributeRequest[] = [];
  for (const attribute of attributes ?? []) {
    if (!attribute.attributeKey) continue;
    if (attribute.valueText != null) values.push({ attributeKey: attribute.attributeKey, valueText: attribute.valueText });
    else if (attribute.valueNumber != null) values.push({ attributeKey: attribute.attributeKey, valueNumber: attribute.valueNumber });
    else if (attribute.valueBoolean != null) values.push({ attributeKey: attribute.attributeKey, valueBoolean: attribute.valueBoolean });
    else if (attribute.optionId != null) values.push({ attributeKey: attribute.attributeKey, optionId: attribute.optionId });
  }
  return values;
}

function submittedAttributeRequests(form: FormData, definitions: AttributeDefinition[]): AttributeRequest[] {
  const values: AttributeRequest[] = [];
  for (const definition of definitions) {
    const attributeKey = definition.attributeKey?.trim();
    if (!attributeKey || !definition.dataType) continue;
    const name = attributeFieldName(attributeKey);
    if (definition.dataType === "MULTI_SELECT") {
      const selected = form.getAll(name).map(String).filter(Boolean);
      for (const optionId of selected) values.push({ attributeKey, optionId });
      continue;
    }
    const raw = String(form.get(name) ?? "").trim();
    if (!raw) continue;
    switch (definition.dataType) {
      case "TEXT": values.push({ attributeKey, valueText: raw }); break;
      case "NUMBER": {
        const number = Number(raw);
        if (!Number.isFinite(number)) throw new Error(`${definition.name ?? attributeKey} phải là số hợp lệ.`);
        values.push({ attributeKey, valueNumber: number });
        break;
      }
      case "BOOLEAN": values.push({ attributeKey, valueBoolean: raw === "true" }); break;
      case "SINGLE_SELECT": values.push({ attributeKey, optionId: raw }); break;
    }
  }
  return values;
}

function requireCompleteChangedAttributes(definitions: AttributeDefinition[], values: AttributeRequest[]) {
  for (const definition of definitions) {
    if (!definition.required || !definition.attributeKey) continue;
    if (!values.some((value) => value.attributeKey === definition.attributeKey)) {
      throw new Error(`Vui lòng hoàn thiện ${definition.name ?? definition.attributeKey} trước khi lưu thuộc tính.`);
    }
  }
}

function sameAttributeRequests(left: AttributeRequest[], right: AttributeRequest[]) {
  const signature = (value: AttributeRequest) => JSON.stringify([
    value.attributeKey, value.valueText ?? null, value.valueNumber ?? null,
    value.valueBoolean ?? null, value.optionId ?? null,
  ]);
  return left.map(signature).sort().join("\u0000") === right.map(signature).sort().join("\u0000");
}

export function CatalogEditForm({ merchantId, item, etag, categories }: {
  merchantId: string;
  item: Detail;
  etag: string | null;
  categories: Category[];
}) {
  const router = useRouter();
  const [pending, setPending] = useState(false);
  const [stale, setStale] = useState(false);
  const [message, setMessage] = useState<string>();
  const [files, setFiles] = useState<File[]>([]);
  const [categoryDetail, setCategoryDetail] = useState<CategoryDetail>();
  const [categoryStatus, setCategoryStatus] = useState<"loading" | "ready" | "error">("loading");
  const [categoryAttempt, setCategoryAttempt] = useState(0);
  const itemId = item.id ?? "";
  const itemActions = new Set(item.allowedActions ?? []);
  const canEditItem = itemActions.has("UPDATE");

  useEffect(() => {
    if (!canEditItem || !item.categoryId) return;
    let active = true;
    void getCategoryDetail(item.categoryId).then((detail) => {
      if (active) { setCategoryDetail(detail); setCategoryStatus("ready"); }
    }).catch(() => {
      if (active) { setCategoryDetail(undefined); setCategoryStatus("error"); }
    });
    return () => { active = false; };
  }, [canEditItem, item.categoryId, categoryAttempt]);

  async function mutate(action: () => Promise<unknown>) {
    if (pending || stale) return;
    setPending(true);
    setMessage(undefined);
    try {
      await action();
      router.refresh();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setStale(true);
        setMessage("Dữ liệu đã thay đổi ở nơi khác. Tải dữ liệu mới trước khi tiếp tục.");
      } else {
        setMessage(error instanceof Error ? error.message : "Không thể lưu thay đổi. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  function submitItem(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!etag) return setMessage("Thiếu phiên bản dữ liệu. Hãy tải dữ liệu mới.");
    const form = new FormData(event.currentTarget);
    void mutate(async () => {
      const patch: components["schemas"]["UpdateCatalogItemRequest"] = {};
      const name = String(form.get("name") ?? "").trim();
      if (name !== item.name) patch.name = name;
      const description = String(form.get("description") ?? "").trim();
      if (description !== (item.description ?? "")) patch.description = description;
      const tags = String(form.get("tags") ?? "").split(",").map((value) => value.trim()).filter(Boolean);
      if (tags.join("\u0000") !== (item.tags ?? []).join("\u0000")) patch.tags = tags;
      if (categoryDetail && categoryDetail.id === item.categoryId) {
        const submitted = submittedAttributeRequests(form, categoryDetail.attributes ?? []);
        if (!sameAttributeRequests(submitted, currentAttributeRequests(item.attributes))) {
          requireCompleteChangedAttributes(categoryDetail.attributes ?? [], submitted);
          patch.attributes = submitted;
        }
      }
      if (files.length) {
        const mediaIds = (item.media ?? []).map((media) => media.id).filter((id): id is string => Boolean(id));
        for (const file of files) {
          const uploaded = await uploadImage(file, "CATALOG_IMAGE");
          if (!uploaded.mediaId) throw new Error("Ảnh tải lên không có mã hợp lệ.");
          mediaIds.push(uploaded.mediaId);
        }
        patch.mediaIds = mediaIds;
      }
      if (!Object.keys(patch).length) return;
      await updateCatalogItem(merchantId, itemId, etag, patch);
    });
  }

  function lifecycle(action: CatalogLifecycleAction) {
    if (!etag) return setMessage("Thiếu phiên bản dữ liệu. Hãy tải dữ liệu mới.");
    if (action === "ARCHIVE" && !window.confirm("Lưu trữ nội dung này?")) return;
    void mutate(() => runCatalogLifecycleAction(merchantId, itemId, action, etag));
  }

  const categoryName = categories.find((c) => c.id === item.categoryId)?.name ?? item.categoryId ?? "Chưa rõ";

  return (
    <section className="seller-catalog-editor" aria-busy={pending}>
      {/* HEADER SUMMARY */}
      <header className="seller-catalog-editor__summary">
        <div className="seller-catalog-editor__header-top">
          <div>
            <p className="eyebrow">Catalog / Chỉnh sửa</p>
            <h1>{item.name ?? "Nội dung catalog"}</h1>
          </div>
          <div className="seller-catalog-editor__states">
            <span className={`status-chip ${item.listingStatus === "ACTIVE" ? "status-chip--success" : item.listingStatus === "PAUSED" ? "status-chip--warning" : "status-chip--neutral"}`}>
              {listingLabel(item.listingStatus)}
            </span>
            <span className={`status-chip ${item.moderationStatus === "APPROVED" ? "status-chip--success" : item.moderationStatus === "REJECTED" ? "status-chip--danger" : "status-chip--neutral"}`}>
              {moderationLabel(item.moderationStatus)}
            </span>
          </div>
        </div>
      </header>

      {message ? (
        <p className="warning-banner" role="alert">
          {message}
        </p>
      ) : null}

      {stale ? (
        <div className="seller-catalog-editor__stale-bar">
          <button type="button" className="seller-button seller-button--secondary" onClick={() => router.refresh()}>
            <ArrowsClockwise size={16} weight="bold" aria-hidden="true" />
            <span>Tải dữ liệu mới</span>
          </button>
        </div>
      ) : null}

      {/* PANEL 1: THÔNG TIN CHUNG */}
      <section className="seller-catalog-editor__panel">
        <div className="seller-catalog-editor__panel-title">
          <Package size={20} weight="duotone" aria-hidden="true" />
          <h2>Thông tin chung</h2>
        </div>

        {itemActions.has("UPDATE") ? (
          <form className="seller-catalog-form" onSubmit={submitItem}>
            <label>
              Tên
              <input name="name" defaultValue={item.name} maxLength={240} required disabled={pending || stale} />
            </label>

            <label>
              Danh mục
              <input readOnly value={categoryName} />
            </label>
            <p className="seller-contract-note">Đổi danh mục cần xác nhận lại thuộc tính; phiên bản này giữ danh mục hiện tại khi chỉnh sửa.</p>

            <label>
              Mô tả
              <textarea name="description" defaultValue={item.description} maxLength={4000} disabled={pending || stale} />
            </label>

            <label>
              Thẻ
              <input name="tags" defaultValue={(item.tags ?? []).join(", ")} disabled={pending || stale} />
            </label>

            {categoryDetail && categoryDetail.id === item.categoryId ? (
              <fieldset className="seller-attribute-fields" disabled={pending || stale}>
                <legend>Thuộc tính danh mục</legend>
                <p className="seller-contract-note">Thuộc tính bắt buộc cần hoàn thiện trước khi đăng bán; bản nháp vẫn có thể sửa thông tin chung.</p>
                {(categoryDetail.attributes ?? []).map((definition) => {
                  const attributeKey = definition.attributeKey?.trim();
                  if (!attributeKey || !definition.dataType) return null;
                  const values = (item.attributes ?? []).filter((attribute) => attribute.categoryAttributeId === definition.id);
                  const current = values[0];
                  const name = attributeFieldName(attributeKey);
                  const label = definition.name?.trim() || attributeKey;

                  if (definition.dataType === "MULTI_SELECT") {
                    const selected = new Set(values.map((value) => value.optionId));
                    return (
                      <fieldset className="seller-attribute-options" key={definition.id ?? attributeKey}>
                        <legend>{label}</legend>
                        {(definition.options ?? []).map((option) => option.id ? (
                          <label className="seller-check-row" key={option.id}>
                            <input type="checkbox" name={name} value={option.id} defaultChecked={selected.has(option.id)} />
                            <span>{option.value ?? "Giá trị"}</span>
                          </label>
                        ) : null)}
                      </fieldset>
                    );
                  }

                  if (definition.dataType === "SINGLE_SELECT") {
                    return (
                      <label key={definition.id ?? attributeKey}>
                        {label}
                        <select name={name} defaultValue={current?.optionId ?? ""}>
                          <option value="">{definition.required ? "Chọn một giá trị" : "Không chọn"}</option>
                          {(definition.options ?? []).map((option) => option.id ? (
                            <option key={option.id} value={option.id}>{option.value}</option>
                          ) : null)}
                        </select>
                      </label>
                    );
                  }

                  if (definition.dataType === "BOOLEAN") {
                    return (
                      <label key={definition.id ?? attributeKey}>
                        {label}
                        <select name={name} defaultValue={current?.valueBoolean != null ? String(current.valueBoolean) : ""}>
                          <option value="">{definition.required ? "Chọn một giá trị" : "Không chọn"}</option>
                          <option value="true">Có</option>
                          <option value="false">Không</option>
                        </select>
                      </label>
                    );
                  }

                  return (
                    <label key={definition.id ?? attributeKey}>
                      {label}
                      <input
                        name={name}
                        type={definition.dataType === "NUMBER" ? "number" : "text"}
                        step={definition.dataType === "NUMBER" ? "any" : undefined}
                        defaultValue={definition.dataType === "NUMBER" ? current?.valueNumber ?? "" : current?.valueText ?? ""}
                      />
                    </label>
                  );
                })}
              </fieldset>
            ) : categoryStatus === "error" ? (
              <div className="seller-contract-note">
                <p>Không tải được định nghĩa thuộc tính. Bạn vẫn có thể sửa thông tin chung; thuộc tính sẽ giữ nguyên.</p>
                <button
                  type="button"
                  className="seller-button seller-button--secondary"
                  disabled={pending || stale}
                  onClick={() => {
                    setCategoryStatus("loading");
                    setCategoryAttempt((attempt) => attempt + 1);
                  }}
                >
                  Thử tải lại thuộc tính
                </button>
              </div>
            ) : (
              <p className="seller-muted">Đang tải thuộc tính danh mục…</p>
            )}

            <label>
              Thêm ảnh
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                multiple
                disabled={pending || stale}
                onChange={(event) => setFiles(Array.from(event.target.files ?? []))}
              />
            </label>

            <button type="submit" disabled={pending || stale} className="seller-button seller-button--primary">
              <FloppyDisk size={16} weight="bold" aria-hidden="true" />
              <span>Lưu thông tin</span>
            </button>
          </form>
        ) : (
          <p className="seller-muted">Bạn có quyền xem nhưng không có quyền sửa cấu trúc nội dung này.</p>
        )}

        <dl className="seller-catalog-editor__attributes">
          {(item.attributes ?? []).map((attribute) => (
            <div key={`${attribute.categoryAttributeId}-${attribute.optionId ?? "value"}`}>
              <dt>{attribute.attributeKey}</dt>
              <dd>{attribute.attributeKey}: {attributeValue(attribute)}</dd>
            </div>
          ))}
        </dl>
      </section>

      {/* PANEL 2: ẢNH */}
      <section className="seller-catalog-editor__panel">
        <div className="seller-catalog-editor__panel-title">
          <Images size={20} weight="duotone" aria-hidden="true" />
          <h2>Ảnh</h2>
        </div>

        {(item.media ?? []).length ? (
          <div className="seller-catalog-editor__media">
            {(item.media ?? []).map((media, index) => media.id ? (
              <figure key={media.id} className="seller-catalog-media-card">
                {media.url ? (
                  <Image src={media.url} alt={`${item.name ?? "Catalog"} — ảnh ${index + 1}`} width={160} height={120} unoptimized />
                ) : (
                  <div className="seller-catalog-placeholder" aria-label={`Ảnh ${index + 1} chưa sẵn sàng`} />
                )}
                <figcaption>
                  <span className="seller-media-status">{media.status ?? "UNKNOWN"}</span>
                  {media.primary ? <span className="seller-media-primary-badge"> · Ảnh chính</span> : null}
                </figcaption>
              </figure>
            ) : null)}
          </div>
        ) : (
          <p className="seller-muted">Chưa có ảnh. Có thể bổ sung ảnh trong lần cập nhật nội dung tiếp theo.</p>
        )}
      </section>

      {/* PANEL 3: BIẾN THỂ HOẶC GÓI DỊCH VỤ */}
      <section className="seller-catalog-editor__panel">
        <div className="seller-catalog-editor__panel-title">
          {item.type === "SERVICE" ? (
            <Wrench size={20} weight="duotone" aria-hidden="true" />
          ) : (
            <Package size={20} weight="duotone" aria-hidden="true" />
          )}
          <h2>{item.type === "SERVICE" ? "Gói dịch vụ" : "Biến thể"}</h2>
        </div>

        {(item.optionGroups ?? []).map((group) => (
          <p className="seller-muted" key={group.id}>
            {group.name}: {(group.values ?? []).map((value) => value.value).join(", ")}
          </p>
        ))}

        {(item.variants ?? []).length ? (
          (item.variants ?? []).map((variant) => (
            <form
              className="seller-catalog-editor__resource"
              key={variant.id}
              onSubmit={(event) => {
                event.preventDefault();
                const form = new FormData(event.currentTarget);
                if (!variant.id || variant.version == null) return;
                void mutate(() => updateCatalogVariant(merchantId, itemId, variant.id!, variant.version!, {
                  sku: String(form.get("sku") ?? "").trim(),
                  variantName: String(form.get("variantName") ?? "").trim(),
                  variantStatus: String(form.get("variantStatus") ?? variant.status) as components["schemas"]["UpdateVariantRequest"]["variantStatus"],
                }));
              }}
            >
              <label>
                Tên biến thể
                <input name="variantName" defaultValue={variant.name} disabled={pending || stale || !(variant.allowedActions ?? []).includes("UPDATE")} />
              </label>

              <label>
                SKU
                <input name="sku" defaultValue={variant.sku} disabled={pending || stale || !(variant.allowedActions ?? []).includes("UPDATE")} />
              </label>

              <label>
                Trạng thái biến thể
                <select name="variantStatus" defaultValue={variant.status} disabled={pending || stale || !(variant.allowedActions ?? []).includes("UPDATE")}>
                  <option value="ACTIVE">Hoạt động</option>
                  <option value="PAUSED">Tạm ngưng</option>
                </select>
              </label>

              <div className="seller-catalog-resource__actions">
                {(variant.allowedActions ?? []).includes("UPDATE") ? (
                  <button type="submit" disabled={pending || stale} className="seller-button seller-button--secondary">
                    <FloppyDisk size={14} weight="bold" aria-hidden="true" />
                    <span>Lưu biến thể {variant.name}</span>
                  </button>
                ) : null}

                {(variant.allowedActions ?? []).includes("DELETE") ? (
                  <button
                    type="button"
                    disabled={pending || stale}
                    className="seller-button seller-button--danger"
                    onClick={() => variant.id != null && variant.version != null && window.confirm("Xóa biến thể này?") && void mutate(() => deleteCatalogVariant(merchantId, itemId, variant.id!, variant.version!))}
                  >
                    <Trash size={14} weight="bold" aria-hidden="true" />
                    <span>Xóa biến thể {variant.name}</span>
                  </button>
                ) : null}
              </div>
            </form>
          ))
        ) : (
          <p className="seller-muted">Chưa có biến thể.</p>
        )}

        {itemActions.has("CREATE_VARIANT") ? (
          <form
            className="seller-catalog-editor__resource seller-catalog-editor__resource--create"
            onSubmit={(event) => {
              event.preventDefault();
              const form = new FormData(event.currentTarget);
              void mutate(() => createCatalogVariant(merchantId, itemId, {
                sku: String(form.get("newSku") ?? "").trim() || undefined,
                variantName: String(form.get("newVariantName") ?? "").trim(),
                optionValueIds: (item.optionGroups ?? []).map((_, index) => String(form.get(`newOption:${index}`) ?? "")).filter(Boolean),
              }));
            }}
          >
            <h3>Thêm biến thể</h3>
            <label>
              Tên biến thể mới
              <input name="newVariantName" required disabled={pending || stale} />
            </label>
            <label>
              SKU mới
              <input name="newSku" disabled={pending || stale} />
            </label>
            {(item.optionGroups ?? []).map((group, index) => (
              <label key={group.id}>
                {group.name}
                <select name={`newOption:${index}`} required disabled={pending || stale}>
                  {(group.values ?? []).map((value) => value.id ? (
                    <option key={value.id} value={value.id}>{value.value}</option>
                  ) : null)}
                </select>
              </label>
            ))}
            <button type="submit" disabled={pending || stale} className="seller-button seller-button--primary">
              <PlusCircle size={15} weight="bold" aria-hidden="true" />
              <span>Thêm biến thể</span>
            </button>
          </form>
        ) : null}

        {(item.optionGroups ?? []).length ? (
          <p className="seller-contract-note">Cấu trúc lựa chọn hiện chỉ đọc để bảo toàn các tổ hợp đang dùng.</p>
        ) : null}
      </section>

      {/* PANEL 4: ĐIỂM BÁN & GIÁ */}
      <section className="seller-catalog-editor__panel">
        <div className="seller-catalog-editor__panel-title">
          <Storefront size={20} weight="duotone" aria-hidden="true" />
          <h2>Điểm bán & giá</h2>
        </div>

        {(item.locationOffers ?? []).length ? (
          (item.locationOffers ?? []).map((offer) => (
            <form
              className="seller-catalog-editor__resource"
              key={offer.id}
              onSubmit={(event) => {
                event.preventDefault();
                const form = new FormData(event.currentTarget);
                if (!offer.id || offer.version == null) return;
                const preparationRaw = String(form.get("preparationTimeMinutes") ?? "").trim();
                void mutate(() => updateCatalogLocationOffer(merchantId, itemId, offer.id!, offer.version!, {
                  price: Number(form.get("price")),
                  available: form.get("available") === "on",
                  ...(item.type === "PRODUCT" ? {
                    inventoryMode: String(form.get("inventoryMode")) as components["schemas"]["UpdateLocationOfferRequest"]["inventoryMode"],
                  } : {}),
                  ...(preparationRaw ? { preparationTimeMinutes: Number(preparationRaw) }
                    : offer.preparationTimeMinutes != null ? { preparationTimeMinutes: null } : {}),
                }));
              }}
            >
              <h3>{offer.merchantLocationName ?? "Điểm bán"}</h3>

              <label>
                {`Giá tại ${offer.merchantLocationName ?? "điểm bán"}`}
                <input
                  name="price"
                  type="number"
                  min="0"
                  step="1"
                  defaultValue={offer.price}
                  disabled={pending || stale || !(offer.allowedActions ?? []).includes("UPDATE")}
                />
              </label>

              <label className="seller-check-row">
                <input
                  name="available"
                  type="checkbox"
                  defaultChecked={offer.available}
                  disabled={pending || stale || !(offer.allowedActions ?? []).includes("UPDATE")}
                />
                <span>Đang nhận đơn</span>
              </label>

              <label>
                Thời gian chuẩn bị (phút)
                <input
                  name="preparationTimeMinutes"
                  type="number"
                  min="0"
                  step="1"
                  defaultValue={offer.preparationTimeMinutes ?? ""}
                  disabled={pending || stale || !(offer.allowedActions ?? []).includes("UPDATE")}
                />
              </label>

              {item.type === "PRODUCT" ? (
                <label>
                  Quản lý tồn kho
                  <select
                    name="inventoryMode"
                    defaultValue={offer.inventoryMode}
                    disabled={pending || stale || !(offer.allowedActions ?? []).includes("UPDATE")}
                  >
                    <option value="UNTRACKED">Không theo dõi</option>
                    <option value="TRACKED">Theo dõi tồn kho</option>
                  </select>
                </label>
              ) : null}

              {(offer.allowedActions ?? []).includes("UPDATE") ? (
                <button type="submit" disabled={pending || stale} className="seller-button seller-button--primary">
                  <FloppyDisk size={14} weight="bold" aria-hidden="true" />
                  <span>{`Lưu ${offer.merchantLocationName ?? "điểm bán"}`}</span>
                </button>
              ) : null}
            </form>
          ))
        ) : (
          <p className="seller-muted">Chưa có giá tại điểm bán.</p>
        )}
      </section>

      {/* LIFECYCLE ACTION BAR */}
      <div className="seller-catalog-editor__actions" aria-label="Hành động vòng đời">
        {(["PUBLISH", "PAUSE", "ACTIVATE", "ARCHIVE"] as const).map((action) =>
          itemActions.has(action as ItemAction) ? (
            <button
              type="button"
              key={action}
              disabled={pending || stale}
              onClick={() => lifecycle(action)}
              className={`seller-button ${action === "PUBLISH" || action === "ACTIVATE" ? "seller-button--primary" : action === "ARCHIVE" ? "seller-button--danger" : "seller-button--secondary"}`}
            >
              {action === "PUBLISH" ? (
                <CloudArrowUp size={16} weight="bold" aria-hidden="true" />
              ) : action === "PAUSE" ? (
                <Pause size={16} weight="bold" aria-hidden="true" />
              ) : action === "ACTIVATE" ? (
                <CheckCircle size={16} weight="bold" aria-hidden="true" />
              ) : (
                <Archive size={16} weight="bold" aria-hidden="true" />
              )}
              <span>{lifecycleLabels[action]}</span>
            </button>
          ) : null
        )}
      </div>
    </section>
  );
}
