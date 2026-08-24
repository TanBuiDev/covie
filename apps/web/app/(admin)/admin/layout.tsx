import type { ReactNode } from "react";
export default function AdminLayout({ children }: { children: ReactNode }) { return <main style={{ maxWidth: 1100, margin: "0 auto", padding: "48px 24px", background: "#faf7f2", minHeight: "100vh" }}>{children}</main>; }
