import type { ReactNode } from "react";

export function ActionBar({ children }: { children: ReactNode }) {
  return <div className="action-bar" role="group">{children}</div>;
}
