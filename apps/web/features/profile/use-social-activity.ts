"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CovieApiError } from "@/lib/api/api-error";
import { fetchMySocialActivity, type SocialActivityEntry, type SocialActivityType } from "./social-activity-api";

type State = {
  scope: string; items: SocialActivityEntry[]; nextCursor?: string;
  status: "idle" | "loading" | "ready" | "loadingMore" | "error" | "unauthorized";
  error?: unknown;
};
type Session = { scope: string; live: boolean; pending: boolean; controller?: AbortController };

export function useSocialActivity({ accountId, type, enabled }: {
  accountId?: string; type: SocialActivityType; enabled: boolean;
}) {
  const scope = JSON.stringify([accountId ?? null, type, enabled]);
  const canRead = enabled && Boolean(accountId);
  const initial = (): State => ({ scope, items: [], status: !enabled ? "idle" : canRead ? "loading" : "unauthorized" });
  const [state, setState] = useState<State>(initial);
  const current = useRef<Session | null>(null);
  if (state.scope !== scope) setState(initial());

  const load = useCallback(async (session: Session, cursor?: string) => {
    if (!session.live || session.pending) return;
    session.pending = true;
    session.controller = new AbortController();
    setState(previous => previous.scope === session.scope ? {
      ...previous, items: cursor ? previous.items : [], nextCursor: cursor, error: undefined,
      status: cursor ? "loadingMore" : "loading",
    } : previous);
    try {
      const page = await fetchMySocialActivity(type, cursor, session.controller.signal);
      if (!session.live) return;
      setState(previous => {
        if (previous.scope !== session.scope) return previous;
        const existing = cursor ? previous.items : [];
        const key = (entry: SocialActivityEntry) => type === "COMMENTED" ? entry.commentId : entry.itemId;
        const seen = new Set(existing.map(key).filter(Boolean));
        const added = (page.items ?? []).filter(entry => {
          const id = key(entry);
          if (!id) return true;
          if (seen.has(id)) return false;
          seen.add(id); return true;
        });
        return { scope: session.scope, items: [...existing, ...added], nextCursor: page.nextCursor || undefined, status: "ready" };
      });
    } catch (error) {
      if (!session.live) return;
      const denied = error instanceof CovieApiError && (error.status === 401 || error.status === 403);
      setState(previous => previous.scope === session.scope ? {
        ...previous, items: denied ? [] : previous.items, nextCursor: denied ? undefined : previous.nextCursor,
        status: denied ? "unauthorized" : "error", error,
      } : previous);
    } finally { session.pending = false; }
  }, [type]);

  useEffect(() => {
    if (!canRead) return;
    let session: Session = { scope, live: true, pending: false };
    current.current = session;
    void load(session);
    const reconcile = () => {
      if (document.visibilityState !== "visible") return;
      session.live = false;
      session.controller?.abort();
      session = { scope, live: true, pending: false };
      current.current = session;
      void load(session);
    };
    window.addEventListener("focus", reconcile);
    window.addEventListener("online", reconcile);
    document.addEventListener("visibilitychange", reconcile);
    return () => {
      session.live = false; session.controller?.abort();
      window.removeEventListener("focus", reconcile);
      window.removeEventListener("online", reconcile);
      document.removeEventListener("visibilitychange", reconcile);
    };
  }, [canRead, scope, load]);

  const request = (cursor?: string) => {
    const session = current.current;
    if (canRead && session?.scope === scope) void load(session, cursor);
  };
  return {
    ...state,
    loadMore: () => { if (state.status === "ready" && state.nextCursor) request(state.nextCursor); },
    retry: () => { if (state.status === "error") request(state.nextCursor); },
  };
}
