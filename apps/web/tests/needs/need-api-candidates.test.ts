import { afterEach, expect, it, vi } from "vitest";
import { listEligibleNeedItems } from "@/features/needs/need-api";

const { requestBrowserApi } = vi.hoisted(() => ({ requestBrowserApi: vi.fn() }));
vi.mock("@/lib/api/api-client", () => ({ requestBrowserApi }));

afterEach(() => requestBrowserApi.mockReset());

it("encodes merchant, Need, selected location and cursor in the generated eligible-items GET path", async () => {
  const page = { items: [], hasMore: false, nextCursor: null, maxResponseItems: 3 };
  requestBrowserApi.mockResolvedValueOnce(new Response(JSON.stringify(page), { status: 200 }));

  await expect(listEligibleNeedItems("merchant/1", "need/2", "location+3", "cursor+/=")).resolves.toEqual(page);
  const [method, path] = requestBrowserApi.mock.calls[0] as [string, string];
  expect(method).toBe("GET");
  const url = new URL(path, "https://covie.example");
  expect(url.pathname).toBe("/api/v1/merchants/merchant%2F1/need-requests/need%2F2/eligible-items");
  expect(url.searchParams.get("locationId")).toBe("location+3");
  expect(url.searchParams.get("cursor")).toBe("cursor+/=");
  expect(url.searchParams.get("limit")).toBe("20");
});
