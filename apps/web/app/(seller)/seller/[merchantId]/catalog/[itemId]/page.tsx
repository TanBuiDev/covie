import { CatalogEditForm } from "@/features/catalog/catalog-edit-form";
import { getCatalogManagementDetailForServer, listCategoriesForServer } from "@/features/catalog/catalog-server-api";

export default async function CatalogEditPage({ params }: {
  params: Promise<{ merchantId: string; itemId: string }>;
}) {
  const { merchantId, itemId } = await params;
  const detail = await getCatalogManagementDetailForServer(merchantId, itemId);
  const categories = await listCategoriesForServer(detail.item.type);
  const revisionKey = JSON.stringify([
    detail.etag ?? `${itemId}:${detail.item.version ?? "unknown"}`,
    (detail.item.variants ?? []).map((variant) => [variant.id, variant.version]),
    (detail.item.locationOffers ?? []).map((offer) => [offer.id, offer.version]),
  ]);
  return <CatalogEditForm key={revisionKey}
    merchantId={merchantId} item={detail.item} etag={detail.etag} categories={categories} />;
}
