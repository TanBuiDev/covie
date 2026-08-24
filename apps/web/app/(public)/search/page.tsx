import { SearchResults, type SearchInitialFilters } from "@/features/discovery/search-results";

type SearchParams = Record<string, string | string[] | undefined>;

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
    sort: sort as SearchInitialFilters["sort"],
  };
  return <SearchResults initial={initial} />;
}
