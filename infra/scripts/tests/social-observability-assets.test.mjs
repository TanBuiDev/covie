import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { mkdtempSync, mkdirSync, readFileSync, realpathSync, rmSync, writeFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../../../", import.meta.url));
const dashboard = JSON.parse(readFileSync(path.join(root, "infra/observability/grafana/dashboards/covie-social-v2.json"), "utf8"));
assert.equal(dashboard.uid, "covie-social-v2");
assert.equal(dashboard.panels.length, 10);
const expressions = dashboard.panels.map(panel => panel.targets[0].expr);
const compose = readFileSync(path.join(root, "infra/compose/compose.observability.yml"), "utf8");
const image = compose.match(/prometheus:\r?\n\s+image:\s+(\S+)/)?.[1];
assert.ok(image, "configured Prometheus image is required");
assert.ok(expressions.every(expression => expression.includes('job="covie-api"')));
assert.ok(expressions.every(expression => !expression.includes("or vector(0)")));
const local = path.join(root, ".local");
mkdirSync(local, { recursive: true });
const fixture = realpathSync(mkdtempSync(path.join(local, "social-metrics-promql-")));
const prefix = realpathSync(local) + path.sep;
assert.ok(fixture.startsWith(prefix));
try {
  writeFileSync(path.join(fixture, "rules.yml"), JSON.stringify({ groups: [{ name: "social-dashboard", rules: expressions.map((expr, index) => ({ record: `social_dashboard:panel_${index}`, expr })) }] }));
  const samples = [
    [0, 0.55], [1, 0.1], [3, 0.05], [4, 200], [5, 3], [6, 606], [7, 1],
  ].map(([index, value]) => ({ expr: `abs((${expressions[index]}) - ${value}) <= bool 0.000000001`, eval_time: "10m", exp_samples: [{ labels: "{}", value: 1 }] }));
  const series = [
    ['http_server_requests_seconds_bucket{job="covie-api",uri="/api/v1/catalog/social-previews",method="GET",le="0.1"}', "0+90x10"],
    ['http_server_requests_seconds_bucket{job="covie-api",uri="/api/v1/catalog/social-previews",method="GET",le="1"}', "0+100x10"],
    ['http_server_requests_seconds_bucket{job="covie-api",uri="/api/v1/catalog/social-previews",method="GET",le="+Inf"}', "0+100x10"],
    ['http_server_requests_seconds_count{job="covie-api",uri="/api/v1/catalog/social-previews",method="GET",status="200"}', "0+90x10"],
    ['http_server_requests_seconds_count{job="covie-api",uri="/api/v1/catalog/social-previews",method="GET",status="500"}', "0+10x10"],
    ['covie_catalog_video_delivery_failures_total{job="covie-api"}', "0+3x10"],
    ['covie_media_ready_declared_bytes{job="covie-api"}', "100+10x10"],
    ['covie_moderation_open_cases{job="covie-api"}', "3+0x10"],
    ['covie_moderation_oldest_open_seconds{job="covie-api"}', "6+60x10"],
    ['http_server_requests_seconds_count{job="covie-api",uri="/api/v1/reports",method="POST",status="201"}', "0+60x10"],
  ].map(([series, values]) => ({ series, values }));
  writeFileSync(path.join(fixture, "tests.yml"), JSON.stringify({ rule_files: ["/etc/social/rules.yml"], evaluation_interval: "1m", tests: [{ interval: "1m", input_series: series, promql_expr_test: samples }] }));
  const tool = process.platform === "win32" ? "docker.exe" : "docker";
  const options = { encoding: "utf8", timeout: 120000, env: { ...process.env, MSYS_NO_PATHCONV: "1" } };
  const common = ["run", "--rm", "--network", "none", "--entrypoint", "promtool", "--mount", `type=bind,source=${fixture},target=/etc/social,readonly`, image];
  process.stdout.write(execFileSync(tool, [...common, "check", "rules", "/etc/social/rules.yml"], options));
  process.stdout.write(execFileSync(tool, [...common, "test", "rules", "/etc/social/tests.yml"], options));
  console.log("PASS: all10 dashboard PromQL expressions parse;7 selected synthetic rate/ratio/histogram/gauge cases pass (not deployment evidence)");
} finally {
  assert.ok(realpathSync(fixture).startsWith(prefix));
  rmSync(fixture, { recursive: true, force: true });
}
