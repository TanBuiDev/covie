"use client";

import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { createNeed, type NeedType } from "./need-api";

export function NeedCreateForm() {
  const router = useRouter();
  const [type, setType] = useState<NeedType>("PRODUCT");
  const [categoryId, setCategoryId] = useState("");
  const [title, setTitle] = useState("");
  const [queryText, setQueryText] = useState("");
  const [description, setDescription] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [addressId, setAddressId] = useState("");
  const [radiusMeters, setRadiusMeters] = useState("5000");
  const [expiresAt, setExpiresAt] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const created = await createNeed({
        type,
        categoryId,
        title,
        queryText,
        description: description || undefined,
        requestedQuantity: type === "PRODUCT" ? Number(quantity) : 1,
        addressId: addressId || undefined,
        radiusMeters: Number(radiusMeters),
        expiresAt: new Date(expiresAt).toISOString(),
      });
      router.push(`/needs/${created.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tạo nhu cầu.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={submit} style={{ display: "grid", gap: 12, maxWidth: 720 }}>
      <label>
        Loại nhu cầu
        <select value={type} onChange={(event) => setType(event.target.value as NeedType)}>
          <option value="PRODUCT">Sản phẩm</option>
          <option value="SERVICE">Dịch vụ</option>
        </select>
      </label>
      <label>
        Category ID
        <input required value={categoryId} onChange={(event) => setCategoryId(event.target.value)} />
      </label>
      <label>
        Tiêu đề
        <input required maxLength={220} value={title} onChange={(event) => setTitle(event.target.value)} />
      </label>
      <label>
        Từ khóa tìm kiếm
        <input required maxLength={500} value={queryText} onChange={(event) => setQueryText(event.target.value)} />
      </label>
      <label>
        Mô tả
        <textarea maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} />
      </label>
      {type === "PRODUCT" && (
        <label>
          Số lượng
          <input type="number" min={1} value={quantity} onChange={(event) => setQuantity(event.target.value)} />
        </label>
      )}
      <label>
        Saved address ID
        <input required value={addressId} onChange={(event) => setAddressId(event.target.value)} />
      </label>
      <label>
        Bán kính (m)
        <input type="number" min={1} value={radiusMeters} onChange={(event) => setRadiusMeters(event.target.value)} />
      </label>
      <label>
        Hết hạn lúc
        <input required type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>
      {error && <p role="alert">{error}</p>}
      <button disabled={submitting} type="submit">{submitting ? "Đang tạo…" : "Đăng nhu cầu"}</button>
    </form>
  );
}
