"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { fetchSocialPreviews, fetchSocialSummaries, type SocialPreview, type SocialSummary } from "./social-api";

type Context = {
  previewsEnabled: boolean;
  previews: Record<string, SocialPreview>;
  previewsLoading: boolean;
  previewsFailed: boolean;
  retryPreviews: () => void;
  revalidatePreview: (itemId: string) => void;
  summaries: Record<string, SocialSummary>;
  loading: boolean;
  failed: boolean;
  updateSummary: (summary: SocialSummary) => void;
  retry: () => void;
};

const SOCIAL_SYNC = "covie:catalog-social-updated";
const SOCIAL_PREVIEW_REVALIDATE = "covie:catalog-social-preview-revalidate";
const SocialContext = createContext<Context>({
  previewsEnabled: false,
  previews: {}, previewsLoading: false, previewsFailed: false, retryPreviews: () => undefined,
  revalidatePreview: () => undefined,
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

export function SocialFeedProvider({ itemIds, enabled, children,
  previewsEnabled = process.env.NEXT_PUBLIC_COVIE_SOCIAL_V2_PREVIEWS_ENABLED === "true",
}: {
  itemIds: string[];
  enabled: boolean;
  children: ReactNode;
  previewsEnabled?: boolean;
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
  const [previewAttempt, setPreviewAttempt] = useState(0);
  const previewActive = enabled && previewsEnabled && Boolean(key);
  const previewScope = `${key}|${previewActive}|${attempt}|${previewAttempt}`;
  const [previewResult, setPreviewResult] = useState<{
    scope: string; ready: boolean; previews: Record<string, SocialPreview>; failed: boolean;
  }>({ scope: previewScope, ready: false, previews: {}, failed: false });
  if (previewResult.scope !== previewScope) {
    setPreviewResult({ scope: previewScope, ready: false, previews: {}, failed: false });
  }

  useEffect(() => {
    if (!previewActive) return;
    let live = true;
    const ids = key.split(",");
    Promise.all(Array.from({ length: Math.ceil(ids.length / 40) }, (_, index) =>
      fetchSocialPreviews(ids.slice(index * 40, (index + 1) * 40))))
      .then((chunks) => {
        if (live) setPreviewResult({ scope: previewScope, ready: true,
          previews: Object.assign({}, ...chunks), failed: false });
      })
      .catch(() => {
        if (live) setPreviewResult({ scope: previewScope, ready: true, previews: {}, failed: true });
      });
    return () => { live = false; };
  }, [previewActive, key, previewScope]);

  useEffect(() => {
    if (!previewActive) return;
    const visibleIds = new Set(key.split(","));
    const revalidate = (event: Event) => {
      const itemId: unknown = (event as CustomEvent<unknown>).detail;
      if (typeof itemId === "string" && visibleIds.has(itemId)) setPreviewAttempt(value => value + 1);
    };
    window.addEventListener(SOCIAL_PREVIEW_REVALIDATE, revalidate);
    return () => window.removeEventListener(SOCIAL_PREVIEW_REVALIDATE, revalidate);
  }, [previewActive, key]);

  useEffect(() => {
    if (!enabled || !key) return;
    let live = true;
    const ids = key.split(",");
    const visibleIds = new Set(ids);
    const confirmedWhileReading: Record<string, SocialSummary> = {};
    const onConfirmed = (event: Event) => {
      const summary = asSummary((event as CustomEvent<unknown>).detail);
      if (summary && visibleIds.has(summary.itemId)) confirmedWhileReading[summary.itemId] = summary;
    };
    window.addEventListener(SOCIAL_SYNC, onConfirmed);
    // Never exceed the public API's 40-ID batch limit.
    Promise.all(Array.from({ length: Math.ceil(ids.length / 40) }, (_, index) =>
      fetchSocialSummaries(ids.slice(index * 40, (index + 1) * 40))))
      .then((chunks) => {
        if (live) {
          const summaries: Record<string, SocialSummary> = Object.assign({}, ...chunks);
          for (const [itemId, summary] of Object.entries(confirmedWhileReading)) {
            if (Object.hasOwn(summaries, itemId)) summaries[itemId] = summary;
          }
          setResult({ stamp, summaries, failed: false });
        }
      })
      .catch(() => {
        if (live) setResult({ stamp, summaries: {}, failed: true });
      })
      .finally(() => window.removeEventListener(SOCIAL_SYNC, onConfirmed));
    return () => { live = false; window.removeEventListener(SOCIAL_SYNC, onConfirmed); };
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
  const retryPreviews = useCallback(() => setPreviewAttempt((value) => value + 1), []);
  const revalidatePreview = useCallback((itemId: string) => {
    window.dispatchEvent(new CustomEvent(SOCIAL_PREVIEW_REVALIDATE, { detail: itemId }));
  }, []);
  const previewReady = previewActive && previewResult.scope === previewScope && previewResult.ready;
  const previews = useMemo(() => previewReady ? previewResult.previews : {}, [previewReady, previewResult.previews]);
  const previewsLoading = previewActive && !previewReady;
  const previewsFailed = previewReady && previewResult.failed;
  const value = useMemo<Context>(() => ({
    previewsEnabled: enabled && previewsEnabled,
    summaries, loading, failed, updateSummary, retry, previews, previewsLoading, previewsFailed, retryPreviews, revalidatePreview,
  }), [enabled, previewsEnabled, summaries, loading, failed, updateSummary, retry, previews, previewsLoading, previewsFailed, retryPreviews, revalidatePreview]);
  return <SocialContext.Provider value={value}>{children}</SocialContext.Provider>;
}

export const useSocialFeed = () => useContext(SocialContext);
