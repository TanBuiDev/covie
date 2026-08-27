"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { useMemo, useState } from "react";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip, type StatusTone } from "@/components/ui/status-chip";

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

export function CatalogManagementList({ merchantId, items }: { merchantId: string; items: CatalogSummary[] }) {
  const [view, setView] = useState<CatalogView>("PRODUCT");
  const filtered = useMemo(() => items.filter((item) => matchesView(item, view)), [items, view]);
  const createHref = `/seller/${encodeURIComponent(merchantId)}/catalog/new`;

  return (
    <section className="seller-catalog-management" aria-label="Catalog của cửa hàng">
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
      <Link className="seller-button seller-catalog-add" href={createHref}>Thêm nội dung</Link>

      {!filtered.length ? (
        <EmptyState
          title={`Chưa có ${view === "PRODUCT" ? "sản phẩm" : view === "SERVICE" ? "dịch vụ" : "nội dung thanh lý"}`}
          description="Nội dung thuộc nhóm này sẽ xuất hiện tại đây sau khi được tạo."
          action={<Link className="seller-button" href={createHref}>Thêm nội dung</Link>}
        />
      ) : (
        <ul className="seller-catalog-list">
          {filtered.map((item) => {
            if (!item.id) return null;
            const presentation = listingStatus(item.listingStatus);
            return (
              <li className="seller-catalog-card" key={item.id}>
                <div className="seller-catalog-card__head">
                  <div className="seller-catalog-placeholder" aria-hidden="true" />
                  <div>
                    <strong>{item.name ?? "Nội dung catalog"}</strong>
                    <small>{listingTypeLabel(item)}</small>
                  </div>
                </div>
                <div className="seller-catalog-card__status">
                  <StatusChip label={presentation.label} tone={presentation.tone} />
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
