"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { setStock } from "./inventory-api";

type Item = components["schemas"]["InventoryItemResponse"];

export function InventoryTable({ merchantId, items }: { merchantId: string; items: Item[] }) {
  const [message, setMessage] = useState<string>();
  async function save(item: Item, form: HTMLFormElement) {
    const stock = Number(new FormData(form).get("stock"));
    if (!item.locationVariantId || !Number.isSafeInteger(stock) || stock < 0) { setMessage("Tồn kho phải là số nguyên không âm."); return; }
    try { await setStock(merchantId, item.locationVariantId, stock); window.location.reload(); }
    catch { setMessage("Không thể cập nhật tồn kho."); }
  }
  return <section>{message && <p role="alert">{message}</p>}<div className="inventory-table" role="table"><div role="row" className="inventory-head"><span>Sản phẩm</span><span>Chế độ</span><span>Tồn kho</span><span>Đã giữ</span><span>Có thể bán</span><span /></div>
    {items.map((item) => <form role="row" key={item.locationVariantId} onSubmit={(event) => { event.preventDefault(); save(item, event.currentTarget); }}>
      <span>{item.itemName} · {item.variantName}</span><span>{item.mode}</span><input name="stock" type="number" min="0" defaultValue={item.stockOnHand ?? 0} disabled={item.mode === "UNTRACKED"} aria-label={`Tồn kho ${item.itemName}`} />
      <output aria-label={`Đã giữ ${item.itemName}`}>{item.reservedQuantity ?? 0}</output><output aria-label={`Có thể bán ${item.itemName}`}>{item.availableQuantity ?? 0}</output><button type="submit" disabled={item.mode === "UNTRACKED"}>Lưu</button>
    </form>)}</div></section>;
}
