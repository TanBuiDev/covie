"use client";

import type { components } from "@covie/api-client";
import { useRouter } from "next/navigation";
import { FormEvent, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import type { Etagged } from "./merchant-api";
import { updateLocation } from "./merchant-api";

type Location = components["schemas"]["MerchantLocationResponse"];

export function MinimumOrderForm({ merchantId, location }: { merchantId: string; location: Etagged<Location> }) {
  const router = useRouter();
  const initialAmount = location.data.minimumDeliveryOrder ?? 0;
  const [enabled, setEnabled] = useState(initialAmount > 0);
  const [amount, setAmount] = useState(String(initialAmount > 0 ? initialAmount : 50000));
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState<string>();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const parsed = Number(amount);
    if (enabled && (!Number.isSafeInteger(parsed) || parsed <= 0)) {
      setMessage("Giá trị đơn tối thiểu phải là số nguyên dương.");
      return;
    }

    const current = location.data;
    if (!current.id || current.latitude == null || current.longitude == null || !current.pinSource || !current.timezone) {
      setMessage("Dữ liệu điểm bán chưa đủ để cập nhật an toàn. Hãy tải lại điểm bán.");
      return;
    }
    setPending(true);
    setMessage(undefined);
    try {
      await updateLocation(merchantId, current.id, location.etag, {
        name: current.name ?? "",
        addressText: current.addressText ?? "",
        locationAreaId: current.locationAreaId,
        latitude: current.latitude,
        longitude: current.longitude,
        pinSource: current.pinSource,
        timezone: current.timezone,
        pickupEnabled: current.pickupEnabled ?? false,
        sellerDeliveryEnabled: current.sellerDeliveryEnabled ?? false,
        serviceEnabled: current.serviceEnabled ?? false,
        minimumDeliveryOrder: enabled ? parsed : 0,
        currency: current.currency ?? "VND",
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
        <input type="checkbox" checked={enabled} disabled={pending} onChange={(event) => setEnabled(event.target.checked)} />
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
            value={amount}
            disabled={pending}
            required
            onChange={(event) => setAmount(event.target.value)}
          />
          <small>Chỉ áp dụng cho giao hàng</small>
        </label>
      ) : null}
      <aside className="seller-info-card">
        <strong>Nhận tại chỗ không bị chặn</strong>
        <p>Nếu điểm bán cho phép nhận tại chỗ, khách vẫn có thể chọn hình thức này dù chưa đạt mức tối thiểu giao hàng.</p>
      </aside>
      {message ? <p role={message.startsWith("Đã") ? "status" : "alert"}>{message}</p> : null}
      {message?.startsWith("Điểm bán đã") ? <button type="button" disabled={pending} onClick={() => router.refresh()}>Tải dữ liệu mới</button> : null}
      <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : "Lưu đơn tối thiểu"}</button>
    </form>
  );
}
