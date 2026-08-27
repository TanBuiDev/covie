import { EmptyState } from "@/components/ui/empty-state";
import { InventoryTable } from "@/features/inventory/inventory-table";
import { readApiError } from "@/lib/api/api-error";
import { createServerReadClient } from "@/lib/api/server-api-client";

export default async function InventoryPage({ params }: { params: Promise<{ merchantId: string }> }) {
  const { merchantId } = await params;
  const { data, response } = await (await createServerReadClient()).GET("/api/v1/merchants/{merchantId}/inventory", {
    params: { path: { merchantId } },
  });
  if (!response.ok || !data) throw await readApiError(response);

  return (
    <section className="seller-stack">
      <header className="surface-header seller-desktop-heading">
        <p className="eyebrow">Seller Center / Tồn kho</p>
        <h1>Tồn kho</h1>
        <p>Cập nhật số lượng cho các sản phẩm đang theo dõi tồn kho tại từng điểm bán.</p>
      </header>
      {data.length ? (
        <InventoryTable merchantId={merchantId} items={data} />
      ) : (
        <EmptyState title="Chưa có tồn kho" description="Tồn kho sẽ xuất hiện khi sản phẩm được bán tại điểm bán và bật theo dõi số lượng." />
      )}
    </section>
  );
}
