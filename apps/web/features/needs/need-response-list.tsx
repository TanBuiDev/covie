"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  commitNeedCheckout,
  listNeedAddresses,
  listNeedResponses,
  previewNeedCheckout,
  type NeedAddress,
  type NeedResponse,
} from "./need-api";

function money(amount?: number, currency?: string): string {
  if (amount == null || !currency) return "Liên hệ giá";
  try {
    return new Intl.NumberFormat("vi-VN", { style: "currency", currency, maximumFractionDigits: 0 }).format(amount);
  } catch {
    return `${amount.toLocaleString("vi-VN")} ${currency}`;
  }
}

export function NeedResponseList({ needId }: { needId: string }) {
  const [responses, setResponses] = useState<NeedResponse[]>([]);
  const [addresses, setAddresses] = useState<NeedAddress[]>([]);
  const [addressId, setAddressId] = useState("");
  const [message, setMessage] = useState<string | null>(null);
  const [createdOrder, setCreatedOrder] = useState<{ id: string; orderCode: string } | null>(null);
  const [pendingItem, setPendingItem] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    Promise.all([listNeedResponses(needId), listNeedAddresses()])
      .then(([page, savedAddresses]) => {
        if (!active) return;
        setResponses(page.items);
        setAddresses(savedAddresses);
        setAddressId(savedAddresses.find((item) => item.defaultAddress)?.id ?? savedAddresses[0]?.id ?? "");
      })
      .catch((cause) => {
        if (active) setMessage(cause instanceof Error ? cause.message : "Không thể tải phản hồi.");
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [needId]);

  async function checkout(responseItem: NeedResponse["items"][number], mode: "PICKUP" | "SELLER_DELIVERY") {
    setMessage(null);
    setCreatedOrder(null);
    setPendingItem(responseItem.id);
    try {
      const preview = await previewNeedCheckout({
        needRequestId: needId,
        needResponseItemId: responseItem.id,
        locationVariantId: responseItem.locationVariantId,
        quantity: 1,
        fulfillmentMethod: mode,
        addressId: mode === "SELLER_DELIVERY" ? addressId || undefined : undefined,
        paymentMethod: "COD",
      });
      const order = await commitNeedCheckout(preview.checkoutToken);
      setCreatedOrder(order);
      setMessage(`Đã tạo đơn ${order.orderCode}.`);
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Không thể tạo đơn từ phản hồi này.");
    } finally {
      setPendingItem(null);
    }
  }

  return (
    <section className="need-responses" aria-labelledby="need-responses-title">
      <div className="need-responses__header">
        <div>
          <h2 id="need-responses-title">Phản hồi từ người bán</h2>
          <p>Giá và khả dụng bên dưới được lấy trực tiếp từ phản hồi hiện tại của hệ thống.</p>
        </div>
        {addresses.length ? (
          <label className="need-responses__address">
            <span>Địa chỉ giao hàng</span>
            <select value={addressId} onChange={(event) => setAddressId(event.target.value)}>
              {addresses.map((address) => address.id ? (
                <option key={address.id} value={address.id}>{address.label ? `${address.label} · ` : ""}{address.addressText ?? "Địa chỉ đã lưu"}</option>
              ) : null)}
            </select>
          </label>
        ) : null}
      </div>

      {message ? (
        <p className={createdOrder ? "form-success" : "form-alert"} role="status">
          {message} {createdOrder ? <Link href={`/orders/${createdOrder.id}`}>Xem đơn hàng</Link> : null}
        </p>
      ) : null}

      {loading ? <p className="need-responses__loading">Đang tải phản hồi…</p> : null}
      {!loading && !responses.length ? <div className="need-empty"><h3>Chưa có phản hồi phù hợp</h3><p>Phản hồi mới sẽ xuất hiện ở đây khi backend ghi nhận.</p></div> : null}

      <div className="need-response-cards">
        {responses.map((response) => (
          <article className="need-response-card" key={response.id}>
            <div className="need-response-card__merchant">
              <div>
                <h3>{response.merchantName || "Người bán"}</h3>
                <p>{response.merchantLocationName || "Điểm bán"}{response.distanceMeters != null ? ` · ${(response.distanceMeters / 1000).toLocaleString("vi-VN", { maximumFractionDigits: 1 })} km` : ""}</p>
              </div>
              <span className={`need-status need-status--${response.status.toLowerCase()}`}>{response.status === "ACTIVE" ? "Đang hoạt động" : "Đã rút"}</span>
            </div>
            {response.note ? <p className="need-response-card__note">{response.note}</p> : null}
            <div className="need-response-card__items">
              {response.items.map((item) => {
                const price = item.currentPrice || item.priceAtResponse;
                const pending = pendingItem === item.id;
                return (
                  <div className="need-response-item" key={item.id}>
                    <div>
                      <strong>{money(price?.amount, price?.currency)}</strong>
                      <small>{item.available ? "Đang có sẵn" : "Hiện không có sẵn"}</small>
                    </div>
                    {item.actionable ? (
                      <div className="need-response-item__actions">
                        <button className="button-primary" disabled={pending} type="button" onClick={() => void checkout(item, "PICKUP")}>{pending ? "Đang xử lý…" : "Nhận tại cửa hàng"}</button>
                        {addresses.length ? <button className="button-secondary" disabled={pending || !addressId} type="button" onClick={() => void checkout(item, "SELLER_DELIVERY")}>Giao đến địa chỉ</button> : null}
                      </div>
                    ) : <span className="need-response-item__unavailable">Backend hiện không cho phép đặt mục này.</span>}
                  </div>
                );
              })}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
