"use client";

import Link from "next/link";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { CovieApiError } from "@/lib/api/api-error";
import { SelectDropdown, type DropdownOption } from "@/components/ui/select-dropdown";
import {
  createNeed,
  listNeedAddresses,
  listNeedCategories,
  type NeedAddress,
  type NeedCategory,
  type NeedType,
} from "./need-api";
import { Clock, Eye, Lightbulb, MapPin, Package, Tag, Wrench } from "@phosphor-icons/react";
import styles from "./buyer-needs-mobile.module.css";

const RADIUS_OPTIONS: DropdownOption[] = [
  { value: "1000", label: "1 km · Cùng tòa & đi bộ" },
  { value: "3000", label: "3 km · Nội khu dân cư" },
  { value: "5000", label: "5 km · Lân cận khu vực (Phổ biến)" },
  { value: "10000", label: "10 km · Toàn khu vực mở rộng" },
];

function getInitialQueryText(): string {
  if (typeof window !== "undefined" && window.location?.search) {
    const urlParams = new URLSearchParams(window.location.search);
    return urlParams.get("title") || urlParams.get("q") || "";
  }
  return "";
}

export function NeedCreateForm() {
  const router = useRouter();
  const [type, setType] = useState<NeedType>("PRODUCT");
  const [categories, setCategories] = useState<NeedCategory[]>([]);
  const [addresses, setAddresses] = useState<NeedAddress[]>([]);
  const [categoryId, setCategoryId] = useState("");
  const [title, setTitle] = useState(getInitialQueryText);
  const [queryText, setQueryText] = useState(getInitialQueryText);
  const [description, setDescription] = useState("");
  const [quantity, setQuantity] = useState("1");
  const [addressId, setAddressId] = useState("");
  const [radiusMeters, setRadiusMeters] = useState("5000");
  const [expiresAt, setExpiresAt] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submitPending = useRef(false);
  const [readyType, setReadyType] = useState<NeedType>();
  const [loadError, setLoadError] = useState<string>();
  const [isAuthError, setIsAuthError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const ready = readyType === type && !loadError;

  const categoryOptions = useMemo<DropdownOption[]>(() => {
    return categories
      .filter((category) => Boolean(category.id))
      .map((category) => ({
        value: category.id!,
        label: category.name ?? "Danh mục",
      }));
  }, [categories]);

  const addressOptions = useMemo<DropdownOption[]>(() => {
    return addresses
      .filter((address) => Boolean(address.id))
      .map((address) => ({
        value: address.id!,
        label: `${address.label ? `${address.label} · ` : ""}${address.addressText ?? "Địa chỉ đã lưu"}`,
      }));
  }, [addresses]);

  function changeType(nextType: NeedType) {
    if (nextType === type || submitting) return;
    setReadyType(undefined);
    setLoadError(undefined);
    setIsAuthError(false);
    setType(nextType);
  }

  useEffect(() => {
    let active = true;
    Promise.all([listNeedCategories(type), listNeedAddresses()])
      .then(([nextCategories, nextAddresses]) => {
        if (!active) return;
        setCategories(nextCategories);
        setAddresses(nextAddresses);
        setCategoryId((current) => nextCategories.some((item) => item.id === current) ? current : (nextCategories[0]?.id ?? ""));
        setAddressId((current) => nextAddresses.some(item => item.id === current) ? current : nextAddresses.find((item) => item.defaultAddress)?.id || nextAddresses[0]?.id || "");
        setReadyType(type);
        setLoadError(undefined);
        setIsAuthError(false);
      })
      .catch((cause) => {
        if (active) {
          setReadyType(undefined);
          setIsAuthError(cause instanceof CovieApiError && cause.status === 401);
          setLoadError("Không thể tải danh mục hoặc địa chỉ. Vui lòng thử lại.");
        }
      });
    return () => { active = false; };
  }, [type, reloadKey]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!ready || submitPending.current || !categories.some(item => item.id === categoryId) || !addresses.some(item => item.id === addressId)) return;
    setError(null);
    const requestedQuantity = type === "PRODUCT" ? Number(quantity) : 1;
    if (!Number.isSafeInteger(requestedQuantity) || requestedQuantity <= 0) {
      setError("Số lượng phải là số nguyên dương trong phạm vi hỗ trợ.");
      return;
    }
    const expiry = new Date(expiresAt);
    if (!Number.isFinite(expiry.getTime()) || expiry.getTime() <= Date.now()) {
      setError("Thời hạn phải là thời điểm hợp lệ trong tương lai.");
      return;
    }
    submitPending.current = true;
    setSubmitting(true);
    try {
      const created = await createNeed({
        type,
        categoryId,
        title,
        queryText,
        description: description || undefined,
        requestedQuantity,
        addressId: addressId || undefined,
        radiusMeters: Number(radiusMeters),
        expiresAt: expiry.toISOString(),
      });
      if (!created.id) throw new Error("Không nhận được mã nhu cầu từ hệ thống.");
      router.push(`/needs/${created.id}`);
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) {
        setIsAuthError(true);
        setLoadError("Vui lòng đăng nhập lại để tiếp tục.");
      } else {
        setError(cause instanceof CovieApiError && cause.code === "NEED_EXPIRY_INVALID"
          ? "Thời hạn phải là thời điểm hợp lệ trong tương lai."
          : cause instanceof CovieApiError && cause.code === "VALIDATION_FAILED"
            ? "Thông tin chưa hợp lệ. Vui lòng kiểm tra biểu mẫu."
            : "Không thể tạo nhu cầu. Vui lòng thử lại.");
      }
    } finally {
      submitPending.current = false;
      setSubmitting(false);
    }
  }

  function handleTitleChange(nextTitle: string) {
    setTitle(nextTitle);
    if (!queryText || queryText === title) {
      setQueryText(nextTitle);
    }
  }

  function applyTemplate(templateTitle: string, templateQuery: string) {
    setTitle(templateTitle);
    setQueryText(templateQuery);
  }

  function setQuickExpiry(hours: number) {
    const target = new Date(Date.now() + hours * 3600 * 1000);
    const year = target.getFullYear();
    const month = String(target.getMonth() + 1).padStart(2, "0");
    const day = String(target.getDate()).padStart(2, "0");
    const h = String(target.getHours()).padStart(2, "0");
    const m = String(target.getMinutes()).padStart(2, "0");
    setExpiresAt(`${year}-${month}-${day}T${h}:${m}`);
  }

  function setTodayMidnightExpiry() {
    const target = new Date();
    target.setHours(23, 59, 0, 0);
    const year = target.getFullYear();
    const month = String(target.getMonth() + 1).padStart(2, "0");
    const day = String(target.getDate()).padStart(2, "0");
    setExpiresAt(`${year}-${month}-${day}T23:59`);
  }

  const selectedAddress = addresses.find((a) => a.id === addressId);
  const selectedCategory = categories.find((c) => c.id === categoryId);

  const productTemplates = [
    { label: "Rau củ tươi sạch", title: "Cần mua rau củ tươi sạch", query: "rau củ tươi" },
    { label: "Đồ ăn trưa", title: "Tìm đồ ăn trưa giao tận phòng", query: "cơm trưa đồ ăn" },
    { label: "Thang nhôm gấp", title: "Cần mượn thang nhôm gấp", query: "thang nhôm" },
    { label: "Đồ dùng gia đình", title: "Cần mua đồ dùng gia đình gấp", query: "đồ dùng gia đình" },
  ];

  const serviceTemplates = [
    { label: "Sửa điện nước", title: "Cần thợ sửa chữa điện nước tại nhà", query: "sửa điện nước" },
    { label: "Vệ sinh máy lạnh", title: "Cần thợ vệ sinh bảo dưỡng máy lạnh", query: "vệ sinh máy lạnh" },
    { label: "Dọn dẹp theo giờ", title: "Tìm người dọn dẹp nhà theo giờ", query: "dọn dẹp nhà" },
    { label: "Chuyển đồ nhanh", title: "Cần hỗ trợ chuyển đồ nội khu", query: "chuyển đồ" },
  ];

  const radiusPresets = [
    { value: "1000", label: "Cùng tòa", sub: "< 1 km" },
    { value: "3000", label: "Nội khu", sub: "< 3 km" },
    { value: "5000", label: "Lân cận", sub: "< 5 km" },
    { value: "10000", label: "Toàn khu", sub: "< 10 km" },
  ];

  return (
    <div className="need-create-layout">
      <div className="need-create-form-column">
        <form className={`need-form ${styles.createForm}`} onSubmit={submit}>
          <div className={`need-form__grid ${styles.createFormGrid}`}>
            <div className="need-form__type-field">
              <span className="need-form__field-label">Loại nhu cầu</span>
              <div className={`need-type-segments ${styles.typeSegments}`} role="group" aria-label="Loại nhu cầu">
                <button
                  className={`need-type-segment ${styles.typeSegment} ${type === "PRODUCT" ? `need-type-segment--active ${styles.typeSegmentActive}` : ""}`}
                  type="button"
                  disabled={submitting}
                  aria-pressed={type === "PRODUCT"}
                  onClick={() => changeType("PRODUCT")}
                >
                  <Package size={17} weight={type === "PRODUCT" ? "fill" : "regular"} />
                  <span>Sản phẩm</span>
                </button>
                <button
                  className={`need-type-segment ${styles.typeSegment} ${type === "SERVICE" ? `need-type-segment--active ${styles.typeSegmentActive}` : ""}`}
                  type="button"
                  disabled={submitting}
                  aria-pressed={type === "SERVICE"}
                  onClick={() => changeType("SERVICE")}
                >
                  <Wrench size={17} weight={type === "SERVICE" ? "fill" : "regular"} />
                  <span>Dịch vụ</span>
                </button>
              </div>
              <small className={styles.help}>Chọn tìm sản phẩm hoặc đặt dịch vụ</small>
            </div>

            <div className="need-form__category-field">
              <span style={{ fontSize: "13px", fontWeight: 600, color: "#334155", display: "block", marginBottom: 6 }}>Danh mục</span>
              <SelectDropdown
                aria-label="Danh mục"
                placeholder="Chọn danh mục"
                required
                disabled={!ready || submitting}
                value={categoryId}
                options={categoryOptions}
                onChange={(val) => setCategoryId(String(val))}
                radius="md"
                size="md"
              />
              <small className={styles.help}>Chọn danh mục phù hợp</small>
            </div>
          </div>

          <div className="need-form__field-group">
            <label>
              <span>Tên / nhu cầu</span>
              <input required maxLength={220} value={title} onChange={(event) => handleTitleChange(event.target.value)} placeholder="Bạn đang cần tìm gì?" />
              <small className={styles.help}>Mô tả ngắn thứ bạn đang tìm</small>
            </label>

            <div className="need-form__quick-templates" aria-label="Gợi ý mẫu nhanh">
              <span className="need-form__templates-label">Gợi ý tìm nhanh:</span>
              <div className="need-form__template-chips">
                {(type === "PRODUCT" ? productTemplates : serviceTemplates).map((item) => (
                  <button
                    key={item.label}
                    type="button"
                    className="need-form__template-chip"
                    onClick={() => applyTemplate(item.title, item.query)}
                  >
                    <Tag size={12} weight="bold" />
                    <span>{item.label}</span>
                  </button>
                ))}
              </div>
            </div>
          </div>

          <label>
            <span>Từ khóa tìm kiếm</span>
            <input required maxLength={500} value={queryText} onChange={(event) => setQueryText(event.target.value)} placeholder="Ví dụ: rau hữu cơ, sửa máy lạnh..." />
            <small className={styles.help}>Dùng để tìm catalog phù hợp (tự động điền theo tên món đồ)</small>
          </label>

          <div className={`need-form__grid ${styles.createFormGrid}`}>
            {type === "PRODUCT" ? (
              <label>
                <span>Số lượng</span>
                <input required type="number" min={1} max={Number.MAX_SAFE_INTEGER} step={1} value={quantity} onChange={(event) => setQuantity(event.target.value)} />
                <small className={styles.help}>Số lượng bạn muốn tìm</small>
              </label>
            ) : null}

            <div className="need-form__radius-field">
              <span style={{ fontSize: "13px", fontWeight: 600, color: "#334155", display: "block", marginBottom: 6 }}>Phạm vi tìm</span>
              <SelectDropdown
                aria-label="Phạm vi tìm"
                value={radiusMeters}
                options={RADIUS_OPTIONS}
                onChange={(val) => setRadiusMeters(String(val))}
                radius="md"
                size="md"
              />
              <small className={styles.help}>Khoảng cách tối đa từ vị trí đã chọn</small>
            </div>
          </div>

          <div>
            <span style={{ fontSize: "13px", fontWeight: 600, color: "#334155", display: "block", marginBottom: 6 }}>Vị trí</span>
            <SelectDropdown
              aria-label="Vị trí"
              placeholder="Chọn địa chỉ đã lưu"
              required
              disabled={!ready || submitting}
              value={addressId}
              options={addressOptions}
              onChange={(val) => setAddressId(String(val))}
              radius="md"
              size="md"
            />
            <small className={styles.help}>Địa chỉ đã lưu của bạn</small>
          </div>
          {ready && !addresses.length ? <p className={`need-form__help ${styles.help}`}>Bạn cần một địa chỉ đã lưu. <Link href="/me/addresses">Quản lý địa chỉ</Link></p> : null}

          <div className="need-form__expiry-group">
            <label>
              <span>Thời hạn</span>
              <input required type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
              <small className={styles.help}>Thời điểm bài đăng tự động kết thúc nếu chưa tìm được</small>
            </label>

            <div className="need-form__quick-expiry-row" role="group" aria-label="Chọn nhanh thời hạn">
              <span className="need-form__templates-label">Chọn nhanh:</span>
              <div className="need-form__template-chips">
                <button type="button" className="need-form__template-chip" onClick={() => setQuickExpiry(2)}>
                  <Clock size={12} weight="bold" />
                  <span>2 giờ tới</span>
                </button>
                <button type="button" className="need-form__template-chip" onClick={setTodayMidnightExpiry}>
                  <Clock size={12} weight="bold" />
                  <span>Hết hôm nay</span>
                </button>
                <button type="button" className="need-form__template-chip" onClick={() => setQuickExpiry(24)}>
                  <Clock size={12} weight="bold" />
                  <span>24 giờ tới</span>
                </button>
                <button type="button" className="need-form__template-chip" onClick={() => setQuickExpiry(72)}>
                  <Clock size={12} weight="bold" />
                  <span>3 ngày tới</span>
                </button>
              </div>
            </div>
          </div>

          <label>
            <span>Mô tả thêm</span>
            <textarea maxLength={4000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Mô tả thêm để người bán hiểu đúng nhu cầu (nhãn hiệu, đặc điểm, tình trạng...)" />
            <small className={styles.help}>Không bắt buộc</small>
          </label>

          {error ? <p className="form-alert" role="alert">{error}</p> : null}
          {loadError ? <div role="alert">
            <p>{isAuthError ? "Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập." : loadError}</p>
            {isAuthError
              ? <Link className="button-primary" href="/login?returnTo=%2Fneeds%2Fnew">Đăng nhập lại</Link>
              : <button className="button-secondary" type="button" onClick={() => {setReadyType(undefined);setLoadError(undefined);setReloadKey(value => value + 1);}}>Thử lại</button>}
          </div> : !ready ? <p role="status">Đang tải danh mục và địa chỉ…</p> : null}
          <div className={`need-form__actions ${styles.formActions}`}>
            <button className="button-primary" disabled={!ready || submitting || !categoryId || !addressId} type="submit">
              {submitting ? "Đang đăng…" : "Đăng Cần mua"}
            </button>
            <Link className="button-link" href="/needs">Hủy</Link>
          </div>
        </form>
      </div>

      <aside className="need-create-preview-column" aria-label="Xem trước bài đăng">
        <div className="need-live-preview">
          <div className="need-live-preview__header">
            <div className="need-live-preview__title-wrap">
              <Eye size={16} weight="duotone" className="need-live-preview__icon" />
              <span className="need-live-preview__title">Bản xem trước trên bảng tin</span>
            </div>
            <span className="need-live-preview__badge">Người bán nhìn thấy</span>
          </div>

          <article className="need-live-preview__card">
            <div className="need-live-preview__top">
              <div className="need-live-preview__type">
                {type === "SERVICE" ? (
                  <span className="need-live-preview__type-tag"><Wrench size={13} weight="bold" /> Dịch vụ</span>
                ) : (
                  <span className="need-live-preview__type-tag"><Package size={13} weight="bold" /> Sản phẩm</span>
                )}
                {selectedCategory ? <span className="need-live-preview__category">· {selectedCategory.name}</span> : null}
              </div>
              <span className="need-live-preview__status-pill">Đang tìm</span>
            </div>

            <h3 className="need-live-preview__heading">
              {title.trim() ? title : "Tiêu đề món đồ hoặc dịch vụ bạn đang tìm..."}
            </h3>

            {description.trim() ? (
              <p className="need-live-preview__desc">{description}</p>
            ) : null}

            <div className="need-live-preview__meta">
              <div>
                <MapPin size={13} weight="bold" />
                <span>Bán kính: {(Number(radiusMeters) / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km</span>
              </div>
              {type === "PRODUCT" ? (
                <div>
                  <Package size={13} weight="bold" />
                  <span>Số lượng: {quantity || "1"}</span>
                </div>
              ) : null}
              {selectedAddress ? (
                <div>
                  <MapPin size={13} weight="bold" />
                  <span>Địa điểm: {selectedAddress.label || selectedAddress.addressText}</span>
                </div>
              ) : null}
              {expiresAt ? (
                <div>
                  <Clock size={13} weight="bold" />
                  <span>Hết hạn: {new Date(expiresAt).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" })} {new Date(expiresAt).toLocaleDateString("vi-VN")}</span>
                </div>
              ) : null}
            </div>
          </article>

          <div className="need-live-preview__tips">
            <div className="need-live-preview__tips-title">
              <Lightbulb size={16} weight="duotone" />
              <h4>Mẹo để nhận báo giá nhanh</h4>
            </div>
            <ul>
              <li><strong>Phạm vi hẹp (&le; 1km):</strong> Người bán cùng tòa hoặc liền kề có thể phản hồi và giao hàng chỉ trong 10-20 phút.</li>
              <li><strong>Mô tả chi tiết:</strong> Nêu rõ nhãn hiệu hoặc yêu cầu kỹ thuật để nhận báo giá chuẩn xác nhất.</li>
              <li><strong>Miễn phí đăng tin:</strong> Bạn chỉ thanh toán khi đã chọn được báo giá ưng ý từ người bán.</li>
            </ul>
          </div>
        </div>
      </aside>
    </div>
  );
}
