"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useMemo, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { updateLocation } from "./merchant-api";

type LocationResponse = components["schemas"]["MerchantLocationResponse"];

function hasRequiredRoundTripFields(location: LocationResponse) {
  return Boolean(
    location.id
    && location.name
    && location.addressText
    && location.pinSource
    && location.timezone
    && location.currency
    && Number.isFinite(location.latitude)
    && Number.isFinite(location.longitude),
  );
}

import { Coins, FloppyDisk, Info, Sparkle, Storefront, ShoppingBag } from "@phosphor-icons/react";

const MINIMUM_ORDER_PRESETS = [30000, 50000, 100000, 150000, 200000] as const;

export function MinimumOrderForm({
  merchantId,
  location,
}: {
  merchantId: string;
  location: Etagged<LocationResponse>;
}) {
  const router = useRouter();
  const initialMinimum = location.data.minimumDeliveryOrder ?? 0;
  const [enabled, setEnabled] = useState(initialMinimum > 0);
  const [minimum, setMinimum] = useState(String(initialMinimum > 0 ? initialMinimum : 50000));
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();
  const canRoundTrip = useMemo(() => hasRequiredRoundTripFields(location.data), [location.data]);

  const parsedMinimum = Number(minimum) || 0;
  const mockCartAmount = 80000;
  const isSatisfied = !enabled || mockCartAmount >= parsedMinimum;
  const remainingAmount = Math.max(0, parsedMinimum - mockCartAmount);
  const progressPercent = enabled && parsedMinimum > 0 ? Math.min(100, Math.round((mockCartAmount / parsedMinimum) * 100)) : 100;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending || !canRoundTrip || !location.data.id) return;

    const parsed = Number(minimum);
    if (enabled && (!Number.isSafeInteger(parsed) || parsed <= 0)) {
      setMessage("Giá trị đơn tối thiểu phải là số nguyên dương.");
      return;
    }

    setPending(true);
    setMessage(undefined);
    try {
      await updateLocation(merchantId, location.data.id, location.etag, {
        name: location.data.name!,
        addressText: location.data.addressText!,
        locationAreaId: location.data.locationAreaId,
        latitude: location.data.latitude!,
        longitude: location.data.longitude!,
        pinSource: location.data.pinSource!,
        timezone: location.data.timezone!,
        pickupEnabled: Boolean(location.data.pickupEnabled),
        sellerDeliveryEnabled: Boolean(location.data.sellerDeliveryEnabled),
        serviceEnabled: Boolean(location.data.serviceEnabled),
        minimumDeliveryOrder: enabled ? parsed : 0,
        currency: location.data.currency!,
      });
      setMessage("Đã lưu đơn tối thiểu.");
      router.refresh();
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "RESOURCE_VERSION_CONFLICT") {
        setMessage("Điểm bán đã thay đổi. Hãy tải dữ liệu mới trước khi lưu lại.");
      } else {
        setMessage("Không thể lưu đơn tối thiểu. Vui lòng thử lại.");
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <div className="seller-minimum-order-studio-layout">
      <div className="seller-minimum-order-studio-main">
        <form className="seller-minimum-order-form" onSubmit={submit} aria-busy={pending}>
          <div className="seller-section__heading seller-desktop-only">
            <h2>Thiết lập đơn tối thiểu</h2>
          </div>

          <label className="seller-switch-row seller-switch-card">
            <input
              type="checkbox"
              checked={enabled}
              disabled={pending || !canRoundTrip}
              onChange={(event) => setEnabled(event.target.checked)}
            />
            <div className="seller-switch-card__copy">
              <span>Yêu cầu đơn tối thiểu để giao</span>
              <small>Áp dụng mức tiền giỏ hàng tối thiểu khi khách chọn phương thức giao tận nơi.</small>
            </div>
          </label>

          {enabled ? (
            <div className="seller-minimum-order-input-group">
              <label>
                Giá trị đơn tối thiểu <span aria-hidden="true">*</span>
                <input
                  type="number"
                  inputMode="numeric"
                  min="1"
                  step="1"
                  value={minimum}
                  disabled={pending || !canRoundTrip}
                  required
                  onChange={(event) => setMinimum(event.target.value)}
                />
                <small>Chỉ áp dụng cho giao hàng</small>
              </label>

              {/* Quick Amount Presets */}
              <div className="seller-radius-preset-pills" aria-hidden="true">
                {MINIMUM_ORDER_PRESETS.map((preset) => (
                  <button
                    key={preset}
                    type="button"
                    tabIndex={-1}
                    className={`seller-preset-pill ${Number(minimum) === preset ? "is-selected" : ""}`}
                    disabled={pending || !canRoundTrip}
                    onClick={() => setMinimum(String(preset))}
                  >
                    {preset.toLocaleString("vi-VN")} ₫
                  </button>
                ))}
              </div>
            </div>
          ) : null}

          <aside className="seller-info-card seller-info-card--emerald">
            <div className="seller-info-card__icon" aria-hidden="true">
              <Storefront size={20} weight="duotone" />
            </div>
            <div>
              <strong>Nhận tại chỗ không bị chặn</strong>
              <p>Nếu điểm bán cho phép nhận tại chỗ, khách vẫn có thể chọn hình thức này dù chưa đạt mức tối thiểu giao hàng.</p>
            </div>
          </aside>

          {!canRoundTrip ? (
            <p role="alert">Không đủ dữ liệu điểm bán để cập nhật an toàn. Hãy tải lại trang hoặc kiểm tra quyền truy cập.</p>
          ) : null}
          {message ? <p role={message.startsWith("Đã") ? "status" : "alert"}>{message}</p> : null}
          {message?.startsWith("Điểm bán đã") ? (
            <button type="button" disabled={pending} onClick={() => router.refresh()}>Tải dữ liệu mới</button>
          ) : null}
          <button
            type="submit"
            disabled={pending || !canRoundTrip}
            className="seller-button seller-button--primary seller-location-submit-btn"
          >
            <FloppyDisk size={16} weight="bold" aria-hidden="true" />
            <span>{pending ? "Đang lưu…" : "Lưu đơn tối thiểu"}</span>
          </button>
        </form>
      </div>

      {/* ASIDE: LIVE BUYER CHECKOUT PREVIEW */}
      <aside className="seller-minimum-order-studio-aside">
        <div className="seller-live-preview-box">
          <div className="seller-live-preview-box__title">
            <Sparkle size={16} weight="fill" className="seller-brand-accent" aria-hidden="true" />
            <span>Xem trước trải nghiệm người mua</span>
          </div>

          <div className="seller-buyer-preview-card">
            <div className="seller-buyer-preview-card__head">
              <ShoppingBag size={20} weight="duotone" className="seller-brand-accent" aria-hidden="true" />
              <strong>Giỏ hàng mẫu của khách mua</strong>
            </div>

            <div className="seller-buyer-preview-card__body">
              <div className="seller-buyer-cart-summary">
                <span>Tổng tiền hàng tạm tính:</span>
                <strong>{mockCartAmount.toLocaleString("vi-VN")} ₫</strong>
              </div>

              {enabled ? (
                <div className="seller-buyer-cart-threshold">
                  <div className="seller-progress-meta">
                    <span>Ngưỡng giao hàng: {parsedMinimum.toLocaleString("vi-VN")} ₫</span>
                    <span>{progressPercent}%</span>
                  </div>
                  <div className="seller-progress-bar-track">
                    <div
                      className={`seller-progress-bar-fill ${isSatisfied ? "is-satisfied" : ""}`}
                      style={{ width: `${progressPercent}%` }}
                    />
                  </div>
                  {!isSatisfied ? (
                    <div className="seller-threshold-alert seller-threshold-alert--warning">
                      <span>Cần thêm {remainingAmount.toLocaleString("vi-VN")} ₫ để giao tận nơi. Khách có thể mua thêm hoặc chọn Nhận tại cửa hàng.</span>
                    </div>
                  ) : (
                    <div className="seller-threshold-alert seller-threshold-alert--success">
                      <span>Đã đủ điều kiện giao hàng tận nơi!</span>
                    </div>
                  )}
                </div>
              ) : (
                <div className="seller-threshold-alert seller-threshold-alert--neutral">
                  <span>Không yêu cầu mức tối thiểu. Khách có thể đặt bất kỳ giá trị nào.</span>
                </div>
              )}
            </div>
          </div>

          <div className="seller-create-tips-card">
            <h4>
              <Info size={16} weight="bold" aria-hidden="true" />
              <span>Mẹo chọn ngưỡng đơn</span>
            </h4>
            <ul className="seller-tips-list">
              <li><strong>Chi phí đóng gói:</strong> Hãy tính cả chi phí hộp, túi, đá gel khi đặt ngưỡng tối thiểu.</li>
              <li><strong>Mức đề xuất:</strong> 50.000 ₫ – 100.000 ₫ là mức cân bằng tốt nhất giữa giá trị đơn và tỷ lệ hoàn tất mua hàng.</li>
              <li><strong>Khách mua nhỏ lẻ:</strong> Vẫn có thể đặt và ghé lấy trực tiếp tại chi nhánh mà không bị rào cản.</li>
            </ul>
          </div>
        </div>
      </aside>
    </div>
  );
}
