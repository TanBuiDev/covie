import Link from "next/link";
import { ArrowLeft } from "@phosphor-icons/react/dist/ssr";
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
  return (
    <section className="seller-stack">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={`/seller/${encodeURIComponent(merchantId)}/catalog`}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại danh mục</span>
        </Link>
      </p>
      <CatalogEditForm key={revisionKey}
        merchantId={merchantId} item={detail.item} etag={detail.etag} categories={categories} />
    </section>
  );
}
