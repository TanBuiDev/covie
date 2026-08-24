import type { ReactNode } from "react";
import { AppHeader } from "./app-header";
import { ConsumerBottomNav } from "./consumer-bottom-nav";

export function ConsumerShell({ children }: { children: ReactNode }) {
  return (
    <div className="consumer-shell">
      <AppHeader />
      <main className="app-main">{children}</main>
      <ConsumerBottomNav />
    </div>
  );
}
