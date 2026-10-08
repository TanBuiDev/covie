import { createHash } from "node:crypto";
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { describe, expect, it, vi } from "vitest";
import { parseVisualCases } from "@/e2e/support/visual-cases";
import { mergeEvidenceManifest, stabilizeVisualCapture, validateEvidenceDiagnostics, writeEvidenceFragment, type VisualEvidenceRecord } from "@/e2e/support/visual-evidence";

const catalogPath = path.resolve(process.cwd(), "../../docs/visual-acceptance-cases.csv");
const catalog = readFileSync(catalogPath, "utf8");

describe("visual acceptance case catalog", () => {
  it("parses the authoritative 81 cases into 201 unique capture tokens", () => {
    const cases = parseVisualCases(catalog);
    const captures = cases.flatMap((entry) => entry.captures);

    expect(cases).toHaveLength(81);
    expect(captures).toHaveLength(201);
    expect(new Set(captures.map((capture) => capture.filename)).size).toBe(201);
    expect(new Set(captures.map((capture) => capture.key)).size).toBe(201);
  });

  it("rejects missing and duplicate case or capture identities", () => {
    const lines = catalog.trimEnd().split(/\r?\n/);
    expect(() => parseVisualCases([lines[0]!, ...lines.slice(2)].join("\n"))).toThrow(/81/);
    expect(() => parseVisualCases([lines[0]!, lines[1]!, lines[1]!, ...lines.slice(2)].join("\n"))).toThrow(/Duplicate case_id/);

    const withoutCaptures = lines[1]!.replace(/,[^,]+$/, ",");
    expect(() => parseVisualCases([lines[0]!, withoutCaptures, ...lines.slice(2)].join("\n"))).toThrow(/captures/);

    const duplicateFilenameCase = lines[1]!.replace(/^pub-009-product,/, "pub-009-product-copy,");
    expect(() => parseVisualCases([lines[0]!, lines[1]!, duplicateFilenameCase, ...lines.slice(2)].join("\n"))).toThrow(/Duplicate capture filename/);
  });

  it("rejects review mode, node, viewport, filename and primary-node mismatches", () => {
    const lines = catalog.trimEnd().split(/\r?\n/);
    const invalidMode = lines[1]!.replace("overlay@", "pixel@");
    expect(() => parseVisualCases([lines[0]!, invalidMode, ...lines.slice(2)].join("\n"))).toThrow(/review mode/);

    const invalidNodeFilename = lines[1]!.replace("312-3076-390x844.png", "312-9999-390x844.png");
    expect(() => parseVisualCases([lines[0]!, invalidNodeFilename, ...lines.slice(2)].join("\n"))).toThrow(/node/);

    const invalidViewportFilename = lines[1]!.replace("390x844.png", "390x800.png");
    expect(() => parseVisualCases([lines[0]!, invalidViewportFilename, ...lines.slice(2)].join("\n"))).toThrow(/viewport/);

    const unusedPrimaryNode = lines[1]!.replace(",312:3076,", ",312:9999,");
    expect(() => parseVisualCases([lines[0]!, unusedPrimaryNode, ...lines.slice(2)].join("\n"))).toThrow(/primary Figma node/);
  });

  it("labels catalog context-board evidence as responsive instead of claiming an exact viewport overlay", () => {
    const cases = parseVisualCases(catalog);
    for (const caseId of [
      "sel-012-variants",
      "sel-013-resale",
      "sel-catalog-lifecycle",
      "sel-catalog-media-error",
      "sel-inventory-conflict",
      "sel-015-conflict",
      "sel-019-empty",
      "need-006-empty",
    ]) {
      const visualCase = cases.find((entry) => entry.caseId === caseId);
      expect(visualCase?.captures.every((capture) => capture.mode === "responsive")).toBe(true);
    }
  });
});

