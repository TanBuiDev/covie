"use client";

import Link from "next/link";
import { FormEvent, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import {
  createNeed,
  listNeedAddresses,
  listNeedCategories,
  type NeedAddress,
  type NeedCategory,
  type NeedType,
} from "./need-api";

export function NeedCreateForm() {
  const router = useRouter();
  const [type, setType] = useState<NeedType>("PRODUCT");
  const [categories, setCategories] = useState<NeedCategory[]>([]);
  const [addresses, setAddresses] = useState<NeedAddress[]>([]);
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

  useEffect(() => {
    let active = true;
    Promise.all([listNeedCategories(type), listNeedAddresses()])
      .then(([nextCategories, nextAddresses]) => {
        if (!active) return;
        setCategories(nextCategories);
        setAddresses(nextAddresses);
        setCategoryId((current) => nextCategories.some((item) => item.id === current) ? current : (nextCategories[0]?.id ?? ""));
        setAddressId((current) => current || nextAddresses.find((item) => item.defaultAddress)?.id || nextAddresses[0]?.id || "");
      })
      .catch((cause) => {
        if (active) setError(cause instanceof Error ? cause.message : "Không thể tải dữ liệu cho biểu mẫu.");
      });
    return () => { active = false; };
  }, [type]);

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
      if (!created.id) throw new Error("Không nhận được mã nhu cầu từ hệ thống.");
      router.push(`/needs/${created.id}`);
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể tạo nhu cầu.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form className="need-form" onSubmit={submit}>
      <div className="need-form__grid">
        <label>
          <span>Loại nhu cầu</span>
          <select value={type} onChange={(event) => setType(event.target.value as NeedType)}>
            <option value="PRODUCT">Sản phẩm</option>
            <option value="SERVICE">Dịch vụ</option>
          </select>
        </label>
        <label>
          <span>Danh mục</span>
          <select required value={categoryId} onChange={(event) => setCategoryId(event.target.value)}>
            <option value="" disabled>Chọn danh mục</option>
            {categories.map((category) => category.id ? <option key={category.id} value={category.id}>{category.name ?? "Danh mục"}</option> : null)}
          </select>
        </label>
      </div>

      <label>
        <span>Tiêu đề</span>
        <input required maxLength={220} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Bạn đang cần tìm gì?" />
      </label>
      <label>
        <span>Từ khóa tìm kiếm</span>
        <input required maxLength={500} value={queryText} onChange={(event) => setQueryText(event.target.value)} placeholder="Ví dụ: rau hữu cơ, sửa máy lạnh..." />
      </label>
      <label>
        <span>Mô tả</span>
        <textarea maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Mô tả thêm để người bán hiểu đúng nhu cầu" />
      </label>

      <div className="need-form__grid">
        {type === "PRODUCT" ? (
          <label>
            <span>Số lượng</span>
            <input type="number" min={1} value={quantity} onChange={(event) => setQuantity(event.target.value)} />
          </label>
        ) : null}
        <label>
          <span>Bán kính tìm kiếm</span>
          <select value={radiusMeters} onChange={(event) => setRadiusMeters(event.target.value)}>
            <option value="1000">1 km</option>
            <option value="3000">3 km</option>
            <option value="5000">5 km</option>
            <option value="10000">10 km</option>
          </select>
        </label>
      </div>

      <label>
        <span>Khu vực nhận hàng / dịch vụ</span>
        <select required value={addressId} onChange={(event) => setAddressId(event.target.value)}>
          <option value="" disabled>Chọn địa chỉ đã lưu</option>
          {addresses.map((address) => address.id ? (
            <option key={address.id} value={address.id}>{address.label ? `${address.label} · ` : ""}{address.addressText ?? "Địa chỉ đã lưu"}</option>
          ) : null)}
        </select>
      </label>
      {!addresses.length ? <p className="need-form__help">Bạn cần một địa chỉ đã lưu. <Link href="/me/addresses">Quản lý địa chỉ</Link></p> : null}

      <label>
        <span>Hết hạn lúc</span>
        <input required type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
      </label>

      {error ? <p className="form-alert" role="alert">{error}</p> : null}
      <div className="need-form__actions">
        <button className="button-primary" disabled={submitting || !categoryId || !addressId} type="submit">
          {submitting ? "Đang đăng…" : "Đăng nhu cầu"}
        </button>
        <Link className="button-link" href="/needs">Hủy</Link>
      </div>
    </form>
  );
}
