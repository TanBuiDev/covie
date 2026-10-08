#!/usr/bin/env node
/**
 * Validate generated API contracts without depending on the reflection order
 * chosen by springdoc at export time. Neither OpenAPI object-member order
 * nor TypeScript interface/property declaration order affects API semantics.
 *
 * Other structural differences (types, paths, requiredness, signatures, etc.)
 * must still fail CI.
 */
import assert from "node:assert/strict";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import ts from "typescript";

const repoRoot = resolve(dirname(fileURLToPath(import.meta.url)), "../../..");
const contracts = [
  "packages/api-client/openapi/openapi.json",
  "packages/api-client/src/generated/schema.ts",
];

function committed(path) {
  return execFileSync("git", ["show", `HEAD:${path}`], {
    cwd: repoRoot,
    encoding: "utf8",
    maxBuffer: 16 * 1024 * 1024,
  });
}

function generated(path) {
  return readFileSync(resolve(repoRoot, path), "utf8");
}

function canonicalJson(value) {
  if (Array.isArray(value)) return value.map(canonicalJson);
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(
      Object.keys(value).sort().map((key) => [key, canonicalJson(value[key])]),
    );
  }
  return value;
}

function structuralTypeScript(source) {
  const ast = ts.createSourceFile("schema.ts", source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TS);
  assert.equal(ast.parseDiagnostics.length, 0, "Generated schema.ts must be valid TypeScript");

  function visit(node) {
    const children = [];
    ts.forEachChild(node, (child) => {
      children.push([child, visit(child)]);
    });

    // Object/interface property order is not semantically meaningful.
    // Preserve all other node types, tokens and member structure.
    if (ts.isInterfaceDeclaration(node) || ts.isTypeLiteralNode(node)) {
      const members = new Set(node.members);
      const others = children.filter(([child]) => !members.has(child)).map(([, value]) => value);
      const properties = children
        .filter(([child]) => members.has(child))
        .map(([, value]) => value)
        .sort((left, right) => JSON.stringify(left).localeCompare(JSON.stringify(right), "en"));
      return [node.kind, others, properties];
    }
    return [node.kind, children.length ? children.map(([, value]) => value) : node.getText(ast)];
  }

  return visit(ast);
}

try {
  const [openapiPath, schemaPath] = contracts;
  assert.deepStrictEqual(
    canonicalJson(JSON.parse(generated(openapiPath))),
    canonicalJson(JSON.parse(committed(openapiPath))),
    "OpenAPI contract changed: regenerate and commit the API client before merging",
  );
  assert.deepStrictEqual(
    structuralTypeScript(generated(schemaPath)),
    structuralTypeScript(committed(schemaPath)),
    "Generated TypeScript API contract changed: regenerate and commit the API client before merging",
  );
  process.stdout.write("Generated API contract is semantically unchanged.\n");
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
