"use client";

import type { components } from "@covie/api-client";
import { useState } from "react";
import { createApiClient } from "../../lib/api/api-client";
import { readApiError } from "../../lib/api/api-error";

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

  return <section>
    <h2>Phiên đăng nhập</h2>
    <button type="button" onClick={() => revoke("/api/v1/me/security/sessions/revoke-others")} disabled={busy !== undefined}>Thu hồi các phiên khác</button>
    {message && <p role="alert">{message}</p>}
    <ul>
      {sessions.map((session) => <li key={session.id}>
        <strong>{session.deviceName ?? "Thiết bị không xác định"}</strong>
        {session.current && <span>Thiết bị hiện tại</span>}
        <button type="button" aria-label={`Thu hồi ${session.deviceName ?? "thiết bị"}`} disabled={busy !== undefined} onClick={() => revoke("/api/v1/me/security/sessions/{sessionId}", session.id)}>Thu hồi</button>
      </li>)}
    </ul>
  </section>;
}
