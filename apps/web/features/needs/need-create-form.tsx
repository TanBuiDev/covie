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
import styles from "./buyer-needs-mobile.module.css";

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
    <form className={`need-form ${styles.createForm}`} onSubmit={submit}>
      <div className={styles.typeSegments} role="group" aria-label="Loại nhu cầu">
        <button className={`${styles.typeSegment} ${type === "PRODUCT" ? styles.typeSegmentActive : ""}`} type="button" aria-pressed={type === "PRODUCT"} onClick={() => setType("PRODUCT")}>Sản phẩm</button>
        <button className={`${styles.typeSegment} ${type === "SERVICE" ? styles.typeSegmentActive : ""}`} type="button" aria-pressed={type === "SERVICE"} onClick={() => setType("SERVICE")}>Dịch vụ</button>
      </div>

      <div className={`need-form__grid ${styles.createFormGrid}`}>
        <label className={styles.desktopToolbar}>
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
          <small className={styles.help}>Chọn danh mục phù hợp</small>
        </label>
      </div>

      <label>
        <span>Tên / nhu cầu</span>
        <input required maxLength={220} value={title} onChange={(event) => setTitle(event.target.value)} placeholder="Bạn đang cần tìm gì?" />
        <small className={styles.help}>Mô tả ngắn thứ bạn đang tìm</small>
      </label>
      <label>
        <span>Từ khóa tìm kiếm</span>
        <input required maxLength={500} value={queryText} onChange={(event) => setQueryText(event.target.value)} placeholder="Ví dụ: rau hữu cơ, sửa máy lạnh..." />
        <small className={styles.help}>Dùng để tìm catalog phù hợp</small>
      </label>

      <div className={`need-form__grid ${styles.createFormGrid}`}>
        {type === "PRODUCT" ? (
          <label>
            <span>Số lượng</span>
            <input type="number" min={1} value={quantity} onChange={(event) => setQuantity(event.target.value)} />
            <small className={styles.help}>Số lượng bạn muốn tìm</small>
          </label>
        ) : null}
        <label>
          <span>Phạm vi tìm</span>
          <select value={radiusMeters} onChange={(event) => setRadiusMeters(event.target.value)}>
            <option value="1000">1 km</option>
            <option value="3000">3 km</option>
            <option value="5000">5 km</option>
            <option value="10000">10 km</option>
          </select>
          <small className={styles.help}>Khoảng cách tối đa từ vị trí đã chọn</small>
        </label>
      </div>

      <label>
        <span>Vị trí</span>
        <select required value={addressId} onChange={(event) => setAddressId(event.target.value)}>
          <option value="" disabled>Chọn địa chỉ đã lưu</option>
          {addresses.map((address) => address.id ? (
            <option key={address.id} value={address.id}>{address.label ? `${address.label} · ` : ""}{address.addressText ?? "Địa chỉ đã lưu"}</option>
          ) : null)}
        </select>
        <small className={styles.help}>Địa chỉ đã lưu của bạn</small>
      </label>
      {!addresses.length ? <p className={`need-form__help ${styles.help}`}>Bạn cần một địa chỉ đã lưu. <Link href="/me/addresses">Quản lý địa chỉ</Link></p> : null}

      <label>
        <span>Thời hạn</span>
        <input required type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
        <small className={styles.help}>Thời điểm hết hạn được gửi nguyên vẹn theo contract hiện tại.</small>
      </label>

      <label>
        <span>Mô tả thêm</span>
        <textarea maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Mô tả thêm để người bán hiểu đúng nhu cầu" />
        <small className={styles.help}>Không bắt buộc</small>
      </label>

      {error ? <p className="form-alert" role="alert">{error}</p> : null}
      <div className={`need-form__actions ${styles.formActions}`}>
        <button className="button-primary" disabled={submitting || !categoryId || !addressId} type="submit">
          {submitting ? "Đang đăng…" : "Đăng Cần mua"}
        </button>
        <Link className="button-link" href="/needs">Hủy</Link>
      </div>
    </form>
  );
}
