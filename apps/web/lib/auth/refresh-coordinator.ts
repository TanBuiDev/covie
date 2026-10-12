import { createCipheriv, createDecipheriv, createHash, hkdfSync, randomBytes, randomUUID } from "node:crypto";

export type RefreshExchange = { status: number; cookies: string[]; retrySafe?: boolean };
export type RefreshClaim =
  | { kind: "owner" }
  | { kind: "pending"; startedAt: number }
  | { kind: "spent" }
  | { kind: "result"; value: string };
export interface RefreshStore {
  claim(key: string, owner: string): Promise<RefreshClaim>;
  publish(key: string, owner: string, value: string, rejected?: boolean): Promise<boolean>;
  release(key: string, owner: string): Promise<void>;
}
// Longer than the three bounded backend calls; never reacquire a consumed token.
export const REFRESH_WAIT_MS = 32_000;
export const REFRESH_RESULT_MS = 5_000;
export const REFRESH_GUARD_SECONDS = 30 * 24 * 60 * 60 + 60;
const unavailable = (): RefreshExchange => ({ status: 503, cookies: [] });

function encryptionKey(token: string, key: string, binding: string) {
  // Only a holder of the high-entropy RT can decrypt; Redis never receives RT/plaintext cookies.
  return Buffer.from(hkdfSync("sha256", token, "covie:ssr-refresh:v1", `${key}:${binding}`, 32));
}
function seal(token: string, key: string, binding: string, result: RefreshExchange) {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", encryptionKey(token, key, binding), iv);
  cipher.setAAD(Buffer.from(key));
  const payload = Buffer.concat([cipher.update(JSON.stringify({ ...result, issuedAt: Date.now() })), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), payload]).toString("base64url");
}
function open(token: string, key: string, binding: string, value: string): RefreshExchange {
  const bytes = Buffer.from(value, "base64url");
  const cipher = createDecipheriv("aes-256-gcm", encryptionKey(token, key, binding), bytes.subarray(0, 12));
  cipher.setAuthTag(bytes.subarray(12, 28));
  cipher.setAAD(Buffer.from(key));
  const result = JSON.parse(Buffer.concat([cipher.update(bytes.subarray(28)), cipher.final()]).toString()) as RefreshExchange & { issuedAt: number };
  const age = Date.now() - result.issuedAt;
  if (!Number.isFinite(age) || age < 0 || age >= REFRESH_RESULT_MS
      || ![200, 401, 429, 503].includes(result.status)
      || !Array.isArray(result.cookies) || !result.cookies.every(cookie => typeof cookie === "string")) return unavailable();
  // Replaying a result must not extend either cookie's original lifetime.
  const elapsed = Math.ceil(age / 1000);
  return { status: result.status, cookies: result.cookies.map(cookie => cookie.replace(/Max-Age=(\d+)/i,
    (_, seconds: string) => `Max-Age=${Math.max(0, Number(seconds) - elapsed)}`)) };
}

export async function coordinateRefresh(
  store: RefreshStore, token: string,
  work: (beforeRotation: () => void) => Promise<RefreshExchange>,
  binding: string = "",
): Promise<RefreshExchange> {
  const key = createHash("sha256").update(token).digest("hex");
  const owner = `${randomUUID()}:${Date.now()}`;
  const deadline = Date.now() + REFRESH_WAIT_MS;
  try {
    while (true) {
      const claim = await store.claim(key, owner);
      if (claim.kind === "result") return open(token, key, binding, claim.value);
      if (claim.kind === "spent") return unavailable();
      if (claim.kind === "pending") {
        if (Date.now() >= deadline || Date.now() - claim.startedAt >= REFRESH_WAIT_MS) return unavailable();
        await new Promise(resolve => setTimeout(resolve, 75));
        continue;
      }
      let attempted = false;
      let result: RefreshExchange;
      try { result = await work(() => { attempted = true; }); }
      catch { result = unavailable(); }
      // Retry is safe only when POST was never attempted. A network error after POST is ambiguous.
      if (!attempted || result.retrySafe) {
        try { await store.release(key, owner); } catch { /* Fail closed for followers. */ }
        return result;
      }
      try { await store.publish(key, owner, seal(token, key, binding, result), result.status === 401); }
      catch { /* Still give the owner its rotated cookies; followers keep their existing cookies. */ }
      return result;
    }
  } catch { return unavailable(); }
}
