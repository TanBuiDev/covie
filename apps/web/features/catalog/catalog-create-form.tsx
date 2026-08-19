"use client";

import { FormEvent, useState } from "react";
import { createCatalogItem } from "./catalog-api";

function clientKey(value: string, fallback: string) {
  return value.trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/(^-|-$)/g, "") || fallback;
}

export function CatalogCreateForm({ merchantId, onCreated }: { merchantId: string; onCreated?: () => void }) {
  const [message, setMessage] = useState<string>();
  const [withOptions, setWithOptions] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const type = String(form.get("type")) as "PRODUCT" | "SERVICE";
    const optionName = String(form.get("optionName") ?? "").trim();
    const optionValues = String(form.get("optionValues") ?? "").split(",").map((value) => value.trim()).filter(Boolean);
    const variantName = String(form.get("variantName") ?? "").trim();
    const hasOptions = withOptions && optionName && optionValues.length > 0 && variantName;
    const optionKey = clientKey(optionName, "option");
    const valueKey = clientKey(optionValues[0] ?? "value", "value");
    const variantKey = hasOptions ? clientKey(variantName, "variant") : "__default__";
    try {
      await createCatalogItem(merchantId, crypto.randomUUID(), {
        type, listingKind: String(form.get("listingKind")) as "STANDARD" | "RESALE", categoryId: String(form.get("categoryId")),
        name: String(form.get("name")).trim(), description: String(form.get("description") ?? "").trim() || undefined,
        tags: String(form.get("tags") ?? "").split(",").map((value) => value.trim()).filter(Boolean), mediaIds: [], attributes: [],
        options: hasOptions ? [{ clientKey: optionKey, name: optionName, values: optionValues.map((value, index) => ({ clientKey: index === 0 ? valueKey : `${valueKey}-${index}`, value })) }] : [],
        variants: hasOptions ? [{ clientKey: variantKey, name: variantName, optionValueKeys: [valueKey] }] : [],
        locationOffers: [{ merchantLocationId: String(form.get("locationId")), variantClientKey: variantKey,
          price: Number(form.get("price")), currency: "VND", available: true, inventoryMode: type === "PRODUCT" ? "UNTRACKED" : undefined }],
      });
      (onCreated ?? (() => window.location.assign(`/seller/${merchantId}/catalog`)))();
    } catch {
      setMessage("Không thể tạo catalog. Vui lòng kiểm tra dữ liệu và thử lại.");
    }
  }
  return <form onSubmit={submit}>
    <h1>Tạo sản phẩm hoặc dịch vụ</h1>
    <label>Tên sản phẩm hoặc dịch vụ <input name="name" required /></label>
    <label>Category ID <input name="categoryId" required /></label>
    <label>Location ID <input name="locationId" required /></label>
    <label>Loại <select name="type" defaultValue="PRODUCT"><option value="PRODUCT">Sản phẩm</option><option value="SERVICE">Dịch vụ</option></select></label>
    <label>Kiểu listing <select name="listingKind" defaultValue="STANDARD"><option value="STANDARD">Tiêu chuẩn</option><option value="RESALE">Bán lại</option></select></label>
    <label>Giá (VND) <input name="price" type="number" min="0" required /></label>
    <label>Mô tả <textarea name="description" /></label>
    <label>Thẻ, phân cách bằng dấu phẩy <input name="tags" /></label>
    <label><input type="checkbox" checked={withOptions} onChange={(event) => setWithOptions(event.target.checked)} /> Tạo một biến thể có lựa chọn</label>
    {withOptions && <fieldset><legend>Biến thể</legend>
      <label>Tên lựa chọn <input name="optionName" /></label>
      <label>Giá trị lựa chọn, phân cách bằng dấu phẩy <input name="optionValues" /></label>
      <label>Tên biến thể <input name="variantName" /></label>
    </fieldset>}
    {message && <p role="alert">{message}</p>}
    <button type="submit">Tạo catalog</button>
  </form>;
}
