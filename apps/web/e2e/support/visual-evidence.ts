import { createHash } from "node:crypto";
import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";
import type { ConsoleMessage, Page } from "@playwright/test";
import type { VisualCaptureCase, VisualCase, VisualReviewMode } from "./visual-cases";

export type EvidenceDiagnostics = {
  scrollWidth: number;
  clientWidth: number;
  pageErrors: string[];
  consoleErrors: string[];
};

export type VisualEvidenceRecord = {
  caseId: string;
  captureKey: string;
  role: string;
  route: string;
  stateSetup: string;
  assertion: string;
  figmaNode: string;
  reviewMode: VisualReviewMode;
  viewport: { width: number; height: number };
  filename: string;
  sha256: string;
  assertionResult: "PASS" | "FAIL";
  pageErrors: string[];
  consoleErrors: string[];
  horizontalOverflow: boolean;
  manualReview: "PENDING" | "PASS" | "FAIL";
  reviewerNote: string;
};

export function validateEvidenceDiagnostics(diagnostics: EvidenceDiagnostics) {
  if (diagnostics.scrollWidth > diagnostics.clientWidth) {
    throw new Error(`Unexpected horizontal overflow: ${diagnostics.scrollWidth}px > ${diagnostics.clientWidth}px.`);
  }
  if (diagnostics.pageErrors.length) throw new Error(`Unexpected page error: ${diagnostics.pageErrors.join(" | ")}`);
  if (diagnostics.consoleErrors.length) throw new Error(`Unexpected console error: ${diagnostics.consoleErrors.join(" | ")}`);
}

export function collectPageDiagnostics(page: Page, expectedUnauthorizedUrls: readonly string[] = []) {
  const pageErrors: string[] = [];
  const consoleErrors: string[] = [];
  const onPageError = (error: Error) => pageErrors.push(error.message);
  const onConsole = (message: ConsoleMessage) => {
    if (message.type() !== "error") return;
    if (message.text() === "Failed to load resource: the server responded with a status of 401 (Unauthorized)"
        && expectedUnauthorizedUrls.includes(message.location().url)) return;
    consoleErrors.push(message.text());
  };
  page.on("pageerror", onPageError);
  page.on("console", onConsole);
  return {
    pageErrors,
    consoleErrors,
    dispose() {
      page.off("pageerror", onPageError);
      page.off("console", onConsole);
    },
  };
}

export async function stabilizeVisualCapture(page: Page) {
  await page.evaluate(async () => {
    const previousDisplay = document.body.style.display;
    const previousScrollX = window.scrollX;
    const previousScrollY = window.scrollY;
    document.body.style.display = "none";
    void document.body.offsetHeight;
    document.body.style.display = previousDisplay;
    void document.body.offsetHeight;
    window.scrollTo(previousScrollX, previousScrollY);
    await new Promise<void>((resolve) => {
      requestAnimationFrame(() => requestAnimationFrame(() => resolve()));
    });
  });
}

type CaptureEvidenceInput = {
  page: Page;
  capture: VisualCaptureCase;
  evidenceDirectory: string;
  role: string;
  route: string;
  stateSetup: string;
  assertion: string;
  pageErrors: string[];
  consoleErrors: string[];
  waitForNetworkIdle?: boolean;
  waitForDocumentAssets?: boolean;
  preserveScroll?: boolean;
};

