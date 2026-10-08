import type { CatalogDiscoveryFilters } from "@/features/catalog/catalog-api";
import type { BrowseLocation } from "@/features/location/location-context";
import { toDiscoveryLocation } from "@/features/location/location-context";

export type HomeSectionKey = "nearby" | "services" | "resale";

export type HomeSectionDefinition = {
  key: HomeSectionKey;
  title: string;
  errorMessage: string;
  emptyMessage: string;
  filters: Pick<CatalogDiscoveryFilters, "type" | "listingKind">;
};

export const HOME_SECTIONS: HomeSectionDefinition[] = [
  {
    key: "nearby",
    title: "Gần bạn",
    errorMessage: "Không thể tải sản phẩm gần bạn.",
    emptyMessage: "Chưa có sản phẩm nào gần bạn.",
    filters: { type: "PRODUCT", listingKind: "STANDARD" },
  },
  {
    key: "services",
    title: "Dịch vụ gần bạn",
    errorMessage: "Không thể tải dịch vụ gần bạn.",
    emptyMessage: "Chưa có dịch vụ nào gần bạn.",
    filters: { type: "SERVICE", listingKind: "STANDARD" },
  },
  {
    key: "resale",
    title: "Thanh lý gần bạn",
    errorMessage: "Không thể tải đồ thanh lý gần bạn.",
    emptyMessage: "Chưa có đồ thanh lý nào gần bạn.",
    filters: { type: "PRODUCT", listingKind: "RESALE" },
  },
];

export function homeDiscoveryFilters(section: HomeSectionDefinition, location: BrowseLocation): CatalogDiscoveryFilters {
  return {
    ...section.filters,
    sort: "DISTANCE",
    limit: 4,
    location: toDiscoveryLocation(location),
  };
}
