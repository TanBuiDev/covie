import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { readFileSync } from "node:fs";
import path from "node:path";
import { expect, test } from "@playwright/test";
import { parseVisualCases } from "./support/visual-cases";
import { mergeEvidenceManifest } from "./support/visual-evidence";

const repositoryRoot = path.resolve(process.cwd(), "../..");
const evidenceDirectory = path.join(repositoryRoot, ".local/dev/visual-review/acceptance-2026-09-13");
const catalogPath = path.join(repositoryRoot, "docs/visual-acceptance-cases.csv");
const manualReviewLedgerPath = path.join(repositoryRoot, "docs/visual-acceptance-manual-reviews.json");
const currentDesign = process.env.COVIE_UI_CURRENT_DESIGN === "1";

function sha256(filePath: string) {
  return createHash("sha256").update(readFileSync(filePath)).digest("hex");
}

function git(...args: string[]) {
  return execFileSync("git", args, { cwd: repositoryRoot, encoding: "utf8" }).trim();
}

test("merges and validates the complete visual acceptance manifest", async () => {
  const catalogSource = readFileSync(catalogPath, "utf8");
  const visualCases = parseVisualCases(catalogSource);
  const statusLines = git("status", "--porcelain").split(/\r?\n/).filter(Boolean);
  const manifestPath = await mergeEvidenceManifest({
    evidenceDirectory,
    fragmentNames: ["buyer", "public", "seller", "admin"],
    visualCases,
    manualReviewLedgerPath: currentDesign ? undefined : manualReviewLedgerPath,
    metadata: {
      figmaComparison: currentDesign ? "NOT_APPLICABLE_OWNER_WAIVER" : "FROZEN_FIGMA",
      manualReviewScope: currentDesign ? "NOT_PERFORMED" : "HASH_BOUND_FIGMA_LEDGER",
      generatedAt: new Date().toISOString(),
      catalogSha256: createHash("sha256").update(catalogSource).digest("hex"),
      workingTree: {
        branch: git("branch", "--show-current"),
        head: git("rev-parse", "HEAD"),
        dirty: statusLines.length > 0,
        changedPathCount: statusLines.length,
      },
      buildIdentity: {
        api: {
          pomSha256: sha256(path.join(repositoryRoot, "apps/api/pom.xml")),
          openApiSha256: sha256(path.join(repositoryRoot, "packages/api-client/openapi/openapi.json")),
        },
        web: {
          packageSha256: sha256(path.join(repositoryRoot, "apps/web/package.json")),
          lockfileSha256: sha256(path.join(repositoryRoot, "pnpm-lock.yaml")),
        },
      },
    },
  });

  const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
  expect(manifest.totals).toEqual({
    cases: 81,
    captures: 201,
    manualReview: currentDesign ? { pass: 0, fail: 0, pending: 201 } : { pass: 201, fail: 0, pending: 0 },
  });
  if (currentDesign) {
    expect(manifest.figmaComparison).toBe("NOT_APPLICABLE_OWNER_WAIVER");
    expect(manifest.manualReviewScope).toBe("NOT_PERFORMED");
  }
  expect(manifest.records).toHaveLength(201);
  expect(new Set(manifest.records.map((record: { captureKey: string }) => record.captureKey)).size).toBe(201);
});
