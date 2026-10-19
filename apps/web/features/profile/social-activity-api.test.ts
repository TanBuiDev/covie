import { afterEach, expect, it, vi } from "vitest";
import { fetchMySocialActivity } from "./social-activity-api";

const transport = vi.hoisted(() => ({ GET: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ createApiClient: () => transport }));
afterEach(() => vi.resetAllMocks());

it("uses the generated private activity query without any user selector or browser cache", async () => {
  const signal = new AbortController().signal;
  transport.GET.mockResolvedValue({ data: { items: [], nextCursor: "opaque" }, response: new Response() });
  expect(await fetchMySocialActivity("COMMENTED", "cursor", signal)).toEqual({ items: [], nextCursor: "opaque" });
  expect(transport.GET).toHaveBeenCalledWith("/api/v1/me/social/activity", {
    params: { query: { type: "COMMENTED", limit: 20, cursor: "cursor" } }, credentials: "include", cache: "no-store", signal,
  });
});

it("keeps parsed authentication errors when the generated client already consumed the response", async () => {
  const response = new Response(JSON.stringify({ error: { code: "AUTHENTICATION_REQUIRED", message: "Sign in" } }), { status: 401 });
  const error = await response.json();
  transport.GET.mockResolvedValue({ response, error });
  await expect(fetchMySocialActivity("LIKED")).rejects.toMatchObject({ status: 401, code: "AUTHENTICATION_REQUIRED" });
});
