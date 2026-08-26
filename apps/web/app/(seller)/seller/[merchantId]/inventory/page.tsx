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
      <header className="surface-header">
        <p className="eyebrow">Seller Center / Inventory</p>
        <h1>Tồn kho</h1>
        <p>Chỉ offer PRODUCT ở chế độ TRACKED mới cho phép chỉnh số lượng. Reserved và available luôn lấy từ backend.</p>
      </header>
      {data.length ? (
        <InventoryTable merchantId={merchantId} items={data} />
      ) : (
        <EmptyState title="Chưa có tồn kho" description="Tồn kho xuất hiện khi catalog có offer sản phẩm tại điểm bán." />
      )}
    </section>
  );
}
