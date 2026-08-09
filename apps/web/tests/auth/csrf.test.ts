import { afterEach, describe, expect, it, vi } from "vitest";
import { createApiClient, requestBrowserApi } from "../../lib/api/api-client";
import { readApiError } from "../../lib/api/api-error";
import { getCsrfToken, invalidateCsrfToken } from "../../features/auth/csrf";
import { refreshSession } from "../../features/auth/auth-session";

const json = (value: unknown, status = 200) =>
  new Response(JSON.stringify(value), { status, headers: { "content-type": "application/json" } });

afterEach(() => {
  invalidateCsrfToken();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("browser CSRF middleware", () => {
  it("does not fetch CSRF for a safe request", async () => {
    const fetchMock = vi.fn().mockResolvedValue(json({ status: "ok" }));
    vi.stubGlobal("fetch", fetchMock);

    await createApiClient().GET("/api/v1/health");

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(new Request(fetchMock.mock.calls[0]![0]).url).toContain("/api/v1/health");
  });

  it("attaches a fetched CSRF token to the first unsafe request", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "first-token" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await unsafeRequest("POST", "/api/v1/example");

    expect(fetchMock).toHaveBeenCalledTimes(2);
    expect(new Request(fetchMock.mock.calls[1]![0]).headers.get("X-CSRF-Token")).toBe("first-token");
  });

  it("reuses the cached CSRF token for later unsafe requests", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "cached-token" }))
      .mockResolvedValue(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    await unsafeRequest("POST", "/api/v1/one");
    await unsafeRequest("DELETE", "/api/v1/two");

    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(new Request(fetchMock.mock.calls[2]![0]).headers.get("X-CSRF-Token")).toBe("cached-token");
  });

  it("obtains a fresh CSRF token after invalidation", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "old-token" }))
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "new-token" }));
    vi.stubGlobal("fetch", fetchMock);

    await getCsrfToken();
    invalidateCsrfToken();
    const token = await getCsrfToken();

    expect(token.token).toBe("new-token");
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("retries CSRF loading after a previous token request failed", async () => {
    const fetchMock = vi.fn()
      .mockRejectedValueOnce(new Error("network unavailable"))
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "recovered-token" }));
    vi.stubGlobal("fetch", fetchMock);

    await expect(getCsrfToken()).rejects.toThrow("network unavailable");
    await expect(getCsrfToken()).resolves.toEqual({ headerName: "X-CSRF-Token", token: "recovered-token" });

    expect(fetchMock).toHaveBeenCalledTimes(2);
  });

  it("invalidates CSRF after a CSRF-specific 403 without replaying the mutation", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "stale-token" }))
      .mockResolvedValueOnce(json({ error: { code: "CSRF_INVALID", message: "expired" } }, 403))
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "fresh-token" }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal("fetch", fetchMock);

    const rejectedResponse = await requestBrowserApi("POST", "/api/v1/example");
    const error = await readApiError(rejectedResponse);

    expect(error.code).toBe("CSRF_INVALID");
    expect(fetchMock).toHaveBeenCalledTimes(2);
    await requestBrowserApi("POST", "/api/v1/example");
    expect(new Request(fetchMock.mock.calls[3]![0]).headers.get("X-CSRF-Token")).toBe("fresh-token");
  });
});

describe("session refresh coordination", () => {
  it("uses one refresh request for simultaneous callers", async () => {
    let resolveRefresh: ((response: Response) => void) | undefined;
    const fetchMock = vi.fn((input: RequestInfo | URL) => {
      const url = typeof input === "string" ? input : new Request(input).url;
      if (url.endsWith("/csrf")) return Promise.resolve(json({ headerName: "X-CSRF-Token", token: "token" }));
      return new Promise<Response>((resolve) => { resolveRefresh = resolve; });
    });
    vi.stubGlobal("fetch", fetchMock);

    const first = refreshSession();
    const second = refreshSession();
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
    resolveRefresh?.(new Response(null, { status: 200 }));
    await Promise.all([first, second]);

    expect(fetchMock.mock.calls.filter(([request]) => (typeof request === "string" ? request : new Request(request).url).endsWith("/refresh"))).toHaveLength(1);
  });

  it("retries a safe resource read once after a successful refresh", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "token" }))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))
      .mockResolvedValueOnce(json({ status: "ok" }));
    vi.stubGlobal("fetch", fetchMock);

    const result = await createApiClient().GET("/api/v1/health");

    expect(result.response.status).toBe(200);
    expect(fetchMock).toHaveBeenCalledTimes(4);
  });

  it("does not replay an unsafe mutation after a 401", async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(json({ headerName: "X-CSRF-Token", token: "token" }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }));
    vi.stubGlobal("fetch", fetchMock);

    const response = await unsafeRequest("POST", "/api/v1/example");

    expect(response.status).toBe(401);
    expect(fetchMock).toHaveBeenCalledTimes(2);
  });
});

async function unsafeRequest(method: "POST" | "DELETE", path: string): Promise<Response> {
  const client = createApiClient() as unknown as {
    request(method: string, path: string): Promise<{ response: Response }>;
  };
  return (await client.request(method, path)).response;
}
