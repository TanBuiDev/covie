// Isolated child used only by the disposable Redis integration test.
import { createClient } from "redis";
import { coordinateRefresh } from "../../lib/auth/refresh-coordinator.ts";

let raw = "";
for await (const chunk of process.stdin) raw += chunk;
const { redisUrl, token, mode = "normal" } = JSON.parse(raw);
const client = createClient({ url: redisUrl, socket: { reconnectStrategy: false } });
client.on("error", () => {});
await client.connect();
// Import production adapter through TypeScript's transpileModule because Node's stripped
// TypeScript does not resolve its extensionless relative import.
import ts from "typescript";
import { readFile } from "node:fs/promises";
const source = (await readFile(new URL("../../lib/auth/redis-refresh-store.ts", import.meta.url), "utf8"))
  .replace('from "./refresh-coordinator"', `from "${new URL("../../lib/auth/refresh-coordinator.ts", import.meta.url).href}"`)
  .replace('from "redis"', `from "${import.meta.resolve("redis")}"`);
const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.ESNext, target: ts.ScriptTarget.ES2022 } }).outputText;
const { createRedisRefreshStore } = await import(`data:text/javascript;base64,${Buffer.from(js).toString("base64")}`);
const result = await coordinateRefresh(createRedisRefreshStore(redisUrl), token, async begin => {
  begin();
  await client.incr(`covie:test:posts:${token}`);
  if (mode === "crash") process.exit(0);
  await new Promise(resolve => setTimeout(resolve, 1800));
  return { status: 200, cookies: ["covie_at=worker-access; Path=/; HttpOnly", "covie_rt=worker-refresh; Path=/api/v1/auth; HttpOnly"] };
});
process.stdout.write(JSON.stringify(result));
client.destroy();
// The production connection intentionally remains open for server reuse.
process.exit(0);
