// @vitest-environment node
import { afterEach, expect, it, vi } from "vitest";
import { coordinateRefresh, REFRESH_RESULT_MS, REFRESH_WAIT_MS, type RefreshStore } from "../../lib/auth/refresh-coordinator";

function storage() {
  const entries = new Map<string, { owner: string; value?: string; until?: number }>();
  const store: RefreshStore = {
    async claim(key, owner) {
      const record = entries.get(key);
      if (record?.value && record.until! > Date.now()) return { kind: "result", value: record.value };
      if (record?.value) return { kind: "spent" };
      if (record) return { kind: "pending", startedAt: Number(record.owner.split(":")[1]) };
      entries.set(key, { owner }); return { kind: "owner" };
    },
    async publish(key, owner, value) {
      if (entries.get(key)?.owner !== owner) return false;
      entries.set(key, { owner, value, until: Date.now() + REFRESH_RESULT_MS }); return true;
    },
    async release(key, owner) { if (entries.get(key)?.owner === owner) entries.delete(key); },
  };
  return { entries, store };
}
afterEach(() => vi.useRealTimers());
const success = { status: 200, cookies: ["covie_at=access; Max-Age=900", "covie_rt=refresh; Max-Age=2592000"] };
it("does not rotate again after the result window closes", async () => {
  vi.useFakeTimers();
  const { store } = storage();
  let posts = 0;
  const work = async (begin: () => void) => { begin(); posts++; return success; };
  await coordinateRefresh(store, "expiry-token", work);
  await vi.advanceTimersByTimeAsync(REFRESH_RESULT_MS + 1);
  expect(await coordinateRefresh(store, "expiry-token", work)).toEqual({ status: 503, cookies: [] });
  expect(posts).toBe(1);
});
it("never reacquires an orphaned claim after a process disappears", async () => {
  vi.useFakeTimers();
  const { store, entries } = storage();
  const unfinished = coordinateRefresh(store, "crash-token", async begin => {
    begin(); return new Promise(() => {});
  });
  void unfinished;
  await vi.advanceTimersByTimeAsync(REFRESH_WAIT_MS + 1);
  const replacement = vi.fn(async () => success);
  expect(await coordinateRefresh(store, "crash-token", replacement)).toEqual({ status: 503, cookies: [] });
  expect(replacement).not.toHaveBeenCalled();
  expect(entries.size).toBe(1);
});
it("allows a safe retry when CSRF fails before POST", async () => {
  const { store } = storage();
  expect(await coordinateRefresh(store, "csrf-token", async () => ({ status: 503, cookies: [] })))
    .toEqual({ status: 503, cookies: [] });
  expect(await coordinateRefresh(store, "csrf-token", async begin => { begin(); return success; })).toEqual(success);
});
it("allows retry after an explicit backend rate limit before rotation", async () => {
  const { store } = storage();
  const limited = { status: 429, cookies: [], retrySafe: true };
  expect(await coordinateRefresh(store, "limited-token", async begin => { begin(); return limited; })).toEqual(limited);
  expect(await coordinateRefresh(store, "limited-token", async begin => { begin(); return success; })).toEqual(success);
});
it("does not retry an ambiguous network failure after POST", async () => {
  const { store } = storage();
  await coordinateRefresh(store, "network-token", async begin => { begin(); throw new Error("lost response"); });
  const retry = vi.fn(async () => success);
  expect(await coordinateRefresh(store, "network-token", retry)).toEqual({ status: 503, cookies: [] });
  expect(retry).not.toHaveBeenCalled();
});
it("preserves the owner's cookies when publishing the result fails", async () => {
  const { store } = storage();
  store.publish = async () => { throw new Error("Redis connection lost"); };
  expect(await coordinateRefresh(store, "publish-token", async begin => { begin(); return success; })).toEqual(success);
});
it("rejects tampered encrypted results without calling the backend", async () => {
  const { store, entries } = storage();
  await coordinateRefresh(store, "tamper-token", async begin => { begin(); return success; });
  entries.values().next().value!.value = "invalid-ciphertext";
  const retry = vi.fn(async () => success);
  expect(await coordinateRefresh(store, "tamper-token", retry)).toEqual({ status: 503, cookies: [] });
  expect(retry).not.toHaveBeenCalled();
});
it("reduces cookie lifetime when delivering a shared result later", async () => {
  vi.useFakeTimers();
  const { store } = storage();
  await coordinateRefresh(store, "age-token", async begin => { begin(); return success; });
  await vi.advanceTimersByTimeAsync(1100);
  const result = await coordinateRefresh(store, "age-token", async () => { throw new Error("must not rotate"); });
  expect(result.cookies).toEqual(["covie_at=access; Max-Age=898", "covie_rt=refresh; Max-Age=2591998"]);
});
