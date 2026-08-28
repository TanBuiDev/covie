import { CatalogEditForm } from "@/features/catalog/catalog-edit-form";
import { getManagedCatalogItemForServer } from "@/features/catalog/catalog-server-api";

export default async function CatalogEditPage({
  params,
}: {
  params: Promise<{ merchantId: string; itemId: string }>;
}) {
  const { merchantId, itemId } = await params;
  const item = await getManagedCatalogItemForServer(merchantId, itemId);
  return <CatalogEditForm merchantId={merchantId} item={item} />;
}
