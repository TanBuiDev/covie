// @vitest-environment node
import { afterEach, beforeEach, expect, it, vi } from "vitest";
const records = vi.hoisted(() => new Map<string, { owner: string; result?: string }>());
vi.mock("@/lib/env/server", () => ({ serverEnv: { API_INTERNAL_BASE_URL: "http://api:8080", COVIE_PUBLIC_URL: "https://covie.test", REDIS_URL: "redis://test:6379" } }));
vi.mock("../../lib/auth/redis-refresh-store", () => ({ createRedisRefreshStore: () => ({
  async claim(key: string, owner: string) {
    const entry = records.get(key);
    if (entry?.result) return { kind: "result", value: entry.result };
    if (entry) return { kind: "pending", startedAt: Number(entry.owner.split(":")[1]) };
    records.set(key, { owner });
    return { kind: "owner" };
  },
  async publish(key: string, owner: string, result: string) {
    if (records.get(key)?.owner !== owner) return false;
    records.set(key, { owner, result }); return true;
  },
  async release(key: string, owner: string) { if (records.get(key)?.owner === owner) records.delete(key); },
}) }));
import { refreshServerSession } from "../../lib/auth/ssr-refresh";

const request = (cookie = "covie_rt=valid-refresh", destination = "/cart?view=mine") => new Request(
  `https://covie.test/api/v1/auth/ssr-refresh?returnTo=${encodeURIComponent(destination)}`,
  { headers: { cookie: cookie ? `${cookie}; covie_refresh_binding=${"A".repeat(43)}` : "", "sec-fetch-site": "same-origin" } },
);
function backend() {
  const fetcher = vi.fn().mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-Token", token: "csrf-value" }, { headers: { "set-cookie": "COVIE-XSRF-TOKEN=csrf-value; Path=/" } }))
    .mockResolvedValueOnce(Response.json({}, { headers: new Headers([
      ["set-cookie", "covie_at=new-access; Path=/; HttpOnly; SameSite=Lax; Max-Age=900"],
      ["set-cookie", "covie_rt=new-refresh; Path=/api/v1/auth; HttpOnly; SameSite=Lax; Max-Age=2592000"],
    ]) }))
    .mockResolvedValueOnce(Response.json({ id: "buyer" }));
  vi.stubGlobal("fetch", fetcher);
  return fetcher;
}
beforeEach(() => records.clear());
afterEach(() => vi.unstubAllGlobals());
it("refreshes with CSRF, forwards both cookies and preserves the protected destination", async () => {
  const fetcher = backend();
  const result = await refreshServerSession(request());
  expect(result.status).toBe(303);
  expect(result.headers.get("location")).toBe("https://covie.test/cart?view=mine");
  expect(result.headers.getSetCookie()).toHaveLength(2);
  const init = fetcher.mock.calls[2]![1] as RequestInit;
  expect(init.method).toBe("POST");
  expect(new Headers(init.headers).get("cookie")).toContain("COVIE-XSRF-TOKEN=csrf-value");
  expect(new Headers(init.headers).get("X-CSRF-Token")).toBe("csrf-value");
  expect(result.headers.get("cache-control")).toContain("no-store");
  expect(await result.text()).not.toContain("new-refresh");
});
it("sends missing or invalid refresh sessions to login without looping", async () => {
  const fetcher = backend();
  expect((await refreshServerSession(request(""))).headers.get("location")).toBe("https://covie.test/login?returnTo=%2Fcart%3Fview%3Dmine");
  expect(fetcher).not.toHaveBeenCalled();
  fetcher.mockReset().mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-Token", token: "csrf" }))
    .mockResolvedValueOnce(new Response(null, { status: 401 }));
  const result = await refreshServerSession(request());
  expect(result.headers.get("location")).toContain("/login?");
  expect(result.headers.getSetCookie().join(";")).toContain("Max-Age=0");
});
it("preserves cookies when the backend is temporarily unavailable", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 503 })));
  const result = await refreshServerSession(request());
  expect(result.status).toBe(503);
  expect(result.headers.get("location")).toBeNull();
  expect(result.headers.getSetCookie()).toEqual([]);
});
it("rejects cross-site refresh and external return destinations", async () => {
  const fetcher = backend();
  const cross = request(); cross.headers.set("sec-fetch-site", "cross-site");
  expect((await refreshServerSession(cross)).status).toBe(403);
  expect(fetcher).not.toHaveBeenCalled();
  expect((await refreshServerSession(request("", "https://evil.test"))).headers.get("location")).toBe("https://covie.test/login");
});
it("coalesces concurrent rotation of the same refresh token without mixing destinations", async () => {
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  let posts = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith("/csrf")) return Response.json({ headerName: "X-CSRF-Token", token: "csrf" });
    if (url.endsWith("/refresh")) { posts++; await gate; return Response.json({}, { headers: { "set-cookie": "covie_at=rotated; Path=/; HttpOnly" } }); }
    return new Headers(init.headers).get("cookie")?.includes("covie_at=rotated")
      ? Response.json({ id: "buyer" }) : new Response(null, { status: 401 });
  }));
  const browserRequest = request("covie_rt=concurrent", "/cart");
  const browserUrl = new URL(browserRequest.url); browserUrl.searchParams.set("client", "1");
  const first = refreshServerSession(new Request(browserUrl, browserRequest));
  const second = refreshServerSession(request("covie_rt=concurrent", "/me"));
  await vi.waitFor(() => expect(posts).toBe(1));
  release();
  const results = await Promise.all([first, second]);
  expect(results[0]!.status).toBe(200);
  expect(await results[0]!.json()).toEqual({ refreshed: true });
  expect(results[1]!.headers.get("location")).toBe("https://covie.test/me");
  expect(results[0]!.headers.getSetCookie()).toEqual(results[1]!.headers.getSetCookie());
});
it("preserves rotated cookies on a temporary verification failure", async () => {
  const fetcher = backend();
  fetcher.mockReset().mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-Token", token: "csrf" }))
    .mockResolvedValueOnce(Response.json({}, { headers: { "set-cookie": "covie_rt=rotated; Path=/api/v1/auth; HttpOnly" } }))
    .mockResolvedValueOnce(new Response(null, { status: 503 }));
  const result = await refreshServerSession(request("covie_rt=temporary"));
  expect(result.status).toBe(503);
  expect(result.headers.getSetCookie().join(";")).toContain("covie_rt=rotated");
});
it("preserves rotated cookies when verification loses its network connection", async () => {
  const fetcher = backend();
  fetcher.mockReset().mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-Token", token: "csrf" }))
    .mockResolvedValueOnce(Response.json({}, { headers: { "set-cookie": "covie_rt=rotated; Path=/api/v1/auth; HttpOnly" } }))
    .mockRejectedValueOnce(new Error("connection lost"));
  const result = await refreshServerSession(request("covie_rt=network-failure"));
  expect(result.status).toBe(503);
  expect(result.headers.getSetCookie().join(";")).toContain("covie_rt=rotated");
});
it("does not rotate an already valid access token", async () => {
  const fetcher = vi.fn().mockResolvedValue(Response.json({ id: "buyer" }));
  vi.stubGlobal("fetch", fetcher);
  const result = await refreshServerSession(request());
  expect(result.status).toBe(303);
  expect(fetcher).toHaveBeenCalledTimes(1);
  expect(result.headers.getSetCookie()).toEqual([]);
});
it("ends a session if the newly issued access token is rejected", async () => {
  const fetcher = backend();
  fetcher.mockReset().mockResolvedValueOnce(new Response(null, { status: 401 }))
    .mockResolvedValueOnce(Response.json({ headerName: "X-CSRF-Token", token: "csrf" }))
    .mockResolvedValueOnce(Response.json({}, { headers: { "set-cookie": "covie_rt=rotated; Path=/api/v1/auth; HttpOnly" } }))
    .mockResolvedValueOnce(new Response(null, { status: 403 }));
  const result = await refreshServerSession(request("covie_rt=revoked-after-rotation"));
  expect(result.status).toBe(303);
  expect(result.headers.get("location")).toContain("/login?");
  expect(result.headers.getSetCookie()).toHaveLength(2);
  expect(result.headers.getSetCookie().every(cookie => cookie.includes("Max-Age=0"))).toBe(true);
});
