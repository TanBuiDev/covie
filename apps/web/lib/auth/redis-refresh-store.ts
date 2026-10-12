import { createClient } from "redis";
import { REFRESH_GUARD_SECONDS, REFRESH_RESULT_MS, type RefreshClaim, type RefreshStore } from "./refresh-coordinator";

// Atomic result lookup + claim. A pending/spent marker outlives the RT; it is never reclaimed.
const claimScript = `
local result = redis.call('GET', KEYS[2])
if result then return {'result', result} end
local guard = redis.call('GET', KEYS[1])
if guard then
  if guard == 'spent' then return {'spent'} end
  return {'pending', guard}
end
redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2], 'NX')
return {'owner'}
`;
const publishScript = `
if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[3])
redis.call('SET', KEYS[1], 'spent', 'KEEPTTL')
if ARGV[4] == 'rejected' then redis.call('PEXPIRE', KEYS[1], ARGV[3]) end
return 1
`;
const releaseScript = `
if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
return 0
`;
export function createRedisRefreshStore(url: string): RefreshStore {
  // No offline command queue or automatic reconnect/replay of an ambiguous mutation.
  const client = createClient({ url, disableOfflineQueue: true,
    socket: { connectTimeout: 1_000, reconnectStrategy: false } });
  client.on("error", () => {}); // Never log credentials from Redis connection errors.
  let connecting: Promise<unknown> | undefined;
  let verified = false;
  let verification: Promise<void> | undefined;
  client.on("end", () => { verified = false; });
  async function command(args: string[]): Promise<unknown> {
    if (!client.isReady) {
      connecting ??= client.connect().finally(() => { connecting = undefined; });
      await connecting;
    }
    if (!verified) {
      verification ??= (async () => {
        const config = await client.withAbortSignal(AbortSignal.timeout(1_000))
          .configGet(["appendonly", "appendfsync", "maxmemory-policy"]);
        if (config.appendonly !== "yes" || config.appendfsync !== "always" || config["maxmemory-policy"] !== "noeviction")
          throw new Error("Refresh coordination requires durable non-evicting Redis");
        verified = true;
      })().finally(() => { verification = undefined; });
      await verification;
    }
    return client.sendCommand(args, { abortSignal: AbortSignal.timeout(1_000) });
  }
  const keys = (key: string) => [`covie:ssr-refresh:{${key}}:guard`, `covie:ssr-refresh:{${key}}:result`];
  return {
    async claim(key, owner): Promise<RefreshClaim> {
      const value = await command(["EVAL", claimScript, "2", ...keys(key), owner, String(REFRESH_GUARD_SECONDS)]) as string[];
      if (value[0] === "owner" || value[0] === "spent") return { kind: value[0] };
      if (value[0] === "result" && value[1]) return { kind: "result", value: value[1] };
      if (value[0] === "pending" && value[1]) {
        const startedAt = Number(value[1].split(":")[1]);
        if (Number.isFinite(startedAt)) return { kind: "pending", startedAt };
      }
      throw new Error("Invalid refresh coordination state");
    },
    async publish(key, owner, value, rejected = false) {
      return await command(["EVAL", publishScript, "2", ...keys(key), owner, value, String(REFRESH_RESULT_MS), rejected ? "rejected" : "spent"]) === 1;
    },
    async release(key, owner) {
      await command(["EVAL", releaseScript, "1", keys(key)[0]!, owner]);
    },
  };
}
