import { act, cleanup, renderHook, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { CovieApiError } from "@/lib/api/api-error";
import { useSocialActivity } from "./use-social-activity";

const api = vi.hoisted(() => ({ fetchMySocialActivity: vi.fn() }));
vi.mock("./social-activity-api", () => api);
afterEach(() => { cleanup(); vi.resetAllMocks(); });

it("appends cursor pages, retries the failed page and preserves separate comments on one item", async () => {
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [{ itemId: "item", commentId: "comment-1" }], nextCursor: "page-2" })
    .mockRejectedValueOnce(new Error("offline"))
    .mockResolvedValueOnce({ items: [{ itemId: "item", commentId: "comment-1" }, { itemId: "item", commentId: "comment-2" }], nextCursor: null });
  const hook = renderHook(() => useSocialActivity({ accountId: "buyer", type: "COMMENTED", enabled: true }));
  await waitFor(() => expect(hook.result.current.status).toBe("ready"));
  act(() => { hook.result.current.loadMore(); hook.result.current.loadMore(); });
  await waitFor(() => expect(hook.result.current.status).toBe("error"));
  expect(hook.result.current.items).toHaveLength(1);
  act(() => hook.result.current.retry());
  await waitFor(() => expect(hook.result.current.items).toHaveLength(2));
  expect(api.fetchMySocialActivity.mock.calls.map(call => call[1])).toEqual([undefined, "page-2", "page-2"]);
  expect(hook.result.current.nextCursor).toBeUndefined();
});

it("clears old rows/cursor on account or type changes and ignores late completions", async () => {
  let finish!: (page: unknown) => void;
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [{ itemId: "private-A" }], nextCursor: "A-cursor" })
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockResolvedValueOnce({ items: [{ itemId: "private-B" }] });
  const hook = renderHook(({ accountId, type }) => useSocialActivity({ accountId, type, enabled: true }), {
    initialProps: { accountId: "A", type: "LIKED" as "LIKED" | "SAVED" },
  });
  await waitFor(() => expect(hook.result.current.items).toHaveLength(1));
  act(() => hook.result.current.loadMore());
  hook.rerender({ accountId: "B", type: "SAVED" });
  expect(hook.result.current.items).toEqual([]);
  await waitFor(() => expect(hook.result.current.items[0]?.itemId).toBe("private-B"));
  await act(async () => finish({ items: [{ itemId: "late-A" }] }));
  expect(hook.result.current.items.map(row => row.itemId)).toEqual(["private-B"]);
  expect(api.fetchMySocialActivity.mock.calls[2]?.slice(0, 2)).toEqual(["SAVED", undefined]);
});

it.each([401, 403])("clears private rows on authorization %s and does not fetch while disabled", async (status) => {
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [{ itemId: "private" }], nextCursor: "next" })
    .mockRejectedValueOnce(new CovieApiError(status, "AUTHENTICATION_REQUIRED", "Sign in"));
  const hook = renderHook(({ enabled }) => useSocialActivity({ accountId: "buyer", type: "LIKED", enabled }), { initialProps: { enabled: false } });
  expect(api.fetchMySocialActivity).not.toHaveBeenCalled();
  hook.rerender({ enabled: true });
  await waitFor(() => expect(hook.result.current.items).toHaveLength(1));
  act(() => hook.result.current.loadMore());
  await waitFor(() => expect(hook.result.current.status).toBe("unauthorized"));
  expect(hook.result.current.items).toEqual([]);
  expect(hook.result.current.nextCursor).toBeUndefined();
});

it("revalidates on refocus while a page is pending and cannot restore its stale private rows", async () => {
  let finish!: (page: unknown) => void;
  api.fetchMySocialActivity.mockResolvedValueOnce({ items: [{ itemId: "private-before" }], nextCursor: "old-page" })
    .mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
    .mockRejectedValueOnce(new CovieApiError(403, "FORBIDDEN", "Access changed"));
  const hook = renderHook(() => useSocialActivity({ accountId: "buyer", type: "LIKED", enabled: true }));
  await waitFor(() => expect(hook.result.current.status).toBe("ready"));
  act(() => hook.result.current.loadMore());
  const pendingSignal = api.fetchMySocialActivity.mock.calls[1]?.[2] as AbortSignal;
  act(() => window.dispatchEvent(new Event("focus")));
  await waitFor(() => expect(api.fetchMySocialActivity).toHaveBeenCalledTimes(3));
  expect(pendingSignal.aborted).toBe(true);
  await waitFor(() => expect(hook.result.current.status).toBe("unauthorized"));
  await act(async () => finish({ items: [{ itemId: "stale-private" }], nextCursor: "stale-cursor" }));
  expect(hook.result.current.items).toEqual([]);
  expect(hook.result.current.nextCursor).toBeUndefined();
  expect(api.fetchMySocialActivity.mock.calls[2]?.slice(0, 2)).toEqual(["LIKED", undefined]);
});
