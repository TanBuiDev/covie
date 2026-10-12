// @vitest-environment node
import { afterEach, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
const mocks = vi.hoisted(() => ({ use: vi.fn(), headers: vi.fn(), redirect: vi.fn(() => { throw new Error("NEXT_REDIRECT"); }) }));
vi.mock("next/headers", () => ({ headers: mocks.headers }));
vi.mock("next/navigation", () => ({ redirect: mocks.redirect }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => ({ use: mocks.use }) }));
vi.mock("@/lib/env/server", () => ({ serverEnv: { API_INTERNAL_BASE_URL: "http://api:8080", COVIE_PUBLIC_URL: "https://covie.test" } }));
import { createServerReadClient } from "@/lib/api/server-api-client";
import { proxy } from "../../proxy";
afterEach(() => vi.clearAllMocks());
it("overwrites a supplied destination with the actual page and query", () => {
  const response = proxy(new NextRequest("https://covie.test/me/orders?status=active", { headers: { "x-covie-ssr-return-to": "/admin" } }));
  expect(response.headers.get("x-middleware-request-x-covie-ssr-return-to")).toBe("/me/orders?status=active");
});
it("redirects an expired server read to the cookie-writing bridge and retains its destination", async () => {
  mocks.headers.mockResolvedValue(new Headers({ cookie: "covie_at=expired", "x-covie-ssr-return-to": "/me/orders?status=active" }));
  await createServerReadClient();
  const middleware = mocks.use.mock.calls[0]![0];
  const request = new Request("http://api:8080/api/v1/me");
  const forwarded = await middleware.onRequest({ request });
  expect(forwarded.headers.get("cookie")).toBe("covie_at=expired");
  expect(forwarded.cache).toBe("no-store");
  await expect(middleware.onResponse({ request, response: new Response(null, { status: 401 }) })).rejects.toThrow("NEXT_REDIRECT");
  expect(mocks.redirect).toHaveBeenCalledWith("/api/v1/auth/ssr-refresh?returnTo=%2Fme%2Forders%3Fstatus%3Dactive");
  const unavailable = new Response(null, { status: 503 });
  expect(await middleware.onResponse({ request, response: unavailable })).toBe(unavailable);
});
it("does not forward authentication cookies or refresh a different API origin", async () => {
  mocks.headers.mockResolvedValue(new Headers({ cookie: "covie_at=private" }));
  await createServerReadClient();
  const middleware = mocks.use.mock.calls[0]![0];
  const request = new Request("https://other.test/api/v1/me");
  expect((await middleware.onRequest({ request })).headers.has("cookie")).toBe(false);
  const rejected = new Response(null, { status: 401 });
  expect(await middleware.onResponse({ request, response: rejected })).toBe(rejected);
  expect(mocks.redirect).not.toHaveBeenCalled();
});
