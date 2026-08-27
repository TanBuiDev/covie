import type { ReactNode } from "react";
import { ConsumerShell } from "@/components/layout/consumer-shell";

export default function PublicLayout({ children }: { children: ReactNode }) {
  return <ConsumerShell showHeader={false}>{children}</ConsumerShell>;
}
