import { CatalogDetail } from "@/features/catalog/catalog-detail";
import { getCatalogItem } from "@/features/catalog/catalog-api";
import { locationFromSearchParams } from "@/features/location/location-context";

export default async function CatalogItemPage({ params, searchParams }: { params: Promise<{ itemId: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const [{ itemId }, query] = await Promise.all([params, searchParams]);
  const item = await getCatalogItem(itemId, locationFromSearchParams(query));
  return <main><CatalogDetail item={item} /></main>;
}
