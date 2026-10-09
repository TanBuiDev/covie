"use client";

import type { components } from "@covie/api-client";
import { FormEvent, useMemo, useRef, useState } from "react";
import { CheckCircle, MagnifyingGlass, Package, Storefront, WarningCircle } from "@phosphor-icons/react";
import { CovieApiError } from "@/lib/api/api-error";
import { setStock } from "./inventory-api";

type Item = components["schemas"]["InventoryItemResponse"];

export function InventoryTable({ merchantId, items: initialItems }: { merchantId: string; items: Item[] }) {
  const [items, setItems] = useState(initialItems);
  const [pendingId, setPendingId] = useState<string>();
  const [messages, setMessages] = useState<Record<string, string | undefined>>({});
  const [searchQuery, setSearchQuery] = useState("");
  const intentKeys = useRef(new Map<string, string>());

  const totalTracked = useMemo(() => items.filter((item) => item.mode === "TRACKED").length, [items]);
  const totalAvailable = useMemo(
    () => items.reduce((sum, item) => sum + (item.mode === "TRACKED" ? (item.availableQuantity ?? 0) : 0), 0),
    [items]
  );
  const lowStockCount = useMemo(
    () => items.filter((item) => item.mode === "TRACKED" && (item.availableQuantity ?? 0) <= 5).length,
    [items]
  );

  const filteredItems = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return items;
    return items.filter(
      (item) =>
        (item.itemName ?? "").toLowerCase().includes(q) ||
        (item.locationName ?? "").toLowerCase().includes(q) ||
        (item.variantName ?? "").toLowerCase().includes(q)
    );
  }, [items, searchQuery]);

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
      setMessages((current) => ({ ...current, [locationVariantId]: "Đã cập nhật tồn kho." }));
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
    <div className="seller-inventory-wrapper">
      {/* KPI METRICS OVERVIEW */}
      <div className="seller-inventory-overview">
        <div className="seller-inventory-kpi-card">
          <div className="seller-inventory-kpi-icon seller-inventory-kpi-icon--emerald" aria-hidden="true">
            <Package size={22} weight="duotone" />
          </div>
          <div className="seller-inventory-kpi-content">
            <span className="seller-inventory-kpi-label">Mặt hàng theo dõi</span>
            <strong className="seller-inventory-kpi-value">{totalTracked} <small>/ {items.length} SKU</small></strong>
          </div>
        </div>

        <div className="seller-inventory-kpi-card">
          <div className="seller-inventory-kpi-icon seller-inventory-kpi-icon--teal" aria-hidden="true">
            <CheckCircle size={22} weight="duotone" />
          </div>
          <div className="seller-inventory-kpi-content">
            <span className="seller-inventory-kpi-label">Sẵn sàng bán</span>
            <strong className="seller-inventory-kpi-value">{totalAvailable} <small>đơn vị</small></strong>
          </div>
        </div>

        <div className="seller-inventory-kpi-card">
          <div className="seller-inventory-kpi-icon seller-inventory-kpi-icon--amber" aria-hidden="true">
            <WarningCircle size={22} weight="duotone" />
          </div>
          <div className="seller-inventory-kpi-content">
            <span className="seller-inventory-kpi-label">Cảnh báo sắp hết (≤ 5)</span>
            <strong className="seller-inventory-kpi-value">{lowStockCount} <small>mặt hàng</small></strong>
          </div>
        </div>
      </div>

      {/* QUICK SEARCH TOOLBAR */}
      {items.length > 0 ? (
        <div className="seller-inventory-toolbar">
          <div className="seller-catalog-search">
            <MagnifyingGlass size={18} className="seller-catalog-search__icon" aria-hidden="true" />
            <input
              type="search"
              placeholder="Tìm kiếm theo tên mặt hàng hoặc điểm bán…"
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="seller-catalog-search__input"
              aria-label="Tìm kiếm tồn kho"
            />
          </div>
        </div>
      ) : null}

      {/* INVENTORY CARD LIST */}
      <ul className="seller-inventory-list" aria-label="Tồn kho theo điểm bán">
        {filteredItems.map((item) => {
          const id = item.locationVariantId;
          if (!id) return null;
          const tracked = item.mode === "TRACKED";
          const pending = pendingId === id;
          return (
            <li className="seller-inventory-card" key={id}>
              <header className="seller-inventory-card__header">
                <div className="seller-inventory-card__info">
                  <div className="seller-inventory-card__location-badge">
                    <Storefront size={13} weight="bold" aria-hidden="true" />
                    <small>{item.locationName ?? "Điểm bán"}</small>
                  </div>
                  <strong className="seller-inventory-card__title">{item.itemName ?? "Sản phẩm"}</strong>
                  {item.variantName ? <span className="seller-inventory-card__variant-tag">{item.variantName}</span> : null}
                </div>
                <span className={`status-chip ${tracked ? "status-chip--success" : "status-chip--neutral"}`}>
                  {tracked ? "Theo dõi tồn kho" : "Không theo dõi"}
                </span>
              </header>

              <dl className="seller-inventory-metrics">
                <div className="seller-inventory-metric-box seller-inventory-metric-box--on-hand">
                  <dt>Tồn thực tế</dt>
                  <dd>{item.stockOnHand ?? "—"}</dd>
                </div>
                <div className="seller-inventory-metric-box seller-inventory-metric-box--reserved">
                  <dt>Đã giữ</dt>
                  <dd>{item.reservedQuantity ?? "—"}</dd>
                </div>
                <div className="seller-inventory-metric-box seller-inventory-metric-box--available">
                  <dt>Có thể bán</dt>
                  <dd>{item.availableQuantity ?? "—"}</dd>
                </div>
              </dl>

              {tracked ? (
                <form className="seller-inventory-form" onSubmit={(event) => void save(item, event)}>
                  <div className="seller-inventory-stock-field">
                    <label className="seller-inventory-form__field">
                      Đặt tồn thực tế
                      <input
                        id={`inventory-stock-${id}`}
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
                        className="seller-inventory-input"
                      />
                    </label>
                    <div className="seller-inventory-quick-steppers" role="group" aria-label="Tăng nhanh số lượng">
                      <button
                        type="button"
                        disabled={pending}
                        className="seller-quick-step-btn"
                        title="Tăng thêm 1 đơn vị"
                        onClick={() => {
                          const el = document.getElementById(`inventory-stock-${id}`) as HTMLInputElement | null;
                          if (el) {
                            const cur = Number(el.value || 0);
                            el.value = String(cur + 1);
                            intentKeys.current.delete(id);
                          }
                        }}
                      >
                        +1
                      </button>
                      <button
                        type="button"
                        disabled={pending}
                        className="seller-quick-step-btn"
                        title="Tăng thêm 5 đơn vị"
                        onClick={() => {
                          const el = document.getElementById(`inventory-stock-${id}`) as HTMLInputElement | null;
                          if (el) {
                            const cur = Number(el.value || 0);
                            el.value = String(cur + 5);
                            intentKeys.current.delete(id);
                          }
                        }}
                      >
                        +5
                      </button>
                      <button
                        type="button"
                        disabled={pending}
                        className="seller-quick-step-btn"
                        title="Tăng thêm 10 đơn vị"
                        onClick={() => {
                          const el = document.getElementById(`inventory-stock-${id}`) as HTMLInputElement | null;
                          if (el) {
                            const cur = Number(el.value || 0);
                            el.value = String(cur + 10);
                            intentKeys.current.delete(id);
                          }
                        }}
                      >
                        +10
                      </button>
                    </div>
                  </div>
                  <label className="seller-inventory-form__field">
                    Ghi chú điều chỉnh
                    <input
                      name="note"
                      maxLength={500}
                      disabled={pending}
                      placeholder="Ghi chú lý do thay đổi tồn kho…"
                      onChange={() => intentKeys.current.delete(id)}
                      className="seller-inventory-input"
                    />
                  </label>
                  <p className="seller-muted" id={`inventory-help-${id}`}>Số lượng đã giữ và có thể bán sẽ được cập nhật theo dữ liệu mới nhất sau khi lưu.</p>
                  {messages[id] ? (
                    <p
                      role={messages[id]?.startsWith("Đã") ? "status" : "alert"}
                      className={`seller-inventory-msg ${messages[id]?.startsWith("Đã") ? "seller-inventory-msg--success" : "seller-inventory-msg--error"}`}
                    >
                      {messages[id]}
                    </p>
                  ) : null}
                  <button type="submit" disabled={pending} className="seller-button seller-button--primary seller-inventory-save-btn">
                    {pending ? "Đang lưu…" : "Lưu tồn kho"}
                  </button>
                </form>
              ) : (
                <p className="seller-muted">Nội dung này không theo dõi số lượng tồn kho nên không cần cập nhật thủ công.</p>
              )}
            </li>
          );
        })}
      </ul>
    </div>
  );
}
