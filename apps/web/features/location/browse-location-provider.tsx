"use client";

import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { clearBrowseLocation, readBrowseLocation, writeBrowseLocation } from "@/lib/location/browse-location-storage";
import type { BrowseLocation } from "./location-context";

export type BrowseLocationContextValue = {
  location: BrowseLocation | null;
  hydrated: boolean;
  setLocation: (location: BrowseLocation) => void;
  clearLocation: () => void;
};

export const BrowseLocationContext = createContext<BrowseLocationContextValue | null>(null);

export function BrowseLocationProvider({ children }: Readonly<{ children: ReactNode }>) {
  const [location, setLocationState] = useState<BrowseLocation | null>(null);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    const timer = window.setTimeout(() => {
      setLocationState(readBrowseLocation(window.localStorage));
      setHydrated(true);
    }, 0);
    return () => window.clearTimeout(timer);
  }, []);

  const setLocation = useCallback((next: BrowseLocation) => {
    writeBrowseLocation(window.localStorage, next);
    setLocationState(next);
  }, []);

  const clearLocation = useCallback(() => {
    clearBrowseLocation(window.localStorage);
    setLocationState(null);
  }, []);

  const value = useMemo(() => ({ location, hydrated, setLocation, clearLocation }), [location, hydrated, setLocation, clearLocation]);
  return <BrowseLocationContext.Provider value={value}>{children}</BrowseLocationContext.Provider>;
}
