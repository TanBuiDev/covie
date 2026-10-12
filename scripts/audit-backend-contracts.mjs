import { readFileSync, readdirSync, mkdirSync, writeFileSync } from "node:fs";
import { resolve, relative } from "node:path";

// Read-only inventory: exact typed-client calls only, not a runtime acceptance test.
const root = process.cwd();
const document = JSON.parse(readFileSync(resolve(root, "packages/api-client/openapi/openapi.json"), "utf8"));
const files = [];
function walk(directory) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (["node_modules", ".next", "test-results"].includes(entry.name)) continue;
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) walk(path);
    else if (/\.tsx?$/.test(entry.name) && !/\.(test|spec)\./.test(entry.name)) files.push(path);
  }
}
walk(resolve(root, "apps/web"));
const calls = [];
const patterns = [
  /\.(GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)\(\s*["'](\/api\/v1\/[^"']+)["']/g,
  /requestBrowserApi\(\s*["'](GET|POST|PUT|PATCH|DELETE|HEAD|OPTIONS)["']\s*,\s*["'](\/api\/v1\/[^"']+)["']/g,
];
for (const path of files) {
  const source = readFileSync(path, "utf8");
  for (const pattern of patterns) for (const match of source.matchAll(pattern)) {
    const method = match[1].toLowerCase();
    const endpoint = match[2];
    calls.push({ file: relative(root, path).replaceAll("\\", "/"), line: source.slice(0, match.index).split("\n").length,
      method: method.toUpperCase(), endpoint, documented: Boolean(document.paths?.[endpoint]?.[method]) });
  }
}
const operations = Object.entries(document.paths).flatMap(([endpoint, methods]) =>
  Object.keys(methods).filter(method => ["get", "post", "put", "patch", "delete", "head", "options"].includes(method))
    .map(method => ({ method: method.toUpperCase(), endpoint })));
const result = { generatedAt: new Date().toISOString(), scope: "Exact literal typed-client calls; dynamic calls, raw fetch, authorization, schemas and runtime behavior require separate review.",
  scannedFiles: files.length, operations, calls, missing: calls.filter(call => !call.documented) };
mkdirSync(resolve(root, ".local/backend-audit"), { recursive: true });
writeFileSync(resolve(root, ".local/backend-audit/contracts.json"), JSON.stringify(result, null, 2));
console.log(JSON.stringify({ scannedFiles: files.length, operations: operations.length, literalCalls: calls.length, missing: result.missing }, null, 2));
