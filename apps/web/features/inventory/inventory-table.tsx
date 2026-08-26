"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useRef, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { setStock } from "./inventory-api";

type Item = components["schemas"]["InventoryItemResponse"];

export function InventoryTable({ merchantId, items: initialItems }: { merchantId: string; items: Item[] }) {
  const [items, setItems] = useState(initialItems);
  const [pendingId, setPendingId] = useState<string>();
  const [messages, setMessages] = useState<Record<string, string | undefined>>({});
  const intentKeys = useRef(new Map<string, string>());

  async function save(item: Item, event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const locationVariantId = item.locationVariantId;
    if (!locationVariantId || pendingId === locationVariantId) return;

    const form = new FormData(event.currentTarget);
    const stock = Number(form.get("stock"));
    if (!Number.isSafeInteger(stock) || stock < 0) {
      setMessages((current) => ({ ...current, [locationVariantId]: "Tồn kho phải là số nguyên không âm." }));
      return;
    }

    const key = intentKeys.current.get(locationVariantId) ?? crypto.randomUUID();
    intentKeys.current.set(locationVariantId, key);
    setPendingId(locationVariantId);
    setMessages((current) => ({ ...current, [locationVariantId]: undefined }));

    try {
      const result = await setStock(
        merchantId,
        locationVariantId,
        stock,
        key,
        String(form.get("note") ?? ""),
      );
      setItems((current) => current.map((currentItem) => currentItem.locationVariantId === locationVariantId ? {
        ...currentItem,
        stockOnHand: result.stockOnHand,
        reservedQuantity: result.reservedQuantity,
        availableQuantity: result.availableQuantity,
        version: result.version,
      } : currentItem));
      intentKeys.current.delete(locationVariantId);
      setMessages((current) => ({ ...current, [locationVariantId]: "Đã cập nhật tồn kho từ kết quả backend." }));
    } catch (error) {
      if (error instanceof CovieApiError && error.code === "INVENTORY_BELOW_RESERVED_QUANTITY") {
        setMessages((current) => ({
          ...current,
          [locationVariantId]: `Không thể đặt tồn kho thấp hơn số lượng đã giữ (${item.reservedQuantity ?? 0}). Hãy nhập giá trị hợp lệ cao hơn rồi thử lại.`,
        }));
      } else {
        setMessages((current) => ({
          ...current,
          [locationVariantId]: error instanceof Error ? error.message : "Không thể cập nhật tồn kho. Hãy thử lại.",
        }));
      }
    } finally {
      setPendingId(undefined);
    }
  }

  return (
    <ul className="seller-inventory-list" aria-label="Tồn kho theo điểm bán">
      {items.map((item) => {
        const id = item.locationVariantId;
        if (!id) return null;
        const tracked = item.mode === "TRACKED";
        const pending = pendingId === id;
        return (
          <li className="seller-inventory-card" key={id}>
            <header>
              <div>
                <small>{item.locationName ?? "Điểm bán"}</small>
                <strong>{item.itemName ?? "Sản phẩm"}</strong>
                {item.variantName ? <small>{item.variantName}</small> : null}
              </div>
              <span className="status-chip">{tracked ? "Theo dõi tồn kho" : "Không theo dõi"}</span>
            </header>

            <dl className="seller-inventory-metrics">
              <div><dt>Tồn thực tế</dt><dd>{item.stockOnHand ?? "—"}</dd></div>
              <div><dt>Đã giữ</dt><dd>{item.reservedQuantity ?? "—"}</dd></div>
              <div><dt>Có thể bán</dt><dd>{item.availableQuantity ?? "—"}</dd></div>
            </dl>

            {tracked ? (
              <form onSubmit={(event) => void save(item, event)}>
                <label>
                  Đặt tồn thực tế
                  <input
                    name="stock"
                    type="number"
                    inputMode="numeric"
                    min={item.reservedQuantity ?? 0}
                    step="1"
                    defaultValue={item.stockOnHand ?? 0}
                    disabled={pending}
                    onChange={() => intentKeys.current.delete(id)}
                    aria-describedby={`inventory-help-${id}`}
                    required
                  />
                </label>
                <label>
                  Ghi chú điều chỉnh
                  <input name="note" maxLength={500} disabled={pending} onChange={() => intentKeys.current.delete(id)} />
                </label>
                <p className="seller-muted" id={`inventory-help-${id}`}>Backend giữ quyền xác nhận số lượng reserved và available; UI không cập nhật lạc quan.</p>
                {messages[id] ? <p role={messages[id]?.startsWith("Đã") ? "status" : "alert"}>{messages[id]}</p> : null}
                <button type="submit" disabled={pending}>{pending ? "Đang lưu…" : "Lưu tồn kho"}</button>
              </form>
            ) : (
              <p className="seller-muted">Offer này ở chế độ UNTRACKED nên không có thao tác stock.</p>
            )}
          </li>
        );
      })}
    </ul>
  );
}
