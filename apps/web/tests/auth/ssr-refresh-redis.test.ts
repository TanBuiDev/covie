// @vitest-environment node
import { afterAll, beforeAll, beforeEach, expect, it, vi } from "vitest";
import { createClient } from "redis";
import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import { createRedisRefreshStore } from "../../lib/auth/redis-refresh-store";
import { coordinateRefresh, REFRESH_WAIT_MS } from "../../lib/auth/refresh-coordinator";

const config = vi.hoisted(() => ({
  API_INTERNAL_BASE_URL: "http://api:8080", COVIE_PUBLIC_URL: "https://covie.test",
  REDIS_URL: process.env.COVIE_REFRESH_TEST_REDIS_URL,
}));
vi.mock("@/lib/env/server", () => ({ serverEnv: config }));
const enabled = Boolean(config.REDIS_URL);
const redis = createClient({ url: config.REDIS_URL, socket: { reconnectStrategy: false } });
redis.on("error", () => {});
beforeAll(async () => { if (enabled) await redis.connect(); });
beforeEach(() => { vi.resetModules(); });
afterAll(() => { if (redis.isOpen) redis.destroy(); vi.unstubAllGlobals(); });

function request(token: string, path: string) {
  return new Request(`https://covie.test/api/v1/auth/ssr-refresh?returnTo=${path}`, {
    headers: { cookie: `covie_rt=${token}; covie_refresh_binding=${"A".repeat(43)}`, "sec-fetch-site": "same-origin" },
  });
}
function backend(delay = 0) {
  let posts = 0;
  vi.stubGlobal("fetch", vi.fn(async (url: string, init: RequestInit) => {
    if (url.endsWith("/csrf")) return Response.json({ headerName: "X-CSRF-Token", token: "csrf" });
    if (url.endsWith("/refresh")) {
      posts++;
      await new Promise(resolve => setTimeout(resolve, delay));
      return Response.json({}, { headers: new Headers([
        ["set-cookie", "covie_at=new-access; Path=/; HttpOnly; Max-Age=900"],
        ["set-cookie", "covie_rt=new-refresh; Path=/api/v1/auth; HttpOnly; Max-Age=2592000"],
      ]) });
    }
    return new Headers(init.headers).get("cookie")?.includes("covie_at=new-access")
      ? Response.json({ id: "buyer" }) : new Response(null, { status: 401 });
  }));
  return () => posts;
}
async function worker(token: string, mode = "normal") {
  const child = spawn(process.execPath, ["--experimental-strip-types", "tests/auth/refresh-process-worker.mjs"], {
    cwd: process.cwd(), windowsHide: true, stdio: ["pipe", "pipe", "pipe"],
  });
  child.stdin.end(JSON.stringify({ redisUrl: config.REDIS_URL, token, mode }));
  let output = "", errors = "";
  child.stdout.on("data", chunk => { output += chunk; });
  child.stderr.on("data", chunk => { errors += chunk; });
  await new Promise<void>((resolve, reject) => {
    const deadline = setTimeout(() => { child.kill(); reject(new Error("Refresh worker timeout")); }, 45_000);
    child.on("error", error => { clearTimeout(deadline); reject(error); });
    child.on("exit", code => { clearTimeout(deadline); if (code === 0) resolve(); else reject(new Error(errors)); });
  });
  return output ? JSON.parse(output) as { status: number; cookies: string[] } : undefined;
}
it.skipIf(!enabled)("shares rotated cookies across isolated module instances and a late request", async () => {
  const posts = backend(100);
  const first = await import("../../lib/auth/ssr-refresh");
  vi.resetModules();
  const second = await import("../../lib/auth/ssr-refresh");
  const token = `parallel-${crypto.randomUUID()}`;
  const results = await Promise.all([
    first.refreshServerSession(request(token, "/cart")),
    second.refreshServerSession(request(token, "/me")),
  ]);
  expect(posts()).toBe(1);
  expect(results.map(response => response.status)).toEqual([303, 303]);
  const credentials = (response: Response) => response.headers.getSetCookie().map(cookie => cookie.split(";")[0]);
  expect(credentials(results[0]!)).toEqual(credentials(results[1]!));
  expect(results[1]!.headers.get("location")).toBe("https://covie.test/me");
  const late = await second.refreshServerSession(request(token, "/orders"));
  expect(late.status).toBe(303);
  expect(posts()).toBe(1);
  const keys = await redis.keys("covie:ssr-refresh:*");
  const stored = await redis.mGet(keys);
  expect(JSON.stringify([keys, stored])).not.toContain(token);
  expect(JSON.stringify(stored)).not.toMatch(/new-access|new-refresh/);
});
it.skipIf(!enabled)("fails closed when Redis is unreachable without clearing cookies or rotating", async () => {
  const previous = config.REDIS_URL;
  config.REDIS_URL = "redis://127.0.0.1:1";
  try {
    const posts = backend();
    const { refreshServerSession } = await import("../../lib/auth/ssr-refresh");
    const result = await refreshServerSession(request(`offline-${crypto.randomUUID()}`, "/cart"));
    expect(result.status).toBe(503);
    expect(result.headers.getSetCookie()).toEqual([]);
    expect(result.headers.get("location")).toBeNull();
    expect(posts()).toBe(0);
  } finally { config.REDIS_URL = previous; }
});
it.skipIf(!enabled)("coalesces three real Node processes even when rotation takes over 1.5 seconds", async () => {
  const token = `process-${crypto.randomUUID()}`;
  const results = await Promise.all([worker(token), worker(token), worker(token)]);
  expect(results.map(result => result?.status)).toEqual([200, 200, 200]);
  expect(results[0]!.cookies).toEqual(results[1]!.cookies);
  expect(results[1]!.cookies).toEqual(results[2]!.cookies);
  expect(await redis.get(`covie:test:posts:${token}`)).toBe("1");
}, 10_000);
it.skipIf(!enabled)("does not reuse a consumed token after the encrypted result expires", async () => {
  const token = `spent-${crypto.randomUUID()}`;
  expect((await worker(token))?.status).toBe(200);
  await new Promise(resolve => setTimeout(resolve, 5100));
  expect(await worker(token)).toEqual({ status: 503, cookies: [] });
  expect(await redis.get(`covie:test:posts:${token}`)).toBe("1");
}, 12_000);
it.skipIf(!enabled)("keeps an orphaned guard when the owner process crashes after sending rotation", async () => {
  const token = `crash-${crypto.randomUUID()}`;
  await worker(token, "crash");
  await new Promise(resolve => setTimeout(resolve, REFRESH_WAIT_MS + 50));
  expect(await worker(token)).toEqual({ status: 503, cookies: [] });
  expect(await redis.get(`covie:test:posts:${token}`)).toBe("1");
}, 40_000);
it.skipIf(!enabled)("only the owning claim may release or publish", async () => {
  const token = `owner-${crypto.randomUUID()}`;
  const key = createHash("sha256").update(token).digest("hex");
  const store = createRedisRefreshStore(config.REDIS_URL!);
  const owner = `owner:${Date.now()}`;
  expect(await store.claim(key, owner)).toEqual({ kind: "owner" });
  await store.release(key, "wrong-owner");
  expect(await store.publish(key, "wrong-owner", "invalid")).toBe(false);
  expect((await store.claim(key, "other-owner")).kind).toBe("pending");
  expect(await redis.ttl(`covie:ssr-refresh:{${key}}:guard`)).toBeGreaterThan(2592000);
  await store.release(key, owner);
  expect(await store.claim(key, `next:${Date.now()}`)).toEqual({ kind: "owner" });
});
it.skipIf(!enabled)("does not retain an invalid token's guard for 30 days", async () => {
  const token = `invalid-${crypto.randomUUID()}`;
  await coordinateRefresh(createRedisRefreshStore(config.REDIS_URL!), token, async begin => {
    begin(); return { status: 401, cookies: [] };
  });
  const key = createHash("sha256").update(token).digest("hex");
  const ttl = await redis.ttl(`covie:ssr-refresh:{${key}}:guard`);
  expect(ttl).toBeGreaterThan(0);
  expect(ttl).toBeLessThanOrEqual(5);
});
it.skipIf(!enabled)("rejects shared cookies after the session has been revoked", async () => {
  let revoked = false;
  const posts = backend();
  const normalFetch = globalThis.fetch;
  vi.stubGlobal("fetch", vi.fn(async (...args: Parameters<typeof fetch>) => {
    if (revoked && String(args[0]).endsWith("/me")) return new Response(null, { status: 401 });
    return normalFetch(...args);
  }));
  const { refreshServerSession } = await import("../../lib/auth/ssr-refresh");
  const token = `revoked-${crypto.randomUUID()}`;
  expect((await refreshServerSession(request(token, "/cart"))).status).toBe(303);
  revoked = true;
  const result = await refreshServerSession(request(token, "/cart"));
  expect(result.headers.get("location")).toContain("/login?");
  expect(result.headers.getSetCookie().every(cookie => cookie.includes("Max-Age=0"))).toBe(true);
  expect(posts()).toBe(1);
});
it.skipIf(!enabled)("keeps the owner's new cookies if Redis publication fails", async () => {
  const token = `lost-publication-${crypto.randomUUID()}`;
  const store = createRedisRefreshStore(config.REDIS_URL!);
  store.publish = async () => { throw new Error("publication lost"); };
  const expected = { status: 200, cookies: ["covie_rt=rotated; HttpOnly"] };
  expect(await coordinateRefresh(store, token, async begin => { begin(); return expected; })).toEqual(expected);
});
it.skipIf(!enabled)("refuses rotation on Redis configured to lose guards on restart", async () => {
  await redis.configSet("appendonly", "no");
  try {
    const posts = backend();
    const { refreshServerSession } = await import("../../lib/auth/ssr-refresh");
    const result = await refreshServerSession(request(`unsafe-${crypto.randomUUID()}`, "/cart"));
    expect(result.status).toBe(503);
    expect(result.headers.getSetCookie()).toEqual([]);
    expect(posts()).toBe(0);
  } finally { await redis.configSet("appendonly", "yes"); }
});
it.skipIf(!enabled)("initializes an HttpOnly result binding before rotation", async () => {
  const posts = backend();
  const { refreshServerSession } = await import("../../lib/auth/ssr-refresh");
  const incoming = request(`bootstrap-${crypto.randomUUID()}`, "/cart");
  incoming.headers.set("cookie", "covie_rt=bootstrap-refresh");
  const response = await refreshServerSession(incoming);
  expect(response.status).toBe(303);
  expect(response.headers.get("location")).toContain("/api/v1/auth/ssr-refresh?");
  expect(response.headers.getSetCookie()[0]).toMatch(/covie_refresh_binding=[A-Za-z0-9_-]{43};.*HttpOnly/);
  expect(posts()).toBe(0);
});
it.skipIf(!enabled)("does not disclose a shared result to a holder of only the old Refresh Token", async () => {
  const posts = backend();
  const { refreshServerSession } = await import("../../lib/auth/ssr-refresh");
  const token = `binding-${crypto.randomUUID()}`;
  expect((await refreshServerSession(request(token, "/cart"))).status).toBe(303);
  const differentBrowser = request(token, "/me");
  differentBrowser.headers.set("cookie", `covie_rt=${token}; covie_refresh_binding=${"B".repeat(43)}`);
  const response = await refreshServerSession(differentBrowser);
  expect(response.status).toBe(503);
  expect(response.headers.getSetCookie()).toEqual([]);
  expect(posts()).toBe(1);
});