export async function captureEvidence(input: CaptureEvidenceInput): Promise<VisualEvidenceRecord> {
  const { page, capture } = input;
  await page.setViewportSize({ width: capture.width, height: capture.height });
  if (input.waitForNetworkIdle !== false) await page.waitForLoadState("networkidle");
  await page.evaluate(async ({ preserveScroll, waitForDocumentAssets }) => {
    if (waitForDocumentAssets) await document.fonts.ready;
    if (!preserveScroll) window.scrollTo(0, 0);
    if (!waitForDocumentAssets) return;
    const visibleImages = Array.from(document.images).filter((image) => {
      const bounds = image.getBoundingClientRect();
      return bounds.bottom > 0 && bounds.right > 0 && bounds.top < window.innerHeight && bounds.left < window.innerWidth;
    });
    await Promise.all(visibleImages.map((image) => image.complete ? Promise.resolve() : new Promise<void>((resolve) => {
      image.addEventListener("load", () => resolve(), { once: true });
      image.addEventListener("error", () => resolve(), { once: true });
    })));
  }, {
    preserveScroll: input.preserveScroll === true,
    waitForDocumentAssets: input.waitForDocumentAssets !== false,
  });
  await page.addStyleTag({ content: "nextjs-portal{display:none!important}" });
  await stabilizeVisualCapture(page);
  const dimensions = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    clientWidth: document.documentElement.clientWidth,
  }));
  const diagnostics: EvidenceDiagnostics = { ...dimensions, pageErrors: [...input.pageErrors], consoleErrors: [...input.consoleErrors] };
  validateEvidenceDiagnostics(diagnostics);

  await mkdir(input.evidenceDirectory, { recursive: true });
  const screenshotPath = path.join(input.evidenceDirectory, capture.filename);
  await page.screenshot({ path: screenshotPath, fullPage: false });
  const sha256 = createHash("sha256").update(await readFile(screenshotPath)).digest("hex");
  return {
    caseId: capture.caseId,
    captureKey: capture.key,
    role: input.role,
    route: input.route,
    stateSetup: input.stateSetup,
    assertion: input.assertion,
    figmaNode: capture.figmaNode,
    reviewMode: capture.mode,
    viewport: { width: capture.width, height: capture.height },
    filename: capture.filename,
    sha256,
    assertionResult: "PASS",
    pageErrors: diagnostics.pageErrors,
    consoleErrors: diagnostics.consoleErrors,
    horizontalOverflow: false,
    manualReview: "PENDING",
    reviewerNote: "",
  };
}

export async function writeEvidenceFragment(directory: string, specName: string, records: VisualEvidenceRecord[]) {
  if (!/^[a-z0-9-]+$/.test(specName)) throw new Error(`Invalid evidence fragment name: ${specName}.`);
  await mkdir(directory, { recursive: true });
  const destination = path.join(directory, `${specName}.json`);
  const temporary = `${destination}.tmp-${process.pid}-${crypto.randomUUID()}`;
  const payload = `${JSON.stringify({ schemaVersion: 1, specName, records }, null, 2)}\n`;
  await writeFile(temporary, payload, "utf8");
  await rename(temporary, destination);
  return destination;
}

type EvidenceManifestMetadata = {
  generatedAt: string;
  workingTree: Record<string, unknown>;
  buildIdentity: Record<string, unknown>;
  [key: string]: unknown;
};

type MergeEvidenceManifestInput = {
  evidenceDirectory: string;
  fragmentNames: string[];
  visualCases: VisualCase[];
  metadata: EvidenceManifestMetadata;
  manualReviewLedgerPath?: string;
};

type ManualReviewDecision = {
  captureKey: string;
  figmaNode: string;
  screenshotSha256: string;
  figmaReferenceFilename: string;
  figmaReferenceSha256: string;
  decision: "PASS" | "FAIL";
  reviewer: string;
  reviewedAt: string;
  note: string;
  intentionalDeviation?: string;
};

type ManualReviewLedger = {
  schemaVersion?: number;
  reviews?: ManualReviewDecision[];
};

function readPngDimensions(buffer: Buffer, filename: string) {
  const signature = "89504e470d0a1a0a";
  if (buffer.length < 24 || buffer.subarray(0, 8).toString("hex") !== signature || buffer.subarray(12, 16).toString("ascii") !== "IHDR") {
    throw new Error(`Evidence file is not a valid PNG: ${filename}.`);
  }
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) };
}

