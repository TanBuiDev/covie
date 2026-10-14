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
  summaries: {}, loading: false, failed: false, updateSummary: () => undefined, retry: () => undefined,
});

export function SocialFeedProvider({ itemIds, enabled, children }: {
  itemIds: string[];
  enabled: boolean;
  children: ReactNode;
}) {
  const key = itemIds.filter(Boolean).join(",");
  const [summaries, setSummaries] = useState<Record<string, SocialSummary>>({});
  const [loading, setLoading] = useState(false);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    if (!enabled || !key) { setSummaries({}); setLoading(false); setFailed(false); return; }
    let live = true;
    setLoading(true);
    setFailed(false);
    // The backend caps each request at 40; results can include more from cursor pagination.
    const ids = key.split(",");
    Promise.all(Array.from({ length: Math.ceil(ids.length / 40) }, (_, index) =>
      fetchSocialSummaries(ids.slice(index * 40, (index + 1) * 40))))
      .then((chunks) => { if (live) setSummaries(Object.assign({}, ...chunks)); })
      .catch(() => { if (live) { setFailed(true); setSummaries({}); } })
      .finally(() => { if (live) setLoading(false); });
    return () => { live = false; };
  }, [enabled, key, attempt]);

  const value = useMemo<Context>(() => ({
    summaries, loading, failed,
    updateSummary: (summary) => setSummaries((old) => ({ ...old, [summary.itemId]: summary })),
    retry: () => setAttempt((n) => n + 1),
  }), [summaries, loading, failed]);
  return <SocialContext.Provider value={value}>{children}</SocialContext.Provider>;
}

export const useSocialFeed = () => useContext(SocialContext);
