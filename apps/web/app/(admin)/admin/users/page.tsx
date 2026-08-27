import { AdminEntityTable } from "@/features/admin/admin-entity-table";

export default function UsersPage() {
  return <AdminEntityTable type="users" title="Người dùng" columns={["id", "displayName", "status", "updatedAt"]} />;
}
