"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useEffect, useMemo, useRef, useState, useSyncExternalStore } from "react";
import { useRouter } from "next/navigation";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import {
  CheckCircle,
  Clock,
  CurrencyDollar,
  Images,
  Package,
  PlusCircle,
  SlidersHorizontal,
  Sparkle,
  Tag,
  UploadSimple,
  Wrench,
} from "@phosphor-icons/react";
import { SelectDropdown } from "@/components/ui/select-dropdown";
import { createCatalogItem, getCategoryDetail } from "./catalog-api";
import { SellerBarcodeLookup } from "./seller-barcode-lookup";
import { SocialCaptionField } from "./social-caption-field";
import { CatalogSocialPreview } from "./catalog-social-preview";
import { CatalogMediaEditor, useCatalogGallery } from "./catalog-media-editor";

type Category = components["schemas"]["CategorySummary"];
type CategoryDetail = components["schemas"]["CategoryDetail"];
type AttributeDefinition = components["schemas"]["AttributeDefinition"];
type AttributeValue = components["schemas"]["AttributeValueRequest"];
type Location = components["schemas"]["MerchantLocationResponse"];
type CatalogType = components["schemas"]["CreateCatalogItemRequest"]["type"];
type ListingKind = components["schemas"]["CreateCatalogItemRequest"]["listingKind"];
type InventoryMode = NonNullable<components["schemas"]["LocationOfferCreateRequest"]["inventoryMode"]>;

function clientKey(value: string, fallback: string) {
  return value.trim().toLowerCase().normalize("NFD").replace(/[\u0300-\u036f]/g, "").replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || fallback;
}

function integerField(form: FormData, name: string, minimum = 0) {
  const value = Number(form.get(name));
  if (!Number.isSafeInteger(value) || value < minimum) throw new Error("INVALID_INTEGER");
  return value;
}

function optionValuesFrom(value: string) {
  return [...new Set(value.split(",").map((part) => part.trim()).filter(Boolean))];
}

function attributeFieldName(attributeKey: string) {
  return `attribute:${attributeKey}`;
}

function categoryAttributeValues(form: FormData, definitions: AttributeDefinition[]): AttributeValue[] {
  const values: AttributeValue[] = [];

  for (const definition of definitions) {
    const attributeKey = definition.attributeKey?.trim();
    if (!attributeKey || !definition.dataType) continue;
    const name = attributeFieldName(attributeKey);

    if (definition.dataType === "MULTI_SELECT") {
      const optionIds = form.getAll(name).map(String).filter(Boolean);
      if (definition.required && optionIds.length === 0) throw new Error("ATTRIBUTE_REQUIRED");
      values.push(...optionIds.map((optionId) => ({ attributeKey, optionId })));
      continue;
    }

    const raw = String(form.get(name) ?? "").trim();
    if (!raw) {
      if (definition.required) throw new Error("ATTRIBUTE_REQUIRED");
      continue;
    }

    switch (definition.dataType) {
      case "TEXT":
        values.push({ attributeKey, valueText: raw });
        break;
      case "NUMBER": {
        const valueNumber = Number(raw);
        if (!Number.isFinite(valueNumber)) throw new Error("ATTRIBUTE_INVALID");
        values.push({ attributeKey, valueNumber });
        break;
      }
      case "BOOLEAN":
        if (raw !== "true" && raw !== "false") throw new Error("ATTRIBUTE_INVALID");
        values.push({ attributeKey, valueBoolean: raw === "true" });
        break;
      case "SINGLE_SELECT":
        values.push({ attributeKey, optionId: raw });
        break;
    }
  }

  return values;
}

