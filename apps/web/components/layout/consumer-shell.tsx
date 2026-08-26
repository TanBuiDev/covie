import type { ReactNode } from "react";
import { AppHeader } from "./app-header";
import { ConsumerBottomNav } from "./consumer-bottom-nav";

export function ConsumerShell({ children, showHeader = true }: { children: ReactNode; showHeader?: boolean }) {
  return (
    <div className="consumer-shell">
      {showHeader && <AppHeader />}
      <main className="app-main">{children}</main>
      <ConsumerBottomNav />
    </div>
  );
}
