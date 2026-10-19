import { test } from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import { once } from "node:events";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

async function runLoad(origin) {
  const child = spawn(process.execPath, [fileURLToPath(new URL("./run-load.mjs", import.meta.url)),
    "--base-url", origin, "--scenario", "public-social-summary", "--concurrency", "1", "--duration", "40ms"], {
    env: { ...process.env, COVIE_SOCIAL_LOAD_ITEM_IDS: "11111111-1111-4111-8111-111111111111" },
    stdio: ["ignore", "pipe", "pipe"],
  });
  let stdout = "";
  let stderr = "";
  child.stdout.on("data", chunk => { stdout += chunk; });
  child.stderr.on("data", chunk => { stderr += chunk; });
  const timer = setTimeout(() => child.kill(), 6000);
  try {
    const [code] = await once(child, "close");
    return { code, summary: JSON.parse(stdout), stderr };
  } finally { clearTimeout(timer); }
}

test("load latency and byte counts include the complete delayed UTF-8 response body", async () => {
  const payload = Buffer.from(JSON.stringify({ socialCaption: "Tiếng Việt\n".repeat(80), media: [{ url: "https://fixture.invalid/image.png" }] }));
  const server = createServer((_request, response) => {
    response.writeHead(200, { "Content-Type": "application/json", "Content-Length": payload.byteLength });
    response.write(payload.subarray(0, 16));
    const timer = setTimeout(() => response.end(payload.subarray(16)), 700);
    response.on("close", () => clearTimeout(timer));
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    const result = await runLoad("http://127.0.0.1:" + server.address().port);
    assert.equal(result.code, 1, "a response body slower than the existing 400ms objective must not pass");
    assert.equal(result.summary.total, 1);
    assert.equal(result.summary.successful, 1);
    assert.ok(result.summary.latencyMs.p95 >= 600);
    assert.deepEqual(result.summary.responseBodyBytes, {
      completedResponses: 1, total: payload.byteLength, min: payload.byteLength, max: payload.byteLength, mean: payload.byteLength,
    });
    assert.match(result.stderr, /Pilot load objective failed/);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});

test("HTTP200 with a truncated body is a failed transfer, not a successful load sample", async () => {
  const server = createServer((_request, response) => {
    response.writeHead(200, { "Content-Type": "application/json", "Content-Length": 2048 });
    response.write("{\"items\":[");
    const timer = setTimeout(() => response.destroy(), 60);
    response.on("close", () => clearTimeout(timer));
  });
  server.listen(0, "127.0.0.1");
  await once(server, "listening");
  try {
    const result = await runLoad("http://127.0.0.1:" + server.address().port);
    assert.equal(result.code, 1);
    assert.equal(result.summary.successful, 0);
    assert.equal(result.summary.networkErrors, result.summary.total);
    assert.equal(result.summary.errorRatio, 1);
    assert.equal(result.summary.responseBodyBytes.completedResponses, 0);
    assert.equal(result.summary.responseBodyBytes.total, 0);
  } finally { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }
});
