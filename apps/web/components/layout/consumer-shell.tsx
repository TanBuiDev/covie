import type { ReactNode } from "react";
import { AppFooter } from "./app-footer";
import { AppHeader } from "./app-header";
import { BuyerDesktopHeader } from "./buyer-desktop-header";
import { ConsumerBottomNav } from "./consumer-bottom-nav";

export function ConsumerShell({ children, showHeader = true }: { children: ReactNode; showHeader?: boolean }) {
  return (
    <div className="consumer-shell">
      <BuyerDesktopHeader />
      {showHeader && <AppHeader />}
      <main className="app-main">{children}</main>
      <AppFooter />
      <ConsumerBottomNav />
    </div>
  );
}
