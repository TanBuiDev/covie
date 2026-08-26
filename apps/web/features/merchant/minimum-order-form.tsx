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
    <form className="seller-minimum-order-form" onSubmit={submit} aria-busy={pending}>
      <label className="seller-switch-row">
        <input
          type="checkbox"
          checked={enabled}
          disabled={pending || !canRoundTrip}
          onChange={(event) => setEnabled(event.target.checked)}
        />
        <span>Yêu cầu đơn tối thiểu để giao</span>
      </label>
      {enabled ? (
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
      ) : null}
      <aside className="seller-info-card">
        <strong>Nhận tại chỗ không bị chặn</strong>
        <p>Nếu điểm bán cho phép nhận tại chỗ, khách vẫn có thể chọn hình thức này dù chưa đạt mức tối thiểu giao hàng.</p>
      </aside>
      {!canRoundTrip ? (
        <p role="alert">Không đủ dữ liệu điểm bán để cập nhật an toàn. Hãy tải lại trang hoặc kiểm tra quyền truy cập.</p>
      ) : null}
      {message ? <p role={message.startsWith("Đã") ? "status" : "alert"}>{message}</p> : null}
      {message?.startsWith("Điểm bán đã") ? (
        <button type="button" disabled={pending} onClick={() => router.refresh()}>Tải dữ liệu mới</button>
      ) : null}
      <button type="submit" disabled={pending || !canRoundTrip}>
        {pending ? "Đang lưu…" : "Lưu đơn tối thiểu"}
      </button>
    </form>
  );
}
