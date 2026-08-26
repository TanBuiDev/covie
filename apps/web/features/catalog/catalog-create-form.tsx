"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { uploadImage } from "@/features/media/direct-image-upload";
import { CovieApiError } from "@/lib/api/api-error";
import { createCatalogItem } from "./catalog-api";

type Category = components["schemas"]["CategorySummary"];
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
  const idempotencyKeyRef = useRef<string>();
  const [type, setType] = useState<CatalogType>("PRODUCT");
  const [listingKind, setListingKind] = useState<ListingKind>("STANDARD");
  const [inventoryMode, setInventoryMode] = useState<InventoryMode>("UNTRACKED");
  const [withOptions, setWithOptions] = useState(false);
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

      const price = integerField(form, "price");
      const preparationTimeMinutes = integerField(form, "preparationTimeMinutes");
      const optionName = String(form.get("optionName") ?? "").trim();
      const optionValues = [...new Set(String(form.get("optionValues") ?? "")
        .split(",")
        .map((value) => value.trim())
        .filter(Boolean))];
      const hasOptions = withOptions && optionName.length > 0 && optionValues.length > 0;
      if (withOptions && !hasOptions) throw new Error("OPTIONS_REQUIRED");

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
        attributes: [],
        options: hasOptions ? [{ clientKey: optionGroupKey, name: optionName, values: optionRows }] : [],
        variants,
        locationOffers: variantKeys.map((variantClientKey) => ({
          merchantLocationId: locationId,
          variantClientKey,
          price,
          currency: "VND",
          available: true,
          inventoryMode: type === "PRODUCT" ? inventoryMode : undefined,
          preparationTimeMinutes,
        })),
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
      } else if (error instanceof Error && ["CATEGORY_REQUIRED", "LOCATION_REQUIRED", "OPTIONS_REQUIRED", "INVALID_INTEGER"].includes(error.message)) {
        setMessage("Vui lòng kiểm tra category, điểm bán, biến thể và các trường số.");
      } else {
        setMessage(error instanceof Error ? error.message : "Không thể tạo nội dung. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  const canSubmit = compatibleCategories.length > 0 && compatibleLocations.length > 0;

  return (
    <form className="seller-catalog-form" onSubmit={submit} onChange={invalidateIntent}>
      <header>
        <p className="eyebrow">Catalog / Tạo mới</p>
        <h1>Tạo nội dung bán</h1>
        <p className="seller-muted">Sản phẩm, dịch vụ và hàng bán lại dùng cùng contract catalog; category và điểm bán được lấy trực tiếp từ backend.</p>
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
              <option value="RESALE">Sản phẩm bán lại</option>
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
        <select name="categoryId" required defaultValue="">
          <option value="" disabled>Chọn danh mục</option>
          {compatibleCategories.map((category) => category.id ? <option key={category.id} value={category.id}>{category.name ?? "Danh mục"}</option> : null)}
        </select>
      </label>
      {!compatibleCategories.length ? <p className="warning-banner" role="status">Chưa có danh mục backend phù hợp với loại nội dung này.</p> : null}
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
        <label>
          Giá (VND)
          <input name="price" type="number" inputMode="numeric" min="0" step="1" required />
        </label>
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
        <legend>Biến thể</legend>
        <label>
          <input type="checkbox" checked={withOptions} onChange={(event) => setWithOptions(event.target.checked)} />
          Nội dung có lựa chọn / biến thể
        </label>
        {withOptions ? (
          <div className="seller-grid">
            <label>
              Tên lựa chọn
              <input name="optionName" placeholder="Ví dụ: Kích cỡ" required />
            </label>
            <label>
              Các giá trị
              <input name="optionValues" placeholder="S, M, L" required />
            </label>
            <p className="seller-muted">Mỗi giá trị tạo một variant backend và cùng dùng giá/điểm bán của lần tạo này.</p>
          </div>
        ) : <p className="seller-muted">Không có lựa chọn: backend tạo default variant canonical, frontend không tự tạo SKU giả.</p>}
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