function CategoryAttributeFields({ definitions, pending }: { definitions: AttributeDefinition[]; pending: boolean }) {
  if (!definitions.length) return null;

  return (
    <fieldset className="seller-attribute-fields" disabled={pending}>
      <legend>Thuộc tính danh mục</legend>
      {definitions.map((definition, index) => {
        const attributeKey = definition.attributeKey?.trim();
        if (!attributeKey || !definition.dataType) return null;
        const name = attributeFieldName(attributeKey);
        const label = definition.name?.trim() || `Thuộc tính ${index + 1}`;
        const requiredMark = definition.required ? <span className="seller-required-star" aria-hidden="true"> *</span> : null;

        if (definition.dataType === "SINGLE_SELECT") {
          return (
            <SelectDropdown<string>
              key={attributeKey}
              label={label}
              name={name}
              required={definition.required}
              defaultValue=""
              radius="md"
              size="md"
              options={[
                { value: "", label: definition.required ? "Chọn một giá trị" : "Không chọn" },
                ...(definition.options ?? [])
                  .filter((opt) => Boolean(opt.id))
                  .map((opt) => ({
                    value: opt.id!,
                    label: opt.value ?? "Giá trị",
                  })),
              ]}
            />
          );
        }

        if (definition.dataType === "MULTI_SELECT") {
          return (
            <fieldset className="seller-attribute-options" key={attributeKey}>
              <legend><span className="seller-field-title">{label}{requiredMark}</span></legend>
              {(definition.options ?? []).map((option) => option.id ? (
                <label className="seller-check-row" key={option.id}>
                  <input type="checkbox" name={name} value={option.id} />
                  <span>{option.value ?? "Giá trị"}</span>
                </label>
              ) : null)}
            </fieldset>
          );
        }

        if (definition.dataType === "BOOLEAN") {
          return (
            <SelectDropdown<string>
              key={attributeKey}
              label={label}
              name={name}
              required={definition.required}
              defaultValue=""
              radius="md"
              size="md"
              options={[
                { value: "", label: definition.required ? "Chọn một giá trị" : "Không chọn" },
                { value: "true", label: "Có" },
                { value: "false", label: "Không" },
              ]}
            />
          );
        }

        return (
          <label key={attributeKey}>
            <span className="seller-field-title">
              {label}{requiredMark}
            </span>
            <input
              name={name}
              type={definition.dataType === "NUMBER" ? "number" : "text"}
              inputMode={definition.dataType === "NUMBER" ? "decimal" : undefined}
              step={definition.dataType === "NUMBER" ? "any" : undefined}
              required={definition.required}
            />
          </label>
        );
      })}
    </fieldset>
  );
}

