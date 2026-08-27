import { AdminSettingsScreen } from "@/features/admin/admin-settings-screen";

export default async function AdminSettingsPage({ searchParams }: { searchParams: Promise<{ section?: string | string[] }> }) {
  const values = await searchParams;
  const section = Array.isArray(values.section) ? values.section[0] : values.section;
  return <AdminSettingsScreen section={section} />;
}