export async function mergeEvidenceManifest(input: MergeEvidenceManifestInput) {
  const expected = input.visualCases.flatMap((visualCase) => visualCase.captures.map((capture) => ({ visualCase, capture })));
  const expectedByKey = new Map(expected.map((entry) => [entry.capture.key, entry]));
  if (expectedByKey.size !== expected.length) throw new Error("Visual catalog contains duplicate capture keys.");

  const records: VisualEvidenceRecord[] = [];
  for (const fragmentName of input.fragmentNames) {
    const fragmentPath = path.join(input.evidenceDirectory, "fragments", `${fragmentName}.json`);
    const fragment = JSON.parse(await readFile(fragmentPath, "utf8")) as { schemaVersion?: number; specName?: string; records?: VisualEvidenceRecord[] };
    if (fragment.schemaVersion !== 1 || fragment.specName !== fragmentName || !Array.isArray(fragment.records)) {
      throw new Error(`Invalid evidence fragment: ${fragmentName}.json.`);
    }
    records.push(...fragment.records);
  }

  const reviewsByKey = new Map<string, ManualReviewDecision>();
  if (input.manualReviewLedgerPath) {
    const ledger = JSON.parse(await readFile(input.manualReviewLedgerPath, "utf8")) as ManualReviewLedger;
    if (ledger.schemaVersion !== 1 || !Array.isArray(ledger.reviews)) {
      throw new Error("Invalid manual visual review ledger.");
    }
    for (const review of ledger.reviews) {
      if (!review || typeof review.captureKey !== "string" || typeof review.figmaNode !== "string" ||
          !/^[a-f0-9]{64}$/.test(review.screenshotSha256) || !/^[a-f0-9]{64}$/.test(review.figmaReferenceSha256) ||
          (review.decision !== "PASS" && review.decision !== "FAIL") || !review.reviewer.trim() ||
          Number.isNaN(Date.parse(review.reviewedAt)) || !review.note.trim() ||
          (review.intentionalDeviation != null && !review.intentionalDeviation.trim())) {
        throw new Error(`Invalid manual visual review entry: ${review?.captureKey ?? "unknown"}.`);
      }
      if (path.basename(review.figmaReferenceFilename) !== review.figmaReferenceFilename || !review.figmaReferenceFilename.endsWith(".png")) {
        throw new Error(`Invalid Figma reference filename: ${review.figmaReferenceFilename}.`);
      }
      if (!expectedByKey.has(review.captureKey)) throw new Error(`Unexpected manual visual review capture key: ${review.captureKey}.`);
      if (reviewsByKey.has(review.captureKey)) throw new Error(`Duplicate manual visual review capture key: ${review.captureKey}.`);
      reviewsByKey.set(review.captureKey, review);
    }
  }

  if (records.length !== expected.length) {
    throw new Error(`Evidence manifest requires exactly ${expected.length} records; found ${records.length}.`);
  }
  const seenKeys = new Set<string>();
  const seenFilenames = new Set<string>();
  const verifiedRecords: Array<VisualEvidenceRecord & { intentionalDeviation: string }> = [];
  for (const record of records) {
    if (!seenKeys.add(record.captureKey)) throw new Error(`Duplicate evidence capture key: ${record.captureKey}.`);
    if (!seenFilenames.add(record.filename)) throw new Error(`Duplicate evidence filename: ${record.filename}.`);
    const expectedEntry = expectedByKey.get(record.captureKey);
    if (!expectedEntry) throw new Error(`Unexpected evidence capture key: ${record.captureKey}.`);
    const { visualCase, capture } = expectedEntry;
    if (record.caseId !== visualCase.caseId || record.role !== visualCase.role || record.route !== visualCase.route ||
        record.stateSetup !== visualCase.fixtureSetup || record.assertion !== visualCase.assertion ||
        record.figmaNode !== capture.figmaNode || record.reviewMode !== capture.mode ||
        record.viewport.width !== capture.width || record.viewport.height !== capture.height || record.filename !== capture.filename) {
      throw new Error(`Evidence record does not match the catalog: ${record.captureKey}.`);
    }
    if (record.assertionResult !== "PASS" || record.pageErrors.length || record.consoleErrors.length || record.horizontalOverflow) {
      throw new Error(`Evidence record did not pass automated acceptance: ${record.captureKey}.`);
    }
    if (path.basename(record.filename) !== record.filename) throw new Error(`Evidence filename must not contain a path: ${record.filename}.`);
    const screenshot = await readFile(path.join(input.evidenceDirectory, record.filename));
    const sha256 = createHash("sha256").update(screenshot).digest("hex");
    if (sha256 !== record.sha256) throw new Error(`Evidence SHA-256 mismatch: ${record.filename}.`);
    const dimensions = readPngDimensions(screenshot, record.filename);
    if (dimensions.width !== record.viewport.width || dimensions.height !== record.viewport.height) {
      throw new Error(`Evidence PNG dimensions do not match the catalog: ${record.filename}.`);
    }
    const manualReview = reviewsByKey.get(record.captureKey);
    if (manualReview && manualReview.figmaNode !== record.figmaNode) {
      throw new Error(`Manual visual review Figma node mismatch: ${record.captureKey}.`);
    }
    let reviewedRecord: VisualEvidenceRecord & {
      intentionalDeviation: string;
      manualReviewEvidence?: Omit<ManualReviewDecision, "captureKey" | "figmaNode" | "screenshotSha256" | "decision" | "note">;
    } = { ...record, manualReview: "PENDING", reviewerNote: "", intentionalDeviation: "" };
    if (manualReview) {
      if (manualReview.screenshotSha256 !== record.sha256) {
        reviewedRecord = {
          ...reviewedRecord,
          reviewerNote: "Manual review invalidated because the runtime capture SHA-256 changed.",
        };
      } else {
        const figmaReference = await readFile(path.join(input.evidenceDirectory, "figma-reference", manualReview.figmaReferenceFilename));
        const figmaReferenceSha256 = createHash("sha256").update(figmaReference).digest("hex");
        if (figmaReferenceSha256 !== manualReview.figmaReferenceSha256) {
          reviewedRecord = {
            ...reviewedRecord,
            reviewerNote: "Manual review invalidated because the Figma reference SHA-256 changed.",
          };
        } else {
          reviewedRecord = {
            ...reviewedRecord,
            manualReview: manualReview.decision,
            reviewerNote: manualReview.note,
            intentionalDeviation: manualReview.intentionalDeviation ?? "",
            manualReviewEvidence: {
              reviewer: manualReview.reviewer,
              reviewedAt: manualReview.reviewedAt,
              figmaReferenceFilename: manualReview.figmaReferenceFilename,
              figmaReferenceSha256: manualReview.figmaReferenceSha256,
            },
          };
        }
      }
    }
    verifiedRecords.push(reviewedRecord);
  }
  for (const { capture } of expected) {
    if (!seenKeys.has(capture.key)) throw new Error(`Missing evidence capture: ${capture.key}.`);
  }

  const destination = path.join(input.evidenceDirectory, "manifest.json");
  const temporary = `${destination}.tmp-${process.pid}-${crypto.randomUUID()}`;
  const payload = `${JSON.stringify({
    schemaVersion: 1,
    ...input.metadata,
    totals: {
      cases: input.visualCases.length,
      captures: verifiedRecords.length,
      manualReview: {
        pass: verifiedRecords.filter((record) => record.manualReview === "PASS").length,
        fail: verifiedRecords.filter((record) => record.manualReview === "FAIL").length,
        pending: verifiedRecords.filter((record) => record.manualReview === "PENDING").length,
      },
    },
    fragments: [...input.fragmentNames],
    records: verifiedRecords,
  }, null, 2)}\n`;
  await writeFile(temporary, payload, "utf8");
  await rename(temporary, destination);
  return destination;
}
