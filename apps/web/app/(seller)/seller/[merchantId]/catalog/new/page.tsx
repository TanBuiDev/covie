import Link from "next/link";
import { CatalogCreateForm } from "@/features/catalog/catalog-create-form";
import { requireUser } from "@/lib/auth/require-user";

export default async function NewCatalogPage({ params }: { params: Promise<{ merchantId: string }> }) {
  await requireUser();
  const { merchantId } = await params;
  return <main>
    <Link href={`/seller/${merchantId}/catalog`}>← Catalog</Link>
    <CatalogCreateForm merchantId={merchantId} />
  </main>;
}
