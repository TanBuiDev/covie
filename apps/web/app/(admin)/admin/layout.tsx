import type { ReactNode } from "react";
import { redirect } from "next/navigation";
import { requireUser } from "@/lib/auth/require-user";

export default async function AdminLayout({ children }: { children: ReactNode }) {
  const user = await requireUser();
  if (!user.globalRoles?.includes("ADMIN")) redirect("/");
  return <main style={{ maxWidth: 1100, margin: "0 auto", padding: "48px 24px", background: "#faf7f2", minHeight: "100vh" }}>{children}</main>;
}
