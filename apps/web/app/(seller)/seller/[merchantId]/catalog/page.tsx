import Link from "next/link";
import { CatalogManagementList } from "@/features/catalog/catalog-management-list";
import { listCatalogItemsForServer } from "@/features/catalog/catalog-server-api";

export default async function CatalogPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const items = await listCatalogItemsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-section__heading">
        <div>
          <p className="eyebrow">Seller Center / Catalog</p>
          <h1>Nội dung đang bán</h1>
          <p>Sản phẩm, dịch vụ và hàng bán lại của cửa hàng.</p>
        </div>
        <Link className="seller-button" href={`/seller/${encodeURIComponent(merchantId)}/catalog/new`}>Tạo mới</Link>
      </header>
      <CatalogManagementList merchantId={merchantId} items={items} />
    </section>
  );
}
