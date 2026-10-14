"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { fetchSocialSummaries, type SocialSummary } from "./social-api";

type Context = {
  summaries: Record<string, SocialSummary>;
  loading: boolean;
  failed: boolean;
  updateSummary: (summary: SocialSummary) => void;
  retry: () => void;
};

const SOCIAL_SYNC = "covie:catalog-social-updated";
const SocialContext = createContext<Context>({
  summaries: {}, loading: false, failed: false,
  updateSummary: () => undefined, retry: () => undefined,
});

function asSummary(value: unknown): SocialSummary | null {
  if (!value || typeof value !== "object") return null;
  const data = value as Partial<SocialSummary>;
  return typeof data.itemId === "string" &&
    typeof data.likes === "number" && data.likes >= 0 &&
    typeof data.comments === "number" && data.comments >= 0 &&
    typeof data.liked === "boolean" && typeof data.saved === "boolean"
    ? data as SocialSummary : null;
}

export function SocialFeedProvider({ itemIds, enabled, children }: {
  itemIds: string[];
  enabled: boolean;
  children: ReactNode;
}) {
  // Stable keys prevent refetches when a listing occurs twice in a feed.
  const key = Array.from(new Set(itemIds.filter(Boolean))).sort().join(",");
  const [attempt, setAttempt] = useState(0);
  const stamp = key + "|" + attempt;
  const [result, setResult] = useState<{
    stamp: string;
    summaries: Record<string, SocialSummary>;
    failed: boolean;
  }>({ stamp: "", summaries: {}, failed: false });
  const lastReload = useRef(0);

  useEffect(() => {
    if (!enabled || !key) return;
    let live = true;
    const ids = key.split(",");
    // Never exceed the public API's 40-ID batch limit.
    Promise.all(Array.from({ length: Math.ceil(ids.length / 40) }, (_, index) =>
      fetchSocialSummaries(ids.slice(index * 40, (index + 1) * 40))))
      .then((chunks) => {
        if (live) setResult({ stamp, summaries: Object.assign({}, ...chunks), failed: false });
      })
      .catch(() => {
        if (live) setResult({ stamp, summaries: {}, failed: true });
      });
    return () => { live = false; };
  }, [enabled, key, stamp]);

  // Synchronize duplicate cards across independent home-page sections.
  // No persistence: private viewer flags are not stored across account changes.
  useEffect(() => {
    if (!enabled || !key) return;
    const visibleIds = new Set(key.split(","));
    const onUpdate = (event: Event) => {
      const updated = asSummary((event as CustomEvent<unknown>).detail);
      if (!updated || !visibleIds.has(updated.itemId)) return;
      setResult((previous) => previous.stamp === stamp
        ? { ...previous, summaries: { ...previous.summaries, [updated.itemId]: updated } }
        : previous);
    };
    window.addEventListener(SOCIAL_SYNC, onUpdate);
    return () => window.removeEventListener(SOCIAL_SYNC, onUpdate);
  }, [enabled, key, stamp]);

  // Reconcile counts from the server on return to a stale tab, without polling.
  useEffect(() => {
    if (!enabled || !key) return;
    const reload = () => {
      if (document.visibilityState !== "visible") return;
      const now = Date.now();
      if (now - lastReload.current < 30_000) return;
      lastReload.current = now;
      setAttempt((value) => value + 1);
    };
    window.addEventListener("focus", reload);
    window.addEventListener("online", reload);
    document.addEventListener("visibilitychange", reload);
    return () => {
      window.removeEventListener("focus", reload);
      window.removeEventListener("online", reload);
      document.removeEventListener("visibilitychange", reload);
    };
  }, [enabled, key]);

  const ready = enabled && Boolean(key) && result.stamp === stamp;
  const summaries = useMemo(() => ready ? result.summaries : {}, [ready, result.summaries]);
  const loading = enabled && Boolean(key) && !ready;
  const failed = ready && result.failed;

  const updateSummary = useCallback((summary: SocialSummary) => {
    setResult((previous) => previous.stamp === stamp
      ? { ...previous, summaries: { ...previous.summaries, [summary.itemId]: summary } }
      : previous);
    // Other section providers can now show the same server-confirmed state.
    window.dispatchEvent(new CustomEvent(SOCIAL_SYNC, { detail: summary }));
  }, [stamp]);

  const retry = useCallback(() => setAttempt((value) => value + 1), []);
  const value = useMemo<Context>(() => ({
    summaries, loading, failed, updateSummary, retry,
  }), [summaries, loading, failed, updateSummary, retry]);
  return <SocialContext.Provider value={value}>{children}</SocialContext.Provider>;
}

export const useSocialFeed = () => useContext(SocialContext);
