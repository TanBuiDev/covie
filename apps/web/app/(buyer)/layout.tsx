import type { ReactNode } from "react";
import { ConsumerShell } from "@/components/layout/consumer-shell";

export default function BuyerLayout({ children }: { children: ReactNode }) {
  return <ConsumerShell>{children}</ConsumerShell>;
}
