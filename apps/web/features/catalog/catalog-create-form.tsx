"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import { createCatalogItem, getCategoryDetail } from "./catalog-api";

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
        const requiredMark = definition.required ? <span aria-hidden="true"> *</span> : null;

        if (definition.dataType === "SINGLE_SELECT") {
          return (
            <label key={attributeKey}>
              {label}{requiredMark}
              <select name={name} required={definition.required} defaultValue="">
                <option value="">{definition.required ? "Chọn một giá trị" : "Không chọn"}</option>
                {(definition.options ?? []).map((option) => option.id ? (
                  <option key={option.id} value={option.id}>{option.value ?? "Giá trị"}</option>
                ) : null)}
              </select>
            </label>
          );
        }

        if (definition.dataType === "MULTI_SELECT") {
          return (
            <fieldset className="seller-attribute-options" key={attributeKey}>
              <legend>{label}{requiredMark}</legend>
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
            <label key={attributeKey}>
              {label}{requiredMark}
              <select name={name} required={definition.required} defaultValue="">
                <option value="">{definition.required ? "Chọn một giá trị" : "Không chọn"}</option>
                <option value="true">Có</option>
                <option value="false">Không</option>
              </select>
            </label>
          );
        }

        return (
          <label key={attributeKey}>
            {label}{requiredMark}
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
}: {
  merchantId: string;
  categories: Category[];
  locations: Location[];
}) {
  const router = useRouter();
  const idempotencyKeyRef = useRef<string | undefined>(undefined);
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
    if (!selectedCategoryId) {
      setCategoryDetail(undefined);
      setCategoryError(undefined);
      setCategoryLoading(false);
      return;
    }

    let active = true;
    setCategoryLoading(true);
    setCategoryError(undefined);
    setCategoryDetail(undefined);
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

      const mediaIds: string[] = [];
      for (const [index, file] of files.entries()) {
        setProgress(`Đang tải ảnh ${index + 1}/${files.length}…`);
        const uploaded = await uploadImage(file, "CATALOG_IMAGE");
        if (!uploaded.mediaId) throw new Error("MEDIA_UPLOAD_CONTRACT_INVALID");
        mediaIds.push(uploaded.mediaId);
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

  const canSubmit = compatibleCategories.length > 0
    && compatibleLocations.length > 0
    && Boolean(selectedCategoryId)
    && Boolean(categoryDetail)
    && !categoryLoading
    && !categoryError;

  return (
    <form className="seller-catalog-form" onSubmit={submit} onChange={invalidateIntent} aria-busy={pending}>
      <header>
        <p className="eyebrow">Catalog / Tạo mới</p>
        <h1>Tạo nội dung bán</h1>
        <p className="seller-muted">Sản phẩm, dịch vụ và hàng bán lại dùng cùng contract catalog; danh mục, thuộc tính và điểm bán được lấy trực tiếp từ backend.</p>
      </header>

      <fieldset disabled={pending}>
        <legend>Loại nội dung</legend>
        <label>
          Loại
          <select
            name="type"
            value={type}
            onChange={(event) => {
              const next = event.target.value as CatalogType;
              setType(next);
              setSelectedCategoryId("");
              setCategoryDetail(undefined);
              if (next === "SERVICE") setListingKind("STANDARD");
            }}
          >
            <option value="PRODUCT">Sản phẩm</option>
            <option value="SERVICE">Dịch vụ</option>
          </select>
        </label>
        {type === "PRODUCT" ? (
          <label>
            Kiểu listing
            <select name="listingKind" value={listingKind} onChange={(event) => setListingKind(event.target.value as ListingKind)}>
              <option value="STANDARD">Sản phẩm tiêu chuẩn</option>
              <option value="RESALE">Thanh lý</option>
            </select>
          </label>
        ) : null}
      </fieldset>

      <label>
        Tên
        <input name="name" autoComplete="off" maxLength={240} required />
      </label>
      <label>
        Danh mục
        <select
          name="categoryId"
          required
          value={selectedCategoryId}
          onChange={(event) => setSelectedCategoryId(event.target.value)}
        >
          <option value="" disabled>Chọn danh mục</option>
          {compatibleCategories.map((category) => category.id ? <option key={category.id} value={category.id}>{category.name ?? "Danh mục"}</option> : null)}
        </select>
      </label>
      {!compatibleCategories.length ? <p className="warning-banner" role="status">Chưa có danh mục backend phù hợp với loại nội dung này.</p> : null}
      {categoryLoading ? <p className="seller-muted" role="status">Đang tải thuộc tính danh mục…</p> : null}
      {categoryError ? <p className="seller-contract-note" role="alert">{categoryError}</p> : null}
      {categoryDetail ? <CategoryAttributeFields definitions={categoryDetail.attributes ?? []} pending={pending} /> : null}
      {listingKind === "RESALE" && categoryDetail && !(categoryDetail.attributes ?? []).length ? (
        <p className="seller-contract-note" role="status">Danh mục này chưa cung cấp thuộc tính tình trạng qua API. CoVie không tự tạo danh sách tình trạng thanh lý ở frontend.</p>
      ) : null}

      <label>
        Mô tả
        <textarea name="description" maxLength={4000} />
      </label>
      <label>
        Thẻ
        <input name="tags" placeholder="ví dụ: handmade, giao nhanh" />
      </label>

      <fieldset disabled={pending}>
        <legend>Điểm bán & giá</legend>
        <label>
          Điểm bán
          <select name="locationId" required defaultValue="">
            <option value="" disabled>Chọn điểm bán</option>
            {compatibleLocations.map((location) => location.id ? (
              <option key={location.id} value={location.id}>{location.name ?? "Điểm bán"}{location.addressText ? ` — ${location.addressText}` : ""}</option>
            ) : null)}
          </select>
        </label>
        {!compatibleLocations.length ? <p className="warning-banner" role="status">Chưa có điểm bán backend phù hợp. Dịch vụ chỉ dùng location có serviceEnabled.</p> : null}
        {!serviceVariantOffers ? (
          <label>
            Giá chính xác (VND)
            <input name="price" type="number" inputMode="numeric" min="0" step="1" required />
          </label>
        ) : <p className="seller-muted">Mỗi gói dịch vụ có giá và trạng thái nhận đơn riêng bên dưới.</p>}
        <label>
          Thời gian chuẩn bị (phút)
          <input name="preparationTimeMinutes" type="number" inputMode="numeric" min="0" step="1" defaultValue="0" required />
        </label>
        {type === "PRODUCT" ? (
          <label>
            Quản lý tồn kho
            <select name="inventoryMode" value={inventoryMode} onChange={(event) => setInventoryMode(event.target.value as InventoryMode)}>
              <option value="UNTRACKED">Không theo dõi số lượng</option>
              <option value="TRACKED">Theo dõi tồn kho</option>
            </select>
          </label>
        ) : null}
      </fieldset>

      <fieldset disabled={pending}>
        <legend>{type === "SERVICE" ? "Gói dịch vụ" : "Biến thể"}</legend>
        <label className="seller-check-row">
          <input type="checkbox" checked={withOptions} onChange={(event) => setWithOptions(event.target.checked)} />
          <span>{type === "SERVICE" ? "Dịch vụ có nhiều gói" : "Nội dung có lựa chọn / biến thể"}</span>
        </label>
        {withOptions ? (
          <div className="seller-grid">
            <label>
              Tên lựa chọn
              <input name="optionName" placeholder={type === "SERVICE" ? "Ví dụ: Số máy" : "Ví dụ: Kích cỡ"} required />
            </label>
            <label>
              Các giá trị
              <input
                name="optionValues"
                value={optionValuesInput}
                onChange={(event) => setOptionValuesInput(event.target.value)}
                placeholder={type === "SERVICE" ? "1 máy, 2 máy, 3 máy" : "S, M, L"}
                required
              />
            </label>
            {type !== "SERVICE" ? <p className="seller-muted">Mỗi giá trị tạo một variant backend và cùng dùng giá/điểm bán của lần tạo này.</p> : null}
          </div>
        ) : <p className="seller-muted">Không có lựa chọn: backend tạo default variant canonical, frontend không tự tạo SKU giả.</p>}

        {serviceVariantOffers ? (
          <div className="seller-service-variant-grid" aria-label="Giá các gói dịch vụ">
            {optionValues.map((value, index) => (
              <section className="seller-service-variant-card" key={`${value}-${index}`}>
                <strong>{value}</strong>
                <label>
                  Giá chính xác (VND) <span aria-hidden="true">*</span>
                  <input name={`variantPrice:${index}`} type="number" inputMode="numeric" min="0" step="1" required />
                  <small>Giá của gói này tại điểm phục vụ</small>
                </label>
                <label className="seller-switch-row">
                  <input type="checkbox" name={`variantAvailable:${index}`} defaultChecked />
                  <span>Đang nhận đơn</span>
                </label>
              </section>
            ))}
            <p className="seller-muted">Mỗi gói tạo một location offer riêng. Muốn đổi tổ hợp lựa chọn sau này cần contract edit/version authoritative; frontend không tự sửa cấu trúc đã publish.</p>
          </div>
        ) : null}
      </fieldset>

      <fieldset disabled={pending}>
        <legend>Ảnh</legend>
        <label>
          Ảnh catalog
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            onChange={(event) => {
              setFiles(Array.from(event.target.files ?? []));
              idempotencyKeyRef.current = undefined;
            }}
          />
        </label>
        <p className="seller-muted">JPEG, PNG hoặc WebP; tối đa 10 MB mỗi ảnh. File được upload trực tiếp qua presigned storage contract.</p>
        {files.length ? <p role="status">Đã chọn {files.length} ảnh.</p> : null}
      </fieldset>

      {progress ? <p role="status" aria-live="polite">{progress}</p> : null}
      {message ? <p role="alert">{message}</p> : null}
      <button type="submit" disabled={pending || !canSubmit}>{pending ? "Đang lưu…" : "Tạo nội dung"}</button>
    </form>
  );
}
