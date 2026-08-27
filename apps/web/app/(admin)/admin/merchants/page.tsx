import { AdminEntityTable } from "@/features/admin/admin-entity-table";

export default function MerchantsPage() {
  return <AdminEntityTable type="merchants" title="Người bán / Nhà cung cấp" columns={["id", "name", "status", "moderationStatus", "updatedAt"]} />;
}
