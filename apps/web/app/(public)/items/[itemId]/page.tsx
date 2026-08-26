import { CatalogDetail } from "@/features/catalog/catalog-detail";
import { getCatalogItemForServer } from "@/features/catalog/catalog-server-api";
import { locationFromSearchParams } from "@/features/location/location-context";

export default async function CatalogItemPage({ params, searchParams }: { params: Promise<{ itemId: string }>; searchParams: Promise<Record<string, string | string[] | undefined>> }) {
  const [{ itemId }, query] = await Promise.all([params, searchParams]);
  const item = await getCatalogItemForServer(itemId, locationFromSearchParams(query));
  return <CatalogDetail item={item} />;
}
