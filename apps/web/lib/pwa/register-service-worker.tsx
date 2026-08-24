"use client";

import { useEffect } from "react";

export function RegisterServiceWorker() {
  useEffect(() => {
    if (process.env.NODE_ENV !== "production" || !("serviceWorker" in navigator)) return;

    let cancelled = false;
    navigator.serviceWorker.register("/sw.js").catch((error: unknown) => {
      if (!cancelled) console.error("CoVie service worker registration failed", error);
    });

    return () => {
      cancelled = true;
    };
  }, []);

  return null;
}
