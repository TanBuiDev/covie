import Link from "next/link";
import type { components } from "@covie/api-client";
import { locationToSearchParams, type DiscoveryLocation } from "@/features/location/location-context";

type CatalogItem = components["schemas"]["CatalogItemSummaryResponse"];

function price(item: CatalogItem) {
  if (!item.priceFrom?.amount) return "Giá liên hệ";
  return `${new Intl.NumberFormat("vi-VN").format(item.priceFrom.amount)} ${item.priceFrom.currency ?? "VND"}`;
}

export function CatalogCard({ item, location }: { item: CatalogItem; location?: DiscoveryLocation }) {
  const query = locationToSearchParams(location).toString();
  const href = `/items/${item.id}${query ? `?${query}` : ""}`;
  return <article>
    {item.primaryMedia?.url && <img src={item.primaryMedia.url} alt={item.name ?? "Hình ảnh sản phẩm"} width={240} height={180} />}
    <h2><Link href={href}>{item.name ?? "Sản phẩm"}</Link></h2>
    <p>{item.merchant?.name ?? "Người bán"}</p>
    <p>{item.resolvedLocation?.name ?? "Chưa xác định địa điểm"}</p>
    {item.resolvedLocation?.addressText && <p>{item.resolvedLocation.addressText}</p>}
    {item.resolvedLocation?.distanceMeters != null && <p>{Math.round(item.resolvedLocation.distanceMeters)} m</p>}
    <p>{price(item)}</p>
  </article>;
}
