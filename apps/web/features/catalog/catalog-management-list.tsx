"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useMemo, useState } from "react";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";
import { ArrowRight, MagnifyingGlass, Package, PlusCircle, Tag, Wrench } from "@phosphor-icons/react";

type CatalogSummary = components["schemas"]["CatalogItemManagementSummaryResponse"];
type CatalogView = "PRODUCT" | "SERVICE" | "RESALE";

function listingTypeLabel(item: CatalogSummary) {
  if (item.type === "SERVICE") return "Dịch vụ";
  if (item.listingKind === "RESALE") return "Thanh lý";
  return "Sản phẩm";
}

function listingStatus(status?: CatalogSummary["listingStatus"]): { label: string; tone: StatusTone } {
  switch (status) {
    case "DRAFT": return { label: "Bản nháp", tone: "neutral" };
    case "ACTIVE": return { label: "Đang hiển thị", tone: "success" };
    case "PAUSED": return { label: "Tạm dừng", tone: "warning" };
    case "DELETED": return { label: "Đã xóa", tone: "neutral" };
    default: return { label: "Trạng thái đã được cập nhật", tone: "neutral" };
  }
}

function matchesView(item: CatalogSummary, view: CatalogView) {
  if (view === "SERVICE") return item.type === "SERVICE";
  if (view === "RESALE") return item.listingKind === "RESALE";
  return item.type === "PRODUCT" && item.listingKind !== "RESALE";
}

function ItemTypeIcon({ item }: { item: CatalogSummary }) {
  if (item.type === "SERVICE") return <Wrench size={24} weight="duotone" />;
  if (item.listingKind === "RESALE") return <Tag size={24} weight="duotone" />;
  return <Package size={24} weight="duotone" />;
}

export function CatalogManagementList({ merchantId, items }: { merchantId: string; items: CatalogSummary[] }) {
  const [view, setView] = useState<CatalogView>("PRODUCT");
  const [searchQuery, setSearchQuery] = useState("");

  const filteredByType = useMemo(() => items.filter((item) => matchesView(item, view)), [items, view]);
  const filtered = useMemo(() => {
    const q = searchQuery.trim().toLowerCase();
    if (!q) return filteredByType;
    return filteredByType.filter((item) => (item.name ?? "").toLowerCase().includes(q));
  }, [filteredByType, searchQuery]);

  const createHref = `/seller/${encodeURIComponent(merchantId)}/catalog/new`;

  return (
    <section className="seller-catalog-management" aria-label="Catalog của cửa hàng">
      {/* TOOLBAR */}
      <div className="seller-catalog-toolbar">
        <div className="seller-segmented" role="group" aria-label="Lọc loại nội dung">
          {([
            ["PRODUCT", "Sản phẩm"],
            ["SERVICE", "Dịch vụ"],
            ["RESALE", "Thanh lý"],
          ] as const).map(([value, label]) => (
            <button
              key={value}
              type="button"
              aria-pressed={view === value}
              onClick={() => setView(value)}
            >
              {label}
            </button>
          ))}
        </div>

        <Link className="seller-button seller-catalog-add" href={createHref}>
          <PlusCircle size={18} weight="bold" aria-hidden="true" />
          <span>Thêm nội dung</span>
        </Link>
      </div>

      {/* SEARCH BAR (WHEN ITEMS EXIST) */}
      {filteredByType.length > 0 ? (
        <div className="seller-catalog-search">
          <MagnifyingGlass size={18} className="seller-catalog-search__icon" aria-hidden="true" />
          <input
            type="search"
            placeholder="Tìm kiếm theo tên mặt hàng…"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            className="seller-catalog-search__input"
            aria-label="Tìm kiếm mặt hàng"
          />
        </div>
      ) : null}

      {/* LIST / EMPTY STATES */}
      {!filteredByType.length ? (
        <div className="seller-catalog-empty-wrapper">
          <div className="state-card" aria-live="polite">
            <div className="seller-empty-icon" aria-hidden="true">
              {view === "SERVICE" ? (
                <Wrench size={40} weight="duotone" />
              ) : view === "RESALE" ? (
                <Tag size={40} weight="duotone" />
              ) : (
                <Package size={40} weight="duotone" />
              )}
            </div>
            <h2>{`Chưa có ${view === "PRODUCT" ? "sản phẩm" : view === "SERVICE" ? "dịch vụ" : "nội dung thanh lý"}`}</h2>
            <p>Nội dung thuộc nhóm này sẽ xuất hiện tại đây sau khi được tạo.</p>
            <div className="state-card__action">
              <Link className="seller-button" href={createHref}>Thêm nội dung</Link>
            </div>
          </div>
        </div>
      ) : !filtered.length ? (
        <div className="seller-catalog-empty-search">
          <p>Không tìm thấy mặt hàng nào khớp với từ khóa &quot;{searchQuery}&quot;.</p>
        </div>
      ) : (
        <ul className="seller-catalog-list">
          {filtered.map((item) => {
            if (!item.id) return null;
            const presentation = listingStatus(item.listingStatus);
            const detailHref = `/seller/${encodeURIComponent(merchantId)}/catalog/${encodeURIComponent(item.id)}`;
            const iconTone = item.type === "SERVICE" ? "purple" : item.listingKind === "RESALE" ? "amber" : "emerald";

            return (
              <li className="seller-catalog-card" key={item.id}>
                <Link className="seller-catalog-card__head" href={detailHref}>
                  <div className={`seller-catalog-thumb seller-catalog-thumb--${iconTone}`} aria-hidden="true">
                    <ItemTypeIcon item={item} />
                  </div>
                  <div className="seller-catalog-card__main">
                    <strong className="seller-catalog-card__name">{item.name ?? "Nội dung catalog"}</strong>
                    <div className="seller-catalog-card__meta-tags">
                      <span className="seller-catalog-card__kind-tag">{listingTypeLabel(item)}</span>
                    </div>
                  </div>
                </Link>
                <div className="seller-catalog-card__footer">
                  <StatusChip label={presentation.label} tone={presentation.tone} />
                  <Link className="seller-catalog-card__action-link" href={detailHref}>
                    <span>Chỉnh sửa</span>
                    <ArrowRight size={13} weight="bold" />
                  </Link>
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
