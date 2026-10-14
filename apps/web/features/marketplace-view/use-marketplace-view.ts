"use client";

import { useCallback, useEffect, useState } from "react";
import {
  MARKETPLACE_VIEW_STORAGE_KEY,
  resolveMarketplaceViewMode,
  type MarketplaceViewMode,
} from "./marketplace-view-preference";

const CHANGE_EVENT = "covie:marketplace-view-change";

/**
 * Presentation-only preference. Changing modes never changes filters, the
 * discovery query, the cursor, or commerce state.
 */
export function useMarketplaceView(defaultMode: MarketplaceViewMode) {
  const [mode, setMode] = useState<MarketplaceViewMode>(defaultMode);

  useEffect(() => {
    const sync = () => {
      let stored: string | null = null;
      try { stored = window.localStorage.getItem(MARKETPLACE_VIEW_STORAGE_KEY); } catch { /* private browsing */ }
      const explicit = new URLSearchParams(window.location.search).get("view");
      setMode(resolveMarketplaceViewMode(explicit, stored, defaultMode));
    };
    sync();
    window.addEventListener("popstate", sync);
    window.addEventListener("storage", sync);
    window.addEventListener(CHANGE_EVENT, sync);
    return () => {
      window.removeEventListener("popstate", sync);
      window.removeEventListener("storage", sync);
      window.removeEventListener(CHANGE_EVENT, sync);
    };
  }, [defaultMode]);

  const changeMode = useCallback((nextMode: MarketplaceViewMode) => {
    setMode(nextMode);
    try { window.localStorage.setItem(MARKETPLACE_VIEW_STORAGE_KEY, nextMode); } catch { /* storage unavailable */ }
    const url = new URL(window.location.href);
    if (url.searchParams.get("view") !== nextMode) {
      url.searchParams.set("view", nextMode);
      window.history.pushState(window.history.state, "", url.pathname + url.search + url.hash);
    }
    window.dispatchEvent(new Event(CHANGE_EVENT));
  }, []);

  return { mode, changeMode };
}
