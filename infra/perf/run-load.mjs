#!/usr/bin/env node
import { performance } from "node:perf_hooks";
import process from "node:process";
import { resolveScenario, scenarios } from "./scenarios.mjs";

function usage(code = 0) {
  console.log(`Usage: node infra/perf/run-load.mjs --base-url https://staging.covie.vn --scenario public-search [--concurrency 50] [--duration 120s]\n\nScenarios: ${Object.keys(scenarios).join(", ")}`);
  process.exit(code);
}

function args(argv) {
  const out = { concurrency: 50, durationMs: 120_000 };
  for (let i = 0; i < argv.length; i++) {
    const key = argv[i];
    if (key === "--help" || key === "-h") usage(0);
    const value = argv[++i];
    if (key === "--base-url") out.baseUrl = value;
    else if (key === "--scenario") out.scenario = value;
    else if (key === "--concurrency") out.concurrency = Number.parseInt(value, 10);
    else if (key === "--duration") out.durationMs = parseDuration(value);
    else throw new Error(`Unknown argument ${key}`);
  }
  if (!out.baseUrl || !out.scenario) usage(2);
  if (!/^https?:\/\//.test(out.baseUrl)) throw new Error("--base-url must be an absolute HTTP(S) URL");
  if (!Number.isInteger(out.concurrency) || out.concurrency < 1 || out.concurrency > 500) throw new Error("--concurrency must be 1..500");
  return out;
}

function parseDuration(value) {
  const match = /^(\d+)(ms|s|m)$/.exec(value ?? "");
  if (!match) throw new Error("--duration must look like 500ms, 120s or 10m");
  const amount = Number(match[1]);
  return amount * (match[2] === "ms" ? 1 : match[2] === "s" ? 1000 : 60_000);
}

function percentile(sorted, p) {
  if (!sorted.length) return 0;
  const index = Math.min(sorted.length - 1, Math.ceil(sorted.length * p) - 1);
  return sorted[index];
}

const options = args(process.argv.slice(2));
const scenario = resolveScenario(options.scenario);
const endpoint = new URL(scenario.path, options.baseUrl).toString();
const deadline = performance.now() + options.durationMs;
const latencies = [];
const statusCounts = new Map();
let total = 0;
let networkErrors = 0;
let successful = 0;
let completedResponses = 0;
let completedBodyBytes = 0;
let smallestBodyBytes = Infinity;
let largestBodyBytes = 0;

async function worker() {
  while (performance.now() < deadline) {
    const started = performance.now();
    try {
      const headers = { accept: "application/json" };
      if (scenario.authCookie) headers.cookie = scenario.authCookie;
      const response = await fetch(endpoint, {
        method: scenario.method,
        headers,
        redirect: "manual",
        signal: AbortSignal.timeout(15_000),
      });
      let bodyBytes = 0;
      if (response.body) {
        for await (const chunk of response.body) bodyBytes += chunk.byteLength;
      }
      completedResponses++;
      completedBodyBytes += bodyBytes;
      smallestBodyBytes = Math.min(smallestBodyBytes, bodyBytes);
      largestBodyBytes = Math.max(largestBodyBytes, bodyBytes);
      const elapsed = performance.now() - started;
      latencies.push(elapsed);
      total++;
      statusCounts.set(response.status, (statusCounts.get(response.status) ?? 0) + 1);
      if (scenario.expected.includes(response.status)) successful++;
    } catch {
      total++;
      networkErrors++;
      latencies.push(performance.now() - started);
    }
  }
}

await Promise.all(Array.from({ length: options.concurrency }, () => worker()));
latencies.sort((a, b) => a - b);
const failed = total - successful;
const errorRatio = total === 0 ? 1 : failed / total;
const summary = {
  scenario: options.scenario,
  target: endpoint,
  concurrency: options.concurrency,
  durationMs: options.durationMs,
  total,
  successful,
  failed,
  networkErrors,
  errorRatio: Number(errorRatio.toFixed(4)),
  responseBodyBytes: {
    completedResponses,
    total: completedBodyBytes,
    min: completedResponses ? smallestBodyBytes : 0,
    max: largestBodyBytes,
    mean: completedResponses ? Math.round(completedBodyBytes / completedResponses) : 0,
  },
  latencyMs: {
    p50: Math.round(percentile(latencies, 0.50)),
    p95: Math.round(percentile(latencies, 0.95)),
    p99: Math.round(percentile(latencies, 0.99)),
    max: Math.round(latencies.at(-1) ?? 0),
  },
  statusCounts: Object.fromEntries([...statusCounts.entries()].sort(([a], [b]) => a - b)),
  thresholds: { p95Ms: scenario.p95Ms, maxErrorRatio: scenario.maxErrorRatio },
};
console.log(JSON.stringify(summary, null, 2));

if (summary.latencyMs.p95 > scenario.p95Ms || errorRatio >= scenario.maxErrorRatio) {
  console.error("Pilot load objective failed");
  process.exit(1);
}
