"use client";

import type { components } from "@covie/api-client";
import Link from "next/link";
import { ErrorState } from "@/components/ui/error-state";
import { useRef, useState } from "react";
import { createApiClient } from "../../lib/api/api-client";
import { CovieApiError, parsedApiError } from "../../lib/api/api-error";
import styles from "./session-list.module.css";

type Session = components["schemas"]["AuthSessionSummaryResponse"];

function personalApiClient() {
  const client = createApiClient();
  client.use({
    onRequest({ request }) {
      return new Request(request, { cache: "no-store" });
    },
  });
  return client;
}

async function listSessions() {
  const { data, error, response } = await personalApiClient().GET("/api/v1/me/security/sessions");
  if (!response.ok || !data) throw await parsedApiError(response, error);
  return data;
}

function time(value?: string) {
  return value ? new Date(value).toLocaleString("vi-VN") : "—";
}

export function SessionList({ sessions: initialSessions }: { sessions: Session[] }) {
  const [sessions, setSessions] = useState(initialSessions);
  const [message, setMessage] = useState<string>();
  const [busy, setBusy] = useState<string>();
  const revokePending = useRef(false);
  const [authRequired, setAuthRequired] = useState(false);
  const [refreshFailed, setRefreshFailed] = useState(false);
  const hasOtherSessions = sessions.some(session => session.id && session.current === false);

  async function refresh() {
    try {
      setSessions(await listSessions());
      setRefreshFailed(false);
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) setAuthRequired(true);
      setRefreshFailed(true);
    }
  }

  async function retryRefresh() {
    if (revokePending.current || authRequired) return;
    revokePending.current = true;
    setBusy("refresh");
    try { await refresh(); } finally { revokePending.current = false; setBusy(undefined); }
  }

  async function revoke(path: "/api/v1/me/security/sessions/{sessionId}" | "/api/v1/me/security/sessions/revoke-others", sessionId?: string) {
    if (revokePending.current || authRequired || refreshFailed) return;
    if (!sessionId && !hasOtherSessions) return;
    revokePending.current = true;
    setMessage(undefined);
    setBusy(sessionId ?? "others");
    try {
      const client = personalApiClient();
      const result = sessionId
        ? await client.DELETE(path as "/api/v1/me/security/sessions/{sessionId}", { params: { path: { sessionId } } })
        : await client.POST(path as "/api/v1/me/security/sessions/revoke-others");
      if (!result.response.ok) throw await parsedApiError(result.response, result.error);
      await refresh();
    } catch (cause) {
      if (cause instanceof CovieApiError && cause.status === 401) setAuthRequired(true);
      setMessage("Không thể thu hồi phiên. Vui lòng thử lại.");
    } finally {
      revokePending.current = false;
      setBusy(undefined);
    }
  }

  if (authRequired) return <ErrorState title="Vui lòng đăng nhập lại"
    description="Phiên đăng nhập đã hết hạn hoặc bạn chưa đăng nhập."
    action={<Link className="button-primary" href="/login?returnTo=%2Fme%2Fsecurity%2Fsessions">Đăng nhập lại</Link>}
  />;

  return (
    <section className={styles.screen} aria-labelledby="session-list-title">
      <div className={styles.header}>
        <div>
          <h2 id="session-list-title">Phiên đăng nhập</h2>
          <p>{refreshFailed ? "Danh sách phiên cần được tải lại." : `${sessions.length.toLocaleString("vi-VN")} phiên đang hoạt động`}</p>
        </div>
        <button className={styles.revokeOthers} type="button" onClick={() => void revoke("/api/v1/me/security/sessions/revoke-others")} disabled={busy !== undefined || refreshFailed || !hasOtherSessions}>
          {busy === "others" ? "Đang thu hồi…" : "Thu hồi các phiên khác"}
        </button>
      </div>
      {message ? <p className={styles.error} role="alert">{message}</p> : null}
      {refreshFailed ? <div className={styles.error} role="alert">
        <p>Không thể tải danh sách phiên mới nhất. Vui lòng tải lại trước khi tiếp tục.</p>
        <button className="button-secondary" type="button" disabled={busy !== undefined} onClick={() => void retryRefresh()}>Thử lại tải phiên</button>
      </div> : null}
      {!refreshFailed && sessions.length === 0 ? <p role="status">Không có phiên đăng nhập đang hoạt động.</p> : null}
      <ul className={styles.cards}>
        {sessions.map((session) => (
          <li className={styles.card} key={session.id}>
            <div>
              <div className={styles.title}>
                <strong>{session.deviceName ?? "Thiết bị không xác định"}</strong>
                {session.current ? <span>Thiết bị hiện tại</span> : null}
              </div>
              <dl className={styles.facts}>
                <div><dt>Dùng gần nhất</dt><dd>{time(session.lastUsedAt)}</dd></div>
                <div><dt>Đăng nhập</dt><dd>{time(session.createdAt)}</dd></div>
                <div><dt>Hết hạn</dt><dd>{time(session.expiresAt)}</dd></div>
              </dl>
            </div>
            <button
              className={styles.revoke}
              type="button"
              aria-label={`Thu hồi ${session.deviceName ?? "thiết bị"}`}
              disabled={busy !== undefined || refreshFailed || !session.id}
              onClick={() => session.id ? void revoke("/api/v1/me/security/sessions/{sessionId}", session.id) : undefined}
            >
              {busy === session.id ? "Đang thu hồi…" : "Thu hồi"}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
