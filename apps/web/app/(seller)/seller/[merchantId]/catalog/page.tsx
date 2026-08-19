import Link from "next/link";
import { listCatalogItemsForServer } from "@/features/catalog/catalog-server-api";
import { requireUser } from "@/lib/auth/require-user";

export default async function CatalogPage({ params }: { params: Promise<{ merchantId: string }> }) {
  await requireUser();
  const { merchantId } = await params;
  const items = await listCatalogItemsForServer(merchantId);
  return <main>
    <Link href={`/seller/${merchantId}`}>← Cửa hàng</Link>
    <h1>Catalog</h1>
    <Link href={`/seller/${merchantId}/catalog/new`}>Tạo sản phẩm hoặc dịch vụ</Link>
    <ul>{items.map((item) => <li key={item.id}>{item.name} — {item.type} — {item.listingStatus}</li>)}</ul>
  </main>;
}
