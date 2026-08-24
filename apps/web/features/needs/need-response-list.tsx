"use client";

import { useEffect, useState } from "react";
import {
  commitNeedCheckout,
  listNeedResponses,
  previewNeedCheckout,
  type NeedResponse,
} from "./need-api";

export function NeedResponseList({ needId }: { needId: string }) {
  const [responses, setResponses] = useState<NeedResponse[]>([]);
  const [addressId, setAddressId] = useState("");
  const [message, setMessage] = useState<string | null>(null);

  useEffect(() => {
    listNeedResponses(needId)
      .then((page) => setResponses(page.items))
      .catch((cause) => setMessage(cause instanceof Error ? cause.message : "Không thể tải phản hồi."));
  }, [needId]);

  async function checkout(responseItem: NeedResponse["items"][number]) {
    setMessage(null);
    try {
      const preview = await previewNeedCheckout({
        needRequestId: needId,
        needResponseItemId: responseItem.id,
        locationVariantId: responseItem.locationVariantId,
        quantity: 1,
        fulfillmentMethod: addressId ? "SELLER_DELIVERY" : "PICKUP",
        addressId: addressId || undefined,
        paymentMethod: "COD",
      });
      const order = await commitNeedCheckout(preview.checkoutToken);
      setMessage(`Đã tạo đơn ${order.orderCode}.`);
    } catch (cause) {
      setMessage(cause instanceof Error ? cause.message : "Không thể tạo đơn từ phản hồi này.");
    }
  }

  return (
    <section>
      <h2>Phản hồi từ người bán</h2>
      <label>
        Saved address ID cho giao hàng (để trống nếu nhận tại cửa hàng)
        <input value={addressId} onChange={(event) => setAddressId(event.target.value)} />
      </label>
      {message && <p role="status">{message}</p>}
      <div style={{ display: "grid", gap: 12, marginTop: 12 }}>
        {responses.map((response) => (
          <article key={response.id} style={{ border: "1px solid #ddd", padding: 16, borderRadius: 8 }}>
            <strong>{response.merchantName || "Người bán"}</strong>
            <p>{response.merchantLocationName || response.merchantLocationId}{response.distanceMeters != null ? ` · ${response.distanceMeters} m` : ""}</p>
            {response.note && <p>{response.note}</p>}
            <ul>
              {response.items.map((item) => {
                const price = item.currentPrice || item.priceAtResponse;
                return (
                  <li key={item.id}>
                    {price ? `${price.amount.toLocaleString("vi-VN")} ${price.currency}` : "Liên hệ giá"}
                    {item.actionable ? (
                      <button type="button" onClick={() => checkout(item)} style={{ marginLeft: 8 }}>Chọn & đặt hàng</button>
                    ) : <span> · hiện không khả dụng</span>}
                  </li>
                );
              })}
            </ul>
          </article>
        ))}
        {!responses.length && <p>Chưa có phản hồi phù hợp.</p>}
      </div>
    </section>
  );
}
