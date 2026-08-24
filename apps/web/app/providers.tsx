"use client";

import type { ReactNode } from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { BrowseLocationProvider } from "@/features/location/browse-location-provider";
import { RegisterServiceWorker } from "@/lib/pwa/register-service-worker";

export function Providers({ children }: Readonly<{ children: ReactNode }>) {
  const [queryClient] = useState(() => new QueryClient({
    defaultOptions: { queries: { staleTime: 30_000, retry: 1, refetchOnWindowFocus: false } },
  }));
  return (
    <QueryClientProvider client={queryClient}>
      <BrowseLocationProvider>
        <RegisterServiceWorker />
        {children}
      </BrowseLocationProvider>
    </QueryClientProvider>
  );
}
