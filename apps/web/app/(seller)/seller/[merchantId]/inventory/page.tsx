import { InventoryTable } from "@/features/inventory/inventory-table";
import { createServerReadClient } from "@/lib/api/server-api-client";
import { requireUser } from "@/lib/auth/require-user";

export default async function InventoryPage({ params }: { params: Promise<{ merchantId: string }> }) {
  await requireUser();
  const { merchantId } = await params;
  const { data, response } = await (await createServerReadClient()).GET("/api/v1/merchants/{merchantId}/inventory", { params: { path: { merchantId } } });
  if (!response.ok || !data) throw new Error("Không thể tải tồn kho.");
  return <main className="seller-surface"><header className="surface-header"><p className="eyebrow">CoVie / Seller</p><h1>Tồn kho</h1></header><InventoryTable merchantId={merchantId} items={data} /></main>;
}