export function CatalogCreateForm({
  merchantId,
  categories,
  locations,
  socialAuthoringEnabled = false,
}: {
  merchantId: string;
  categories: Category[];
  locations: Location[];
  socialAuthoringEnabled?: boolean;
}) {
  const router = useRouter();
  const hydrated = useSyncExternalStore(() => () => undefined, () => true, () => false);
  const mediaAuthoringEnabled = socialAuthoringEnabled || process.env.NEXT_PUBLIC_COVIE_SOCIAL_V2_VIDEO_ENABLED === "true";
  const socialHydrating = mediaAuthoringEnabled && !hydrated;
  const idempotencyKeyRef = useRef<string | undefined>(undefined);
  const gallery = useCatalogGallery([], invalidateIntent);
  const [type, setType] = useState<CatalogType>("PRODUCT");
  const [listingKind, setListingKind] = useState<ListingKind>("STANDARD");
  const [inventoryMode, setInventoryMode] = useState<InventoryMode>("UNTRACKED");
  const [selectedCategoryId, setSelectedCategoryId] = useState("");
  const [categoryDetail, setCategoryDetail] = useState<CategoryDetail>();
  const [categoryLoading, setCategoryLoading] = useState(false);
  const [categoryError, setCategoryError] = useState<string>();
  const [withOptions, setWithOptions] = useState(false);
  const [optionValuesInput, setOptionValuesInput] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const previewUrls = useMemo(() => files.map((file) => URL.createObjectURL(file)), [files]);
  useEffect(() => {
    return () => {
      previewUrls.forEach((url) => URL.revokeObjectURL(url));
    };
  }, [previewUrls]);
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const [progress, setProgress] = useState<string>();

  const compatibleCategories = useMemo(
    () => categories.filter((category) => category.catalogType === "BOTH" || category.catalogType === type),
    [categories, type],
  );
  const compatibleLocations = useMemo(
    () => locations.filter((location) => type !== "SERVICE" || location.serviceEnabled === true),
    [locations, type],
  );
  const optionValues = useMemo(() => optionValuesFrom(optionValuesInput), [optionValuesInput]);
  const serviceVariantOffers = type === "SERVICE" && withOptions && optionValues.length > 0;

  useEffect(() => {
    if (!selectedCategoryId) return;

    let active = true;
    void getCategoryDetail(selectedCategoryId)
      .then((detail) => {
        if (active) setCategoryDetail(detail);
      })
      .catch(() => {
        if (active) setCategoryError("Không thể tải thuộc tính của danh mục. Vui lòng chọn lại hoặc thử lại.");
      })
      .finally(() => {
        if (active) setCategoryLoading(false);
      });
    return () => {
      active = false;
    };
  }, [selectedCategoryId]);

  function resetCategorySelection() {
    setSelectedCategoryId("");
    setCategoryDetail(undefined);
    setCategoryError(undefined);
    setCategoryLoading(false);
  }

  function selectCategory(categoryId: string) {
    setSelectedCategoryId(categoryId);
    setCategoryDetail(undefined);
    setCategoryError(undefined);
    setCategoryLoading(Boolean(categoryId));
  }

  function invalidateIntent() {
    if (!pending) idempotencyKeyRef.current = undefined;
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    setPending(true);
    setMessage(undefined);

    try {
      const categoryId = String(form.get("categoryId") ?? "");
      const locationId = String(form.get("locationId") ?? "");
      if (!compatibleCategories.some((category) => category.id === categoryId)) throw new Error("CATEGORY_REQUIRED");
      if (!compatibleLocations.some((location) => location.id === locationId)) throw new Error("LOCATION_REQUIRED");
      if (!categoryDetail || categoryDetail.id !== categoryId) throw new Error("CATEGORY_DETAIL_REQUIRED");

      const preparationTimeMinutes = integerField(form, "preparationTimeMinutes");
      const optionName = String(form.get("optionName") ?? "").trim();
      const hasOptions = withOptions && optionName.length > 0 && optionValues.length > 0;
      if (withOptions && !hasOptions) throw new Error("OPTIONS_REQUIRED");
      const perVariantServiceOffer = type === "SERVICE" && hasOptions;
      const sharedPrice = perVariantServiceOffer ? undefined : integerField(form, "price");
      const attributes = categoryAttributeValues(form, categoryDetail.attributes ?? []);
      const socialCaption = String(form.get("socialCaption") ?? "");
      if (socialAuthoringEnabled && socialCaption.length > 3000) throw new Error("Caption không được vượt quá 3.000 UTF-16 units.");

      const mediaIds: string[] = mediaAuthoringEnabled ? await gallery.resolveMediaIds(setProgress) : [];
      for (const [index, file] of (mediaAuthoringEnabled ? [] : files).entries()) {
        setProgress(`Đang tải ảnh ${index + 1}/${files.length}…`);
        try {
          const uploaded = await uploadImage(file, "CATALOG_IMAGE");
          if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
          mediaIds.push(uploaded.mediaId);
        } catch (error) {
          if (error instanceof Error && ["MEDIA_TYPE_NOT_SUPPORTED", "MEDIA_FILE_TOO_LARGE"].includes(error.message)) throw error;
          throw new Error("MEDIA_UPLOAD_FAILED", { cause: error });
        }
      }

      const optionGroupKey = clientKey(optionName, "option");
      const optionRows = hasOptions ? optionValues.map((value, index) => ({
        clientKey: `${clientKey(value, "value")}-${index + 1}`,
        value,
      })) : [];
      const variants = hasOptions ? optionRows.map((option, index) => ({
        clientKey: `variant-${index + 1}`,
        name: option.value,
        optionValueKeys: [option.clientKey],
      })) : [];
      const variantKeys = hasOptions ? variants.map((variant) => variant.clientKey) : ["__default__"];

      const locationOffers = variantKeys.map((variantClientKey, index) => ({
        merchantLocationId: locationId,
        variantClientKey,
        price: perVariantServiceOffer ? integerField(form, `variantPrice:${index}`) : sharedPrice,
        currency: "VND",
        available: perVariantServiceOffer ? form.get(`variantAvailable:${index}`) === "on" : true,
        inventoryMode: type === "PRODUCT" ? inventoryMode : undefined,
        preparationTimeMinutes,
      }));

      setProgress("Đang tạo nội dung…");
      const idempotencyKey = idempotencyKeyRef.current ?? crypto.randomUUID();
      idempotencyKeyRef.current = idempotencyKey;
      await createCatalogItem(merchantId, idempotencyKey, {
        type,
        listingKind: type === "SERVICE" ? "STANDARD" : listingKind,
        categoryId,
        name: String(form.get("name") ?? "").trim(),
        description: String(form.get("description") ?? "").trim() || undefined,
        ...(socialAuthoringEnabled ? { socialCaption: socialCaption.trim() || null } : {}),
        tags: String(form.get("tags") ?? "").split(",").map((value) => value.trim()).filter(Boolean),
        mediaIds,
        attributes,
        options: hasOptions ? [{ clientKey: optionGroupKey, name: optionName, values: optionRows }] : [],
        variants,
        locationOffers,
      });

      idempotencyKeyRef.current = undefined;
      setProgress("Đã tạo nội dung.");
      router.push(`/seller/${encodeURIComponent(merchantId)}/catalog`);
      router.refresh();
    } catch (error) {
      setProgress(undefined);
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Dữ liệu đã thay đổi. Hãy kiểm tra lại thông tin rồi thử lại.");
      } else if (error instanceof Error && error.message === "MEDIA_TYPE_NOT_SUPPORTED") {
        setMessage("Ảnh phải là JPEG, PNG hoặc WebP.");
      } else if (error instanceof Error && error.message === "MEDIA_FILE_TOO_LARGE") {
        setMessage("Mỗi ảnh phải nhỏ hơn hoặc bằng 10 MB.");
      } else if (error instanceof Error && error.message === "MEDIA_UPLOAD_FAILED") {
        setMessage("Không thể tải ảnh lên. Thông tin đã nhập được giữ lại; hãy thử lại.");
      } else if (error instanceof Error && ["ATTRIBUTE_REQUIRED", "ATTRIBUTE_INVALID"].includes(error.message)) {
        setMessage("Vui lòng kiểm tra các thuộc tính bắt buộc của danh mục.");
      } else if (error instanceof Error && ["CATEGORY_REQUIRED", "CATEGORY_DETAIL_REQUIRED", "LOCATION_REQUIRED", "OPTIONS_REQUIRED", "INVALID_INTEGER"].includes(error.message)) {
        setMessage("Vui lòng kiểm tra danh mục, điểm bán, biến thể và các trường số.");
      } else {
        setMessage(error instanceof Error ? error.message : "Không thể tạo nội dung. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  const [name, setName] = useState("");
  const [previewName, setPreviewName] = useState("");
  const [previewCaption, setPreviewCaption] = useState("");
  const [previewPrice, setPreviewPrice] = useState<number>();
  const [previewPrepTime, setPreviewPrepTime] = useState<number>(0);

  const canSubmit = compatibleCategories.length > 0
    && compatibleLocations.length > 0
    && Boolean(selectedCategoryId)
    && Boolean(categoryDetail)
    && !categoryLoading
    && !categoryError;

  const currentCategoryName = compatibleCategories.find((c) => c.id === selectedCategoryId)?.name;

  return (
    <div className="seller-catalog-create-layout">
      <div className="seller-catalog-create-main">
        <form className="seller-catalog-form" onSubmit={submit} onChange={invalidateIntent} aria-busy={pending}>
          <header>
            <p className="eyebrow">Catalog / Tạo mới</p>
            <h1>Tạo nội dung bán</h1>
            <p className="seller-muted">Tạo sản phẩm, dịch vụ hoặc hàng thanh lý từ danh mục và điểm bán hiện có.</p>
          </header>

          {/* CARD 1: PHÂN LOẠI & NGÀNH HÀNG */}
          <section className="seller-studio-card" aria-label="Phân loại nội dung">
            <div className="seller-studio-card__header">
              <Tag size={20} weight="duotone" className="seller-studio-card__icon" aria-hidden="true" />
              <div>
                <h3>1. Phân loại nội dung</h3>
                <p>Chọn hình thức kinh doanh và danh mục niêm yết phù hợp trên CoVie</p>
              </div>
            </div>

            <div className="seller-studio-grid-2">
              <SelectDropdown<CatalogType>
                label="Loại"
                name="type"
                value={type}
                radius="md"
                size="md"
                options={[
                  { value: "PRODUCT", label: "Sản phẩm" },
                  { value: "SERVICE", label: "Dịch vụ" },
                ]}
                onChange={(next) => {
                  setType(next);
                  resetCategorySelection();
                  if (next === "SERVICE") setListingKind("STANDARD");
                }}
              />
              {type === "PRODUCT" ? (
                <SelectDropdown<ListingKind>
                  label="Loại sản phẩm"
                  name="listingKind"
                  value={listingKind}
                  radius="md"
                  size="md"
                  options={[
                    { value: "STANDARD", label: "Sản phẩm tiêu chuẩn" },
                    { value: "RESALE", label: "Thanh lý" },
                  ]}
                  onChange={(val) => setListingKind(val)}
                />
              ) : null}
            </div>

            <SelectDropdown<string>
              label="Danh mục"
              name="categoryId"
              placeholder="Chọn danh mục"
              required
              value={selectedCategoryId}
              radius="md"
              size="md"
              options={[
                { value: "", label: "Chọn danh mục" },
                ...compatibleCategories
                  .filter((category) => Boolean(category.id))
                  .map((category) => ({
                    value: category.id!,
                    label: category.name ?? "Danh mục",
                  })),
              ]}
              onChange={(val) => selectCategory(val)}
            />
            {!compatibleCategories.length ? <p className="warning-banner" role="status">Chưa có danh mục phù hợp với loại nội dung này.</p> : null}
            {categoryLoading ? <p className="seller-muted" role="status">Đang tải thuộc tính danh mục…</p> : null}
            {categoryError ? <p className="seller-contract-note" role="alert">{categoryError}</p> : null}
            {categoryDetail ? <CategoryAttributeFields definitions={categoryDetail.attributes ?? []} pending={pending} /> : null}
            {listingKind === "RESALE" && categoryDetail && !(categoryDetail.attributes ?? []).length ? (
              <p className="seller-contract-note" role="status">Danh mục này chưa có tùy chọn tình trạng để chọn.</p>
            ) : null}
          </section>

          {/* CARD 2: THÔNG TIN SẢN PHẨM / DỊCH VỤ */}
          <section className="seller-studio-card" aria-label="Thông tin cơ bản">
            <div className="seller-studio-card__header">
              <Package size={20} weight="duotone" className="seller-studio-card__icon" aria-hidden="true" />
              <div>
                <h3>2. Thông tin cơ bản</h3>
                <p>Tên gọi, mô tả chi tiết và từ khóa tìm kiếm cho cư dân</p>
              </div>
            </div>

            {type === "PRODUCT" ? (
              <SellerBarcodeLookup
                mode="create"
                disabled={pending}
                currentProductName={name}
                onApplyProductName={(appliedName) => {
                  setName(appliedName);
                  setPreviewName(appliedName);
                  invalidateIntent();
                }}
              />
            ) : null}

            <label>
              <span className="seller-field-title">
                Tên <span className="seller-required-star" aria-hidden="true">*</span>
              </span>
              <input
                aria-label="Tên"
                name="name"
                autoComplete="off"
                maxLength={240}
                required
                value={name}
                placeholder="Ví dụ: Cà phê muối 500ml, Bánh mì chảo, Vệ sinh máy lạnh..."
                onChange={(e) => {
                  setName(e.target.value);
                  setPreviewName(e.target.value);
                  invalidateIntent();
                }}
              />
            </label>

            <label>
              <span className="seller-field-title">Mô tả chi tiết</span>
              <textarea
                aria-label="Mô tả"
                name="description"
                maxLength={4000}
                placeholder="Mô tả định lượng, thành phần, hương vị hoặc các lưu ý khi đặt món/dịch vụ..."
              />
            </label>

            {socialAuthoringEnabled && <SocialCaptionField disabled={pending || socialHydrating} onChange={value => { setPreviewCaption(value); invalidateIntent(); }} />}

            <label>
              <span className="seller-field-title">Thẻ tìm kiếm (Tags)</span>
              <input
                aria-label="Thẻ"
                name="tags"
                placeholder="ví dụ: handmade, giao nhanh, nóng hổi"
              />
            </label>
          </section>

          {/* CARD 3: HÌNH ẢNH NIÊM YẾT */}
          <section className="seller-studio-card" aria-label="Hình ảnh">
            <div className="seller-studio-card__header">
              <Images size={20} weight="duotone" className="seller-studio-card__icon" aria-hidden="true" />
              <div>
                <h3>3. Hình ảnh niêm yết</h3>
                <p>Ảnh chụp thực tế, sắc nét giúp thu hút cư dân đặt hàng nhanh hơn</p>
              </div>
            </div>

            {mediaAuthoringEnabled ? <CatalogMediaEditor gallery={gallery} disabled={pending || socialHydrating} /> : <>
            <label className="seller-catalog-upload-zone">
              <UploadSimple size={28} weight="bold" color="#047857" aria-hidden="true" />
              <div>
                <span className="seller-media-btn" style={{ display: "inline-flex" }}>
                  <UploadSimple size={16} weight="bold" aria-hidden="true" />
                  <span>Chọn ảnh sản phẩm / dịch vụ</span>
                </span>
                <p className="seller-muted" style={{ margin: "6px 0 0" }}>JPEG, PNG hoặc WebP; tối đa 10 MB mỗi ảnh. Hỗ trợ chọn nhiều ảnh cùng lúc.</p>
              </div>
              <input
                type="file"
                aria-label="Ảnh catalog"
                accept="image/jpeg,image/png,image/webp"
                multiple
                onChange={(event) => {
                  const newFiles = Array.from(event.target.files ?? []);
                  setFiles((prev) => [...prev, ...newFiles]);
                  idempotencyKeyRef.current = undefined;
                  event.target.value = "";
                }}
              />
            </label>

            {files.length ? (
              <div style={{ marginTop: "4px" }}>
                <p role="status" style={{ fontSize: "13px", fontWeight: 600, color: "#047857", marginBottom: "8px" }}>
                  Đã chọn {files.length} ảnh (Ảnh đầu tiên sẽ là ảnh chính):
                </p>
                <div className="seller-catalog-staged-grid">
                  {files.map((file, idx) => (
                    <div key={`${file.name}-${idx}`} className="seller-catalog-staged-item">
                      {previewUrls[idx] ? (
                        <img src={previewUrls[idx]} alt={file.name} />
                      ) : null}
                      {idx === 0 ? <span className="seller-catalog-staged-item__badge">Chính</span> : null}
                      <button
                        type="button"
                        className="seller-catalog-staged-item__remove"
                        aria-label={`Xoá ảnh ${file.name}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          setFiles((prev) => prev.filter((_, i) => i !== idx));
                          idempotencyKeyRef.current = undefined;
                        }}
                      >
                        ✕
                      </button>
                    </div>
                  ))}
                </div>
              </div>
            ) : null}
            </>}
          </section>

          {/* CARD 4: GIÁ BÁN & VẬN HÀNH */}
          <section className="seller-studio-card" aria-label="Giá bán và điểm phục vụ">
            <div className="seller-studio-card__header">
              <CurrencyDollar size={20} weight="duotone" className="seller-studio-card__icon" aria-hidden="true" />
              <div>
                <h3>4. Giá bán & Vận hành</h3>
                <p>Thiết lập giá và điểm nhận đơn cho cư dân trong khu căn hộ</p>
              </div>
            </div>

            <div className="seller-studio-grid-2">
              <SelectDropdown<string>
                label="Điểm bán"
                name="locationId"
                placeholder="Chọn điểm bán"
                required
                defaultValue=""
                radius="md"
                size="md"
                options={[
                  { value: "", label: "Chọn điểm bán" },
                  ...compatibleLocations
                    .filter((location) => Boolean(location.id))
                    .map((location) => ({
                      value: location.id!,
                      label: `${location.name ?? "Điểm bán"}${location.addressText ? ` — ${location.addressText}` : ""}`,
                    })),
                ]}
              />
              {type === "PRODUCT" ? (
                <SelectDropdown<InventoryMode>
                  label="Quản lý tồn kho"
                  name="inventoryMode"
                  value={inventoryMode}
                  radius="md"
                  size="md"
                  options={[
                    { value: "UNTRACKED", label: "Không theo dõi số lượng" },
                    { value: "TRACKED", label: "Theo dõi tồn kho" },
                  ]}
                  onChange={(val) => setInventoryMode(val)}
                />
              ) : null}
            </div>
            {!compatibleLocations.length ? <p className="warning-banner" role="status">Chưa có điểm bán phù hợp. Với dịch vụ, hãy bật phục vụ dịch vụ tại ít nhất một điểm bán.</p> : null}

            <div className="seller-studio-grid-2">
              {!serviceVariantOffers ? (
                <label>
                  <span className="seller-field-title">
                    Giá chính xác (VND) <span className="seller-required-star" aria-hidden="true">*</span>
                  </span>
                  <input
                    aria-label="Giá chính xác (VND)"
                    name="price"
                    type="number"
                    inputMode="numeric"
                    min="0"
                    step="1"
                    required
                    placeholder="ví dụ: 25000"
                    onChange={(e) => setPreviewPrice(Number(e.target.value) || undefined)}
                  />
                </label>
              ) : <p className="seller-muted">Mỗi gói dịch vụ có giá và trạng thái nhận đơn riêng bên dưới.</p>}

              <label>
                <span className="seller-field-title">
                  Thời gian chuẩn bị (phút) <span className="seller-required-star" aria-hidden="true">*</span>
                </span>
                <input
                  aria-label="Thời gian chuẩn bị (phút)"
                  name="preparationTimeMinutes"
                  type="number"
                  inputMode="numeric"
                  min="0"
                  step="1"
                  defaultValue="0"
                  required
                  onChange={(e) => setPreviewPrepTime(Number(e.target.value) || 0)}
                />
              </label>
            </div>
          </section>

          {/* CARD 5: TÙY CHỌN & BIẾN THỂ */}
          <section className="seller-studio-card" aria-label="Biến thể và gói tùy chọn">
            <div className="seller-studio-card__header">
              <SlidersHorizontal size={20} weight="duotone" className="seller-studio-card__icon" aria-hidden="true" />
              <div>
                <h3>5. Tùy chọn & Biến thể</h3>
                <p>{type === "SERVICE" ? "Tạo các gói dịch vụ khác nhau theo nhu cầu khách" : "Nội dung có nhiều lựa chọn như kích cỡ, hương vị hoặc đóng gói"}</p>
              </div>
            </div>

            <label className="seller-check-row">
              <input type="checkbox" checked={withOptions} onChange={(event) => setWithOptions(event.target.checked)} />
              <span style={{ fontWeight: 600, color: "#0f172a" }}>{type === "SERVICE" ? "Dịch vụ có nhiều gói" : "Nội dung có lựa chọn / biến thể"}</span>
            </label>

            {withOptions ? (
              <div className="seller-studio-grid-2" style={{ marginTop: "4px" }}>
                <label>
                  <span className="seller-field-title">
                    Tên lựa chọn <span className="seller-required-star" aria-hidden="true">*</span>
                  </span>
                  <input
                    aria-label="Tên lựa chọn"
                    name="optionName"
                    placeholder={type === "SERVICE" ? "Ví dụ: Số máy" : "Ví dụ: Kích cỡ"}
                    required
                  />
                </label>
                <label>
                  <span className="seller-field-title">
                    Các giá trị <span className="seller-required-star" aria-hidden="true">*</span>
                  </span>
                  <input
                    aria-label="Các giá trị"
                    name="optionValues"
                    value={optionValuesInput}
                    onChange={(event) => setOptionValuesInput(event.target.value)}
                    placeholder={type === "SERVICE" ? "1 máy, 2 máy, 3 máy" : "S, M, L"}
                    required
                  />
                </label>
                {type !== "SERVICE" ? <p className="seller-muted" style={{ gridColumn: "1 / -1", margin: 0 }}>Mỗi giá trị tạo một biến thể và dùng chung giá cùng điểm bán trong lần tạo này.</p> : null}
              </div>
            ) : <p className="seller-muted" style={{ margin: 0 }}>Không có lựa chọn: nội dung sẽ dùng một phiên bản mặc định với giá đã đặt ở mục 4.</p>}

            {serviceVariantOffers ? (
              <div className="seller-service-variant-grid" aria-label="Giá các gói dịch vụ">
                {optionValues.map((value, index) => (
                  <section className="seller-service-variant-card" key={`${value}-${index}`}>
                    <strong>{value}</strong>
                    <label>
                      <span className="seller-field-title">
                        Giá chính xác (VND) <span className="seller-required-star" aria-hidden="true">*</span>
                      </span>
                      <input
                        aria-label="Giá chính xác (VND)"
                        name={`variantPrice:${index}`}
                        type="number"
                        inputMode="numeric"
                        min="0"
                        step="1"
                        required
                      />
                      <small>Giá của gói này tại điểm phục vụ</small>
                    </label>
                    <label className="seller-switch-row">
                      <input type="checkbox" name={`variantAvailable:${index}`} defaultChecked />
                      <span>Đang nhận đơn</span>
                    </label>
                  </section>
                ))}
                <p className="seller-muted">Mỗi gói có giá và trạng thái nhận đơn riêng tại điểm bán. Nội dung đã tạo hiện chưa hỗ trợ chỉnh cấu trúc gói.</p>
              </div>
            ) : null}
          </section>

          {/* ACTION SUBMIT BAR */}
          <div className="seller-studio-card" style={{ padding: "20px" }}>
            {progress ? <p role="status" aria-live="polite" style={{ margin: "0 0 10px", color: "#047857", fontWeight: 600 }}>{progress}</p> : null}
            {message ? <p role="alert" className="warning-banner" style={{ margin: "0 0 12px" }}>{message}</p> : null}
            <button
              type="submit"
              disabled={pending || !canSubmit || socialHydrating}
              className="seller-studio-submit-btn"
              style={{ width: "fit-content", minWidth: "200px" }}
            >
              <PlusCircle size={18} weight="bold" aria-hidden="true" />
              <span>{pending ? "Đang lưu…" : "Tạo nội dung"}</span>
            </button>
          </div>
        </form>
      </div>

      <aside className="seller-catalog-create-aside">
        {/* LIVE CARD PREVIEW */}
        {mediaAuthoringEnabled ? <CatalogSocialPreview name={previewName} caption={previewCaption} entries={gallery.entries} /> :
        <div className="seller-live-preview-box" aria-hidden="true">
          <div className="seller-live-preview-box__header">
            <Sparkle size={16} weight="fill" className="seller-live-preview-box__sparkle" />
            <span>Xem trước hiển thị với cư dân</span>
          </div>
          <div className="seller-product-preview-card">
            <div className="seller-product-preview-card__image-box">
              {previewUrls[0] ? (
                <img
                  src={previewUrls[0]}
                  alt="Xem trước ảnh sản phẩm"
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                />
              ) : type === "SERVICE" ? (
                <Wrench size={44} weight="duotone" />
              ) : (
                <Package size={44} weight="duotone" />
              )}
              <span className="seller-product-preview-card__badge">
                {currentCategoryName || (type === "SERVICE" ? "Dịch vụ cư dân" : "Món ăn & Hàng hóa")}
              </span>
            </div>
            <div className="seller-product-preview-card__content">
              <strong className="seller-product-preview-card__title">
                {previewName.trim() || "Tên món hoặc sản phẩm"}
              </strong>
              <div className="seller-product-preview-card__price-row">
                <span className="seller-product-preview-card__price">
                  {previewPrice ? `${previewPrice.toLocaleString("vi-VN")} đ` : "Chưa đặt giá"}
                </span>
                {previewPrepTime > 0 ? (
                  <span className="seller-product-preview-card__prep">
                    <Clock size={12} weight="bold" />
                    <span>{previewPrepTime} phút</span>
                  </span>
                ) : null}
              </div>
              <div className="seller-product-preview-card__footer">
                <span className="seller-product-preview-card__hub">Giao nhận sảnh toà nhà</span>
              </div>
            </div>
          </div>
        </div>

        }
        {/* TIPS CHECKLIST */}
        <div className="seller-create-tips-card" aria-hidden="true">
          <div className="seller-create-tips-card__header">
            <CheckCircle size={18} weight="duotone" className="seller-create-tips-card__icon" />
            <strong>Gợi ý niêm yết chuẩn CoVie</strong>
          </div>
          <ul className="seller-create-tips-list">
            <li>
              <strong>Tên món kèm định lượng: </strong>
              <span>Ví dụ: &quot;Bánh mì chảo - Phần đặc biệt&quot; hoặc &quot;Cà phê muối 500ml&quot; giúp cư dân dễ chọn mua.</span>
            </li>
            <li>
              <strong>Thời gian chuẩn bị chính xác: </strong>
              <span>Giúp hàng xóm chủ động canh giờ xuống sảnh thang máy nhận món nóng hổi.</span>
            </li>
            <li>
              <strong>Ghim đúng điểm bán: </strong>
              <span>Cư dân trong cùng toà nhà sẽ được hiển thị ưu tiên hàng đầu và freeship.</span>
            </li>
          </ul>
        </div>
      </aside>
    </div>
  );
}
