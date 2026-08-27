import { SearchResults, type SearchInitialFilters } from "@/features/discovery/search-results";

type SearchParams = Record<string, string | string[] | undefined>;

const SORT_VALUES = new Set(["RELEVANCE", "DISTANCE", "PRICE_ASC", "PRICE_DESC", "NEWEST"]);

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
  };
  return <SearchResults initial={initial} />;
}
