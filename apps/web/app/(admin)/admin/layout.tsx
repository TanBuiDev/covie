import type { ReactNode } from "react";
import { notFound } from "next/navigation";
import { AdminShell } from "@/features/admin/admin-shell";
import { requireUser } from "@/lib/auth/require-user";
import "./admin.css";

export default async function AdminLayout({ children }: { children: ReactNode }) {
  const user = await requireUser();
  if (!user.globalRoles?.includes("ADMIN")) notFound();
  return <AdminShell user={user}>{children}</AdminShell>;
}
