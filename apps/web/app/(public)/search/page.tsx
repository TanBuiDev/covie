import { SearchResults, type SearchInitialFilters } from "@/features/discovery/search-results";
import { AdDiscoveryLayout } from "@/features/ads/ad-discovery-layout";

type SearchParams = Record<string, string | string[] | undefined>;

const SORT_VALUES = new Set(["RELEVANCE", "DISTANCE", "PRICE_ASC", "PRICE_DESC", "NEWEST"]);
const SHIPPING_VALUES = new Set(["FREE", "FIXED", "NEGOTIATED", "CONDITIONAL"]);

function priceValue(value: string | string[] | undefined): number | undefined {
  const raw = first(value);
  if (raw == null || raw.trim() === "") return undefined;
  const amount = Number(raw);
  return Number.isSafeInteger(amount) && amount >= 0 ? amount : undefined;
}

function first(value: string | string[] | undefined): string | undefined {
  return Array.isArray(value) ? value[0] : value;
}

export default async function SearchPage({ searchParams }: { searchParams: Promise<SearchParams> }) {
  const params = await searchParams;
  const type = first(params.type);
  const sort = first(params.sort);
  const initial: SearchInitialFilters = {
    q: first(params.q),
    type: type === "PRODUCT" || type === "SERVICE" ? type : "",
    categoryId: first(params.categoryId),
    sort: sort && SORT_VALUES.has(sort) ? sort as SearchInitialFilters["sort"] : "RELEVANCE",
    listingKind: first(params.listingKind) === "RESALE" ? "RESALE" : "",
    radiusMeters: Number(first(params.radiusMeters)) || undefined,
    openNow: first(params.openNow) === "true",
    minPrice: priceValue(params.minPrice),
    maxPrice: priceValue(params.maxPrice),
    pickup: first(params.pickup) === "true",
    canServe: first(params.canServe) === "true",
    shipping: SHIPPING_VALUES.has(first(params.shipping) ?? "") ? first(params.shipping) : undefined,
  };
  return <AdDiscoveryLayout><SearchResults initial={initial} /></AdDiscoveryLayout>;
}
