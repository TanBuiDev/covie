import Link from "next/link";
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
      <p><Link href={`/seller/${encodeURIComponent(merchantId)}/catalog`}>Quay lại catalog</Link></p>
      <CatalogCreateForm merchantId={merchantId} categories={categories} locations={locations} />
    </section>
  );
}
