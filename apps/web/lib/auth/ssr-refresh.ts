import { randomBytes } from "node:crypto";
import { serverEnv } from "@/lib/env/server";
import { safeReturnTo } from "./return-to";
import { coordinateRefresh, type RefreshExchange, type RefreshStore } from "./refresh-coordinator";
import { createRedisRefreshStore } from "./redis-refresh-store";

export const SSR_REFRESH_PATH = "/api/v1/auth/ssr-refresh";
export const SSR_RETURN_HEADER = "x-covie-ssr-return-to";
export function ssrRefreshUrl(returnTo?: string) {
  return `${SSR_REFRESH_PATH}?returnTo=${encodeURIComponent(safeReturnTo(returnTo))}`;
}

let store: RefreshStore | undefined;
function cookieJar(raw: string) {
  return new Map(raw.split(";").map(part => part.trim()).filter(Boolean).map(part => {
    const index = part.indexOf("=");
    return [part.slice(0, index), part.slice(index + 1)];
  }));
}
function mergedCookie(raw: string, updates: string[]) {
  const jar = cookieJar(raw);
  for (const update of updates) {
    for (const [name, value] of cookieJar(update.split(";")[0] ?? "")) jar.set(name, value);
  }
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
}
async function api(path: string, cookie: string, init: RequestInit = {}) {
  return fetch(`${serverEnv.API_INTERNAL_BASE_URL}${path}`, {
    ...init, headers: { ...Object.fromEntries(new Headers(init.headers)), cookie },
    cache: "no-store", redirect: "manual", signal: AbortSignal.timeout(10_000),
  });
}
async function exchange(cookie: string, beforeRotation: () => void): Promise<RefreshExchange> {
  const csrf = await api("/api/v1/auth/csrf", cookie);
  if (!csrf.ok) return { status: csrf.status === 429 ? 429 : 503, cookies: [] };
  const token: unknown = await csrf.json();
  if (!token || typeof token !== "object" || !("headerName" in token) || !("token" in token)
      || token.headerName !== "X-CSRF-Token" || typeof token.token !== "string")
    return { status: 503, cookies: [] };
  const readyCookie = mergedCookie(cookie, csrf.headers.getSetCookie());
  beforeRotation();
  const refreshed = await api("/api/v1/auth/refresh", readyCookie,
    { method: "POST", headers: { [token.headerName]: token.token } });
  // Backend AUTH_REFRESH_TOKEN rate limiting runs before RefreshRotationTransaction.
  if (!refreshed.ok) return { status: refreshed.status === 401 ? 401 : refreshed.status === 429 ? 429 : 503,
    cookies: [], retrySafe: refreshed.status === 429 };
  const cookies = refreshed.headers.getSetCookie();
  return { status: 200, cookies };
}

export async function refreshServerSession(request: Request): Promise<Response> {
  const url = new URL(request.url);
  const client = url.searchParams.get("client") === "1";
  const destination = safeReturnTo(url.searchParams.get("returnTo"));
  const headers = new Headers({ "Cache-Control": "private, no-store", "Referrer-Policy": "no-referrer" });
  const site = request.headers.get("sec-fetch-site");
  const origin = request.headers.get("origin");
  if ((site && site !== "same-origin" && site !== "none") || (origin && origin !== new URL(serverEnv.COVIE_PUBLIC_URL).origin))
    return new Response("Refresh requires a same-origin navigation.", { status: 403, headers });
  const login = (clear: boolean) => {
    if (clear) {
      const secure = new URL(serverEnv.COVIE_PUBLIC_URL).protocol === "https:" ? "; Secure" : "";
      headers.append("Set-Cookie", `covie_at=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax${secure}`);
      headers.append("Set-Cookie", `covie_rt=; Path=/api/v1/auth; Max-Age=0; HttpOnly; SameSite=Lax${secure}`);
    }
    if (client) return Response.json({ error: { code: "AUTH_REQUIRED", message: "Session expired." } }, { status: 401, headers });
    headers.set("Location", new URL(destination === "/" ? "/login" : `/login?returnTo=${encodeURIComponent(destination)}`, serverEnv.COVIE_PUBLIC_URL).href);
    return new Response(null, { status: 303, headers });
  };
  const cookie = request.headers.get("cookie") ?? "";
  const refresh = cookieJar(cookie).get("covie_rt");
  if (!refresh) return login(false);
  const binding = cookieJar(cookie).get("covie_refresh_binding");
  if (!binding || !/^[A-Za-z0-9_-]{43}$/.test(binding)) {
    const secure = new URL(serverEnv.COVIE_PUBLIC_URL).protocol === "https:" ? "; Secure" : "";
    headers.append("Set-Cookie", `covie_refresh_binding=${randomBytes(32).toString("base64url")}; Path=/api/v1/auth; Max-Age=2592000; HttpOnly; SameSite=Lax${secure}`);
    headers.set("Location", new URL(url.pathname + url.search, serverEnv.COVIE_PUBLIC_URL).href);
    return new Response(null, { status: 303, headers });
  }
  try {
    const current = await api("/api/v1/me", cookie);
    let result: RefreshExchange;
    if (current.ok) result = { status: 200, cookies: [] };
    else if (current.status !== 401) result = { status: current.status === 403 ? 401 : 503, cookies: [] };
    else {
      if (!serverEnv.REDIS_URL) return new Response("Session coordination unavailable. Retry navigation.", { status: 503, headers });
      store ??= createRedisRefreshStore(serverEnv.REDIS_URL);
      result = await coordinateRefresh(store, refresh, beforeRotation => exchange(cookie, beforeRotation), binding);
      // Recheck every shared result against the current session, including logout/revocation.
      if (result.status === 200) {
        try {
          const verified = await api("/api/v1/me", mergedCookie(cookie, result.cookies));
          if (!verified.ok) result = { ...result, status: verified.status === 401 || verified.status === 403 ? 401 : 503 };
        } catch { result = { ...result, status: 503 }; }
      }
    }
    if (result.status === 401) return login(true);
    for (const value of result.cookies) headers.append("Set-Cookie", value);
    if (result.status !== 200) {
      headers.set("Retry-After", result.status === 429 ? "5" : "2");
      return new Response("Session service temporarily unavailable. Retry navigation.", { status: result.status, headers });
    }
    if (client) return Response.json({ refreshed: true }, { headers });
    headers.set("Location", new URL(destination, serverEnv.COVIE_PUBLIC_URL).href);
    return new Response(null, { status: 303, headers });
  } catch {
    return new Response("Session service temporarily unavailable. Retry navigation.", { status: 503, headers });
  }
}
