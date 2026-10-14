"use client";

import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import { fetchSocialSummaries, type SocialSummary } from "./social-api";

type Context = {
  summaries: Record<string, SocialSummary>;
  loading: boolean;
  failed: boolean;
  updateSummary: (summary: SocialSummary) => void;
  retry: () => void;
};

const SocialContext = createContext<Context>({
  summaries: {}, loading: false, failed: false,
  updateSummary: () => undefined, retry: () => undefined,
});

export function SocialFeedProvider({ itemIds, enabled, children }: {
  itemIds: string[];
  enabled: boolean;
  children: ReactNode;
}) {
  const key = itemIds.filter(Boolean).join(",");
  const [attempt, setAttempt] = useState(0);
  const stamp = key + "|" + attempt;
  const [result, setResult] = useState<{
    stamp: string;
    summaries: Record<string, SocialSummary>;
    failed: boolean;
  }>({ stamp: "", summaries: {}, failed: false });

  useEffect(() => {
    if (!enabled || !key) return;
    let live = true;
    // Changing item IDs invalidates data without a synchronous effect state update.
    const ids = key.split(",");
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

  const ready = enabled && Boolean(key) && result.stamp === stamp;
  const summaries = ready ? result.summaries : {};
  const loading = enabled && Boolean(key) && !ready;
  const failed = ready && result.failed;

  const value = useMemo<Context>(() => ({
    summaries, loading, failed,
    updateSummary: (summary) => setResult((old) => old.stamp === stamp
      ? { ...old, summaries: { ...old.summaries, [summary.itemId]: summary } } : old),
    retry: () => setAttempt((n) => n + 1),
  }), [summaries, loading, failed, stamp]);

  return <SocialContext.Provider value={value}>{children}</SocialContext.Provider>;
}

export const useSocialFeed = () => useContext(SocialContext);
