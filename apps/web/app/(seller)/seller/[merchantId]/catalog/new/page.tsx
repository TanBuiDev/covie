import Link from "next/link";
import { ArrowLeft } from "@phosphor-icons/react/dist/ssr";
import { CatalogCreateForm } from "@/features/catalog/catalog-create-form";
import { listCategoriesForServer } from "@/features/catalog/catalog-server-api";
import { listLocationsForServer } from "@/features/merchant/merchant-server-api";

export default async function NewCatalogPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const [categories, locations] = await Promise.all([
    listCategoriesForServer(),
    listLocationsForServer(merchantId),
  ]);

  return (
    <section className="seller-stack">
      <p className="seller-desktop-only">
        <Link className="seller-back-link" href={`/seller/${encodeURIComponent(merchantId)}/catalog`}>
          <ArrowLeft size={16} weight="bold" aria-hidden="true" />
          <span>Quay lại danh mục</span>
        </Link>
      </p>
      <CatalogCreateForm merchantId={merchantId} categories={categories} locations={locations}
        socialAuthoringEnabled={process.env.COVIE_SOCIAL_V2_CAPTION_AUTHORING_ENABLED === "true"} />
    </section>
  );
}
