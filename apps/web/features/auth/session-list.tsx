"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { createApiClient } from "../../lib/api/api-client";
import { readApiError } from "../../lib/api/api-error";
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
  const { data, response } = await personalApiClient().GET("/api/v1/me/security/sessions");
  if (!response.ok || !data) throw await readApiError(response);
  return data;
}

function time(value?: string) {
  return value ? new Date(value).toLocaleString("vi-VN") : "—";
}

export function SessionList({ sessions: initialSessions }: { sessions: Session[] }) {
  const [sessions, setSessions] = useState(initialSessions);
  const [message, setMessage] = useState<string>();
  const [busy, setBusy] = useState<string>();

  async function revoke(path: "/api/v1/me/security/sessions/{sessionId}" | "/api/v1/me/security/sessions/revoke-others", sessionId?: string) {
    setMessage(undefined);
    setBusy(sessionId ?? "others");
    try {
      const client = personalApiClient();
      const result = sessionId
        ? await client.DELETE(path as "/api/v1/me/security/sessions/{sessionId}", { params: { path: { sessionId } } })
        : await client.POST(path as "/api/v1/me/security/sessions/revoke-others");
      if (!result.response.ok) throw await readApiError(result.response);
      setSessions(await listSessions());
    } catch {
      setMessage("Không thể thu hồi phiên. Vui lòng thử lại.");
    } finally {
      setBusy(undefined);
    }
  }

  return (
    <section className={styles.screen} aria-labelledby="session-list-title">
      <div className={styles.header}>
        <div>
          <h2 id="session-list-title">Phiên đăng nhập</h2>
          <p>{sessions.length.toLocaleString("vi-VN")} phiên đang hoạt động</p>
        </div>
        <button className={styles.revokeOthers} type="button" onClick={() => void revoke("/api/v1/me/security/sessions/revoke-others")} disabled={busy !== undefined}>
          {busy === "others" ? "Đang thu hồi…" : "Thu hồi các phiên khác"}
        </button>
      </div>
      {message ? <p className={styles.error} role="alert">{message}</p> : null}
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
              disabled={busy !== undefined || !session.id || Boolean(session.current)}
              onClick={() => session.id ? void revoke("/api/v1/me/security/sessions/{sessionId}", session.id) : undefined}
            >
              {session.current ? "Phiên hiện tại" : busy === session.id ? "Đang thu hồi…" : "Thu hồi"}
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
