import type { ReactNode } from "react";

export function WarningBanner({ children }: { children: ReactNode }) {
  return <aside className="warning-banner" role="status">{children}</aside>;
}
