"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import {
  FloppyDisk,
  Info,
  MapPin,
  PlusCircle,
  Sparkle,
  Storefront,
  Truck,
} from "@phosphor-icons/react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { createLocation, updateLocation } from "./merchant-api";

type Location = components["schemas"]["MerchantLocationResponse"];

export function LocationForm({ merchantId, location, onSaved, onReload }: {
  merchantId: string;
  location?: Etagged<Location>;
  onSaved?: () => void;
  onReload?: () => void;
}) {
  const router = useRouter();
  const [message, setMessage] = useState<string>();
  const [pending, setPending] = useState(false);
  const editing = Boolean(location?.data.id);
  const reload = onReload ?? (() => router.refresh());
  const saved = onSaved ?? (() => router.refresh());

  // Live preview states
  const [previewName, setPreviewName] = useState(location?.data.name ?? "");
  const [previewAddress, setPreviewAddress] = useState(location?.data.addressText ?? "");
  const [previewPickup, setPreviewPickup] = useState(location?.data.pickupEnabled ?? true);
  const [previewDelivery, setPreviewDelivery] = useState(location?.data.sellerDeliveryEnabled ?? false);
  const [previewService, setPreviewService] = useState(location?.data.serviceEnabled ?? false);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const latitude = Number(form.get("latitude"));
    const longitude = Number(form.get("longitude"));
    if (!Number.isFinite(latitude) || latitude < -90 || latitude > 90 || !Number.isFinite(longitude) || longitude < -180 || longitude > 180) {
      setMessage("Tọa độ chưa hợp lệ. Vui lòng nhập vĩ độ và kinh độ chính xác của điểm bán.");
      return;
    }

    const input: components["schemas"]["MerchantLocationUpsertRequest"] = {
      name: String(form.get("name") ?? "").trim(),
      addressText: String(form.get("addressText") ?? "").trim(),
      locationAreaId: location?.data.locationAreaId,
      latitude,
      longitude,
      pinSource: location?.data.pinSource ?? "USER_ADJUSTED",
      timezone: String(form.get("timezone") ?? "").trim(),
      pickupEnabled: form.get("pickupEnabled") === "on",
      sellerDeliveryEnabled: form.get("sellerDeliveryEnabled") === "on",
      serviceEnabled: form.get("serviceEnabled") === "on",
      minimumDeliveryOrder: location?.data.minimumDeliveryOrder ?? 0,
      currency: location?.data.currency ?? "VND",
    };

    setPending(true);
    setMessage(undefined);
    try {
      if (editing && location?.data.id) await updateLocation(merchantId, location.data.id, location.etag, input);
      else await createLocation(merchantId, input);
      saved();
      if (!editing) router.push(`/seller/${encodeURIComponent(merchantId)}/locations`);
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Điểm bán đã được thay đổi ở nơi khác. Hãy tải dữ liệu mới trước khi lưu lại.");
      } else {
        setMessage("Không thể lưu điểm bán. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  const formElement = (
    <form className="seller-location-form" onSubmit={submit} aria-busy={pending}>
      <div className="seller-location-form__section">
        <div className="seller-location-form__section-header">
          <Storefront size={20} weight="duotone" className="seller-location-form__icon" aria-hidden="true" />
          <h3>Thông tin cơ bản</h3>
        </div>

        <label>
          Tên điểm bán <span aria-hidden="true">*</span>
          <input
            name="name"
            required
            maxLength={150}
            disabled={pending}
            defaultValue={location?.data.name ?? ""}
            onChange={(e) => setPreviewName(e.target.value)}
          />
          <small>Tên giúp bạn phân biệt điểm bán này</small>
        </label>
        <label>
          Địa chỉ <span aria-hidden="true">*</span>
          <input
            name="addressText"
            required
            maxLength={1000}
            disabled={pending}
            defaultValue={location?.data.addressText ?? ""}
            onChange={(e) => setPreviewAddress(e.target.value)}
          />
          <small>Mỗi điểm bán được quản lý riêng.</small>
        </label>
        <label>
          Múi giờ <span aria-hidden="true">*</span>
          <select name="timezone" required disabled={pending} defaultValue={location?.data.timezone ?? "Asia/Ho_Chi_Minh"}>
            <option value="Asia/Ho_Chi_Minh">Asia/Ho_Chi_Minh</option>
          </select>
          <small>Dùng cho giờ vận hành và thời hạn xử lý</small>
        </label>
      </div>

      <div className="seller-location-form__section">
        <div className="seller-location-form__section-header">
          <Truck size={20} weight="duotone" className="seller-location-form__icon" aria-hidden="true" />
          <h3>Hình thức phục vụ khách hàng</h3>
        </div>

        <div className="seller-location-switches">
          <label className="seller-switch-row seller-switch-card">
            <input
              name="pickupEnabled"
              type="checkbox"
              disabled={pending}
              defaultChecked={location?.data.pickupEnabled ?? true}
              onChange={(e) => setPreviewPickup(e.target.checked)}
            />
            <div className="seller-switch-card__copy">
              <span>Cho phép nhận tại chỗ</span>
              <small>Khách hàng có thể đặt đơn và đến lấy hàng trực tiếp tại chi nhánh.</small>
            </div>
          </label>
          <label className="seller-switch-row seller-switch-card">
            <input
              name="sellerDeliveryEnabled"
              type="checkbox"
              disabled={pending}
              defaultChecked={location?.data.sellerDeliveryEnabled ?? false}
              onChange={(e) => setPreviewDelivery(e.target.checked)}
            />
            <div className="seller-switch-card__copy">
              <span>Cho phép giao đến địa chỉ khách</span>
              <small>Chi nhánh tự giao hoặc thuê shipper giao đến bán kính được cấu hình.</small>
            </div>
          </label>
          <label className="seller-switch-row seller-switch-card">
            <input
              name="serviceEnabled"
              type="checkbox"
              disabled={pending}
              defaultChecked={location?.data.serviceEnabled ?? false}
              onChange={(e) => setPreviewService(e.target.checked)}
            />
            <div className="seller-switch-card__copy">
              <span>Có phục vụ dịch vụ tại khu vực khách</span>
              <small>Áp dụng cho nhà cung cấp dịch vụ đến tận nơi khách hàng.</small>
            </div>
          </label>
        </div>
      </div>

      <details className="seller-technical-fields" open={!editing}>
        <summary>Tọa độ điểm bán</summary>
        <p className="seller-muted">Phiên bản hiện tại cần vĩ độ và kinh độ để lưu điểm bán. Chỉ nhập tọa độ đã xác minh; không dùng giá trị ước lượng.</p>
        <div className="seller-grid seller-grid--two">
          <label>
            Vĩ độ
            <input name="latitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.latitude ?? ""} />
          </label>
          <label>
            Kinh độ
            <input name="longitude" type="number" step="any" inputMode="decimal" required disabled={pending} defaultValue={location?.data.longitude ?? ""} />
          </label>
        </div>
      </details>

      {message ? <p role="alert" className="seller-location-form__alert">{message}</p> : null}
      {message?.startsWith("Điểm bán đã") ? (
        <button type="button" disabled={pending} className="seller-button seller-button--secondary" onClick={reload}>
          Tải dữ liệu mới
        </button>
      ) : null}
      <button type="submit" disabled={pending} className="seller-button seller-button--primary seller-location-submit-btn">
        {editing ? (
          <FloppyDisk size={16} weight="bold" aria-hidden="true" />
        ) : (
          <PlusCircle size={16} weight="bold" aria-hidden="true" />
        )}
        <span>{pending ? "Đang lưu…" : editing ? "Lưu điểm bán" : "Thêm điểm bán"}</span>
      </button>
    </form>
  );

  if (editing) {
    return formElement;
  }

  return (
    <div className="seller-location-create-layout">
      <div className="seller-location-create-main">
        <header className="seller-location-create-header">
          <p className="eyebrow">Seller Center / Điểm bán mới</p>
          <h1>Thêm điểm bán</h1>
          <p className="seller-muted">Thiết lập chi nhánh mới để tiếp nhận đơn hàng, tổ chức giao nhận và hiển thị đến khách mua CoVie.</p>
        </header>
        {formElement}
      </div>

      <aside className="seller-location-create-aside">
        <div className="seller-live-preview-box">
          <div className="seller-live-preview-box__title">
            <Sparkle size={16} weight="fill" className="seller-brand-accent" aria-hidden="true" />
            <span>Xem trước thẻ chi nhánh</span>
          </div>

          <div className="seller-branch-preview-card">
            <div className="seller-branch-preview-card__head">
              <div className="seller-branch-preview-card__icon" aria-hidden="true">
                <Storefront size={24} weight="duotone" />
              </div>
              <div className="seller-branch-preview-card__titles">
                <strong className="seller-branch-preview-card__name">
                  {previewName.trim() || "Tên điểm bán mới"}
                </strong>
                <p className="seller-branch-preview-card__address">
                  <MapPin size={13} weight="bold" aria-hidden="true" />
                  <span>{previewAddress.trim() || "Chưa nhập địa chỉ cụ thể"}</span>
                </p>
              </div>
            </div>

            <div className="seller-branch-preview-card__capabilities">
              {previewPickup ? (
                <span className="seller-capability-tag seller-capability-tag--emerald">Nhận tại chỗ</span>
              ) : null}
              {previewDelivery ? (
                <span className="seller-capability-tag seller-capability-tag--blue">Giao hàng</span>
              ) : null}
              {previewService ? (
                <span className="seller-capability-tag seller-capability-tag--purple">Dịch vụ</span>
              ) : null}
              {!previewPickup && !previewDelivery && !previewService ? (
                <small className="seller-muted">Chưa bật hình thức phục vụ</small>
              ) : null}
            </div>

            <div className="seller-branch-preview-card__footer">
              <span className="status-chip status-chip--neutral">Sẵn sàng kích hoạt</span>
            </div>
          </div>

          <div className="seller-create-tips-card">
            <h4>
              <Info size={16} weight="bold" aria-hidden="true" />
              <span>Mẹo vận hành chi nhánh</span>
            </h4>
            <ul className="seller-tips-list">
              <li><strong>Tên chi nhánh:</strong> Nên kèm tên đường hoặc quận (ví dụ: &quot;Chi nhánh Quận 1 - Nguyễn Huệ&quot;) để khách hàng dễ chọn.</li>
              <li><strong>Hình thức phục vụ:</strong> Bật nhận tại chỗ nếu khách có thể ghé lấy trực tiếp, giúp tiết kiệm phí vận chuyển.</li>
              <li><strong>Tọa độ vĩ độ/kinh độ:</strong> Cần nhập chính xác để CoVie tính khoảng cách và định tuyến phí giao hàng chuẩn xác.</li>
            </ul>
          </div>
        </div>
      </aside>
    </div>
  );
}
