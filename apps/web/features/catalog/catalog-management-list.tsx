import type { components } from "@covie/api-client";
import Link from "next/link";
import { EmptyState } from "@/components/ui/empty-state";
import { StatusChip } from "@/components/ui/status-chip";

type CatalogSummary = components["schemas"]["CatalogItemManagementSummaryResponse"];

function listingTypeLabel(item: CatalogSummary) {
  if (item.type === "SERVICE") return "Dịch vụ";
  if (item.listingKind === "RESALE") return "Bán lại";
  return "Sản phẩm";
}

export function CatalogManagementList({ merchantId, items }: { merchantId: string; items: CatalogSummary[] }) {
  if (!items.length) {
    return (
      <EmptyState
        title="Chưa có nội dung bán"
        description="Tạo sản phẩm, dịch vụ hoặc sản phẩm bán lại đầu tiên cho cửa hàng."
        action={<Link className="seller-button" href={`/seller/${encodeURIComponent(merchantId)}/catalog/new`}>Tạo nội dung</Link>}
      />
    );
  }

  return (
    <ul className="seller-row-list" aria-label="Catalog của cửa hàng">
      {items.map((item) => item.id ? (
        <li className="seller-row seller-catalog-row" key={item.id}>
          <div>
            <small>{listingTypeLabel(item)}</small>
            <strong>{item.name ?? "Nội dung catalog"}</strong>
            <small>Mã phiên bản: {item.version ?? "—"}</small>
          </div>
          <StatusChip status={item.listingStatus} />
        </li>
      ) : null)}
    </ul>
  );
}
