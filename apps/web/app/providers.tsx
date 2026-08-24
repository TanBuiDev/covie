"use client";

import type { ReactNode } from "react";
import { RegisterServiceWorker } from "@/lib/pwa/register-service-worker";

/**
 * Plan 09 application-level client providers.
 * TanStack Query is intentionally added here only after its lockfile-managed dependency is installed.
 */
export function Providers({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <>
      <RegisterServiceWorker />
      {children}
    </>
  );
}