describe("visual evidence runtime helper", () => {
  it("forces a fresh paint after hydration while preserving the page display mode", async () => {
    const originalAnimationFrame = globalThis.requestAnimationFrame;
    const originalDisplay = document.body.style.display;
    const originalScrollX = Object.getOwnPropertyDescriptor(window, "scrollX");
    const originalScrollY = Object.getOwnPropertyDescriptor(window, "scrollY");
    const observedDisplays: string[] = [];
    let animationFrames = 0;
    Object.defineProperty(document.body, "offsetHeight", {
      configurable: true,
      get() {
        observedDisplays.push(document.body.style.display);
        return 844;
      },
    });
    globalThis.requestAnimationFrame = (callback: FrameRequestCallback) => {
      animationFrames += 1;
      queueMicrotask(() => callback(performance.now()));
      return animationFrames;
    };
    Object.defineProperty(window, "scrollX", { configurable: true, value: 12 });
    Object.defineProperty(window, "scrollY", { configurable: true, value: 34 });
    const scrollTo = vi.spyOn(window, "scrollTo").mockImplementation(() => undefined);
    document.body.style.display = "grid";

    try {
      await stabilizeVisualCapture({
        evaluate: async (callback: () => unknown) => callback(),
      } as never);

      expect(observedDisplays).toEqual(["none", "grid"]);
      expect(document.body.style.display).toBe("grid");
      expect(animationFrames).toBe(2);
      expect(scrollTo).toHaveBeenCalledWith(12, 34);
    } finally {
      document.body.style.display = originalDisplay;
      globalThis.requestAnimationFrame = originalAnimationFrame;
      scrollTo.mockRestore();
      if (originalScrollX) Object.defineProperty(window, "scrollX", originalScrollX);
      if (originalScrollY) Object.defineProperty(window, "scrollY", originalScrollY);
      delete (document.body as unknown as { offsetHeight?: number }).offsetHeight;
    }
  });

  it("rejects horizontal overflow and unexpected page or console errors", () => {
    expect(() => validateEvidenceDiagnostics({ scrollWidth: 390, clientWidth: 390, pageErrors: [], consoleErrors: [] })).not.toThrow();
    expect(() => validateEvidenceDiagnostics({ scrollWidth: 391, clientWidth: 390, pageErrors: [], consoleErrors: [] })).toThrow(/horizontal overflow/);
    expect(() => validateEvidenceDiagnostics({ scrollWidth: 390, clientWidth: 390, pageErrors: ["boom"], consoleErrors: [] })).toThrow(/page error.*boom/i);
    expect(() => validateEvidenceDiagnostics({ scrollWidth: 390, clientWidth: 390, pageErrors: [], consoleErrors: ["failed request"] })).toThrow(/console error.*failed request/i);
  });

  it("writes concurrent per-spec fragments atomically without losing either record", async () => {
    const directory = mkdtempSync(path.join(os.tmpdir(), "covie-visual-evidence-"));
    const record = {
      caseId: "case",
      captureKey: "case|overlay|1:2|390x844|case-1-2-390x844.png",
      role: "buyer",
      route: "/",
      stateSetup: "ready",
      assertion: "visible",
      figmaNode: "1:2",
      reviewMode: "overlay",
      viewport: { width: 390, height: 844 },
      filename: "case-1-2-390x844.png",
      sha256: "a".repeat(64),
      assertionResult: "PASS",
      pageErrors: [],
      consoleErrors: [],
      horizontalOverflow: false,
      manualReview: "PENDING",
      reviewerNote: "",
    } satisfies VisualEvidenceRecord;
    try {
      await Promise.all([
        writeEvidenceFragment(directory, "buyer", [record]),
        writeEvidenceFragment(directory, "seller", [{ ...record, caseId: "seller-case", filename: "seller-case-1-2-390x844.png" }]),
      ]);
      expect(JSON.parse(readFileSync(path.join(directory, "buyer.json"), "utf8")).records).toHaveLength(1);
      expect(JSON.parse(readFileSync(path.join(directory, "seller.json"), "utf8")).records[0].caseId).toBe("seller-case");
      expect(Array.from(new Set(["buyer.json", "seller.json"]))).toHaveLength(2);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("atomically merges fragments only when records and PNG files exactly match the catalog", async () => {
    const directory = mkdtempSync(path.join(os.tmpdir(), "covie-visual-manifest-"));
    const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64");
    const filename = "case-1-2-1x1.png";
    const captureKey = `case|overlay|1:2|1x1|${filename}`;
    const record = {
      caseId: "case",
      captureKey,
      role: "buyer",
      route: "/case",
      stateSetup: "ready",
      assertion: "visible",
      figmaNode: "1:2",
      reviewMode: "overlay",
      viewport: { width: 1, height: 1 },
      filename,
      sha256: createHash("sha256").update(png).digest("hex"),
      assertionResult: "PASS",
      pageErrors: [],
      consoleErrors: [],
      horizontalOverflow: false,
      manualReview: "PENDING",
      reviewerNote: "",
    } satisfies VisualEvidenceRecord;
    const visualCases = [{
      caseId: "case",
      role: "buyer",
      fixtureSetup: "ready",
      route: "/case",
      assertion: "visible",
      figmaNodes: ["1:2"],
      captures: [{ key: captureKey, caseId: "case", mode: "overlay" as const, figmaNode: "1:2", width: 1, height: 1, filename }],
    }];
    try {
      writeFileSync(path.join(directory, filename), png);
      await writeEvidenceFragment(path.join(directory, "fragments"), "buyer", [record]);
      const manifestPath = await mergeEvidenceManifest({
        evidenceDirectory: directory,
        fragmentNames: ["buyer"],
        visualCases,
        metadata: { generatedAt: "2026-09-17T00:00:00.000Z", workingTree: {}, buildIdentity: {} },
      });
      const manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      expect(manifest.records).toHaveLength(1);
      expect(manifest.records[0]).toMatchObject({ captureKey, intentionalDeviation: "" });

      await writeEvidenceFragment(path.join(directory, "fragments"), "buyer", [{ ...record, sha256: "0".repeat(64) }]);
      await expect(mergeEvidenceManifest({
        evidenceDirectory: directory,
        fragmentNames: ["buyer"],
        visualCases,
        metadata: { generatedAt: "2026-09-17T00:00:00.000Z", workingTree: {}, buildIdentity: {} },
      })).rejects.toThrow(/SHA-256/);
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });

  it("applies hash-bound manual reviews and invalidates them when the runtime capture changes", async () => {
    const directory = mkdtempSync(path.join(os.tmpdir(), "covie-visual-review-ledger-"));
    const png = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=", "base64");
    const changedPng = Buffer.from(png);
    changedPng[changedPng.length - 1] = changedPng[changedPng.length - 1]! ^ 1;
    const filename = "case-1-2-1x1.png";
    const referenceFilename = "1-2.png";
    const captureKey = `case|overlay|1:2|1x1|${filename}`;
    const screenshotSha256 = createHash("sha256").update(png).digest("hex");
    const figmaReferenceSha256 = createHash("sha256").update(png).digest("hex");
    const record = {
      caseId: "case",
      captureKey,
      role: "buyer",
      route: "/case",
      stateSetup: "ready",
      assertion: "visible",
      figmaNode: "1:2",
      reviewMode: "overlay",
      viewport: { width: 1, height: 1 },
      filename,
      sha256: screenshotSha256,
      assertionResult: "PASS",
      pageErrors: [],
      consoleErrors: [],
      horizontalOverflow: false,
      manualReview: "PENDING",
      reviewerNote: "",
    } satisfies VisualEvidenceRecord;
    const visualCases = [{
      caseId: "case",
      role: "buyer",
      fixtureSetup: "ready",
      route: "/case",
      assertion: "visible",
      figmaNodes: ["1:2"],
      captures: [{ key: captureKey, caseId: "case", mode: "overlay" as const, figmaNode: "1:2", width: 1, height: 1, filename }],
    }];
    const ledgerPath = path.join(directory, "manual-reviews.json");
    try {
      writeFileSync(path.join(directory, filename), png);
      mkdirSync(path.join(directory, "figma-reference"));
      writeFileSync(path.join(directory, "figma-reference", referenceFilename), png);
      await writeEvidenceFragment(path.join(directory, "fragments"), "buyer", [record]);
      writeFileSync(ledgerPath, JSON.stringify({
        schemaVersion: 1,
        reviews: [{
          captureKey,
          figmaNode: "1:2",
          screenshotSha256,
          figmaReferenceFilename: referenceFilename,
          figmaReferenceSha256,
          decision: "PASS",
          reviewer: "Codex visual inspection",
          reviewedAt: "2026-09-18T12:00:00.000Z",
          note: "Structure and spacing match the exact Figma frame.",
          intentionalDeviation: "The runtime keeps a contract-backed report action in place of unavailable fulfillment cues.",
        }],
      }));

      const manifestPath = await mergeEvidenceManifest({
        evidenceDirectory: directory,
        fragmentNames: ["buyer"],
        visualCases,
        metadata: { generatedAt: "2026-09-18T12:00:00.000Z", workingTree: {}, buildIdentity: {} },
        manualReviewLedgerPath: ledgerPath,
      });
      let manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      expect(manifest.records[0]).toMatchObject({
        manualReview: "PASS",
        reviewerNote: "Structure and spacing match the exact Figma frame.",
        intentionalDeviation: "The runtime keeps a contract-backed report action in place of unavailable fulfillment cues.",
        manualReviewEvidence: {
          reviewer: "Codex visual inspection",
          reviewedAt: "2026-09-18T12:00:00.000Z",
          figmaReferenceFilename: referenceFilename,
          figmaReferenceSha256,
        },
      });
      expect(manifest.totals).toMatchObject({ manualReview: { pass: 1, fail: 0, pending: 0 } });

      writeFileSync(path.join(directory, filename), changedPng);
      const changedRecord = { ...record, sha256: createHash("sha256").update(changedPng).digest("hex") };
      await writeEvidenceFragment(path.join(directory, "fragments"), "buyer", [changedRecord]);
      await mergeEvidenceManifest({
        evidenceDirectory: directory,
        fragmentNames: ["buyer"],
        visualCases,
        metadata: { generatedAt: "2026-09-18T12:01:00.000Z", workingTree: {}, buildIdentity: {} },
        manualReviewLedgerPath: ledgerPath,
      });
      manifest = JSON.parse(readFileSync(manifestPath, "utf8"));
      expect(manifest.records[0]).toMatchObject({
        manualReview: "PENDING",
        reviewerNote: "Manual review invalidated because the runtime capture SHA-256 changed.",
      });
      expect(manifest.totals).toMatchObject({ manualReview: { pass: 0, fail: 0, pending: 1 } });
    } finally {
      rmSync(directory, { recursive: true, force: true });
    }
  });
});
