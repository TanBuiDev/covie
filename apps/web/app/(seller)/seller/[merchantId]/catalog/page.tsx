import { CatalogManagementList } from "@/features/catalog/catalog-management-list";
import { listCatalogItemsForServer } from "@/features/catalog/catalog-server-api";

export default async function CatalogPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const items = await listCatalogItemsForServer(merchantId);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Catalog</p>
        <h1>Danh mục</h1>
        <p>Sản phẩm, dịch vụ và hàng thanh lý của cửa hàng.</p>
      </header>
      <CatalogManagementList merchantId={merchantId} items={items} />
    </section>
  );
}
