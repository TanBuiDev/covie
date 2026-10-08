export type VisualReviewMode = "overlay" | "responsive";

export type VisualCaptureCase = {
  key: string;
  caseId: string;
  mode: VisualReviewMode;
  figmaNode: string;
  width: number;
  height: number;
  filename: string;
};

export type VisualCase = {
  caseId: string;
  role: string;
  fixtureSetup: string;
  route: string;
  assertion: string;
  figmaNodes: string[];
  captures: VisualCaptureCase[];
};

const REQUIRED_HEADERS = ["case_id", "role", "fixture_setup", "route", "assertion", "figma_node", "captures"] as const;

function parseCsvRows(source: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let field = "";
  let quoted = false;

  for (let index = 0; index < source.length; index += 1) {
    const character = source[index];
    if (character === '"') {
      if (quoted && source[index + 1] === '"') {
        field += '"';
        index += 1;
      } else {
        quoted = !quoted;
      }
    } else if (character === "," && !quoted) {
      row.push(field);
      field = "";
    } else if ((character === "\n" || character === "\r") && !quoted) {
      if (character === "\r" && source[index + 1] === "\n") index += 1;
      row.push(field);
      if (row.some((value) => value.length > 0)) rows.push(row);
      row = [];
      field = "";
    } else {
      field += character;
    }
  }
  if (quoted) throw new Error("Visual case CSV contains an unterminated quoted field.");
  row.push(field);
  if (row.some((value) => value.length > 0)) rows.push(row);
  return rows;
}

function required(value: string | undefined, field: string, caseId: string) {
  const normalized = value?.trim();
  if (!normalized) throw new Error(`Visual case '${caseId || "unknown"}' is missing ${field}.`);
  return normalized;
}

export function parseVisualCases(source: string): VisualCase[] {
  const rows = parseCsvRows(source.replace(/^\uFEFF/, ""));
  const headers = rows.shift();
  if (!headers || REQUIRED_HEADERS.some((header, index) => headers[index]?.trim() !== header)) {
    throw new Error(`Visual case CSV must use headers: ${REQUIRED_HEADERS.join(",")}.`);
  }

  const caseIds = new Set<string>();
  const captureKeys = new Set<string>();
  const captureFilenames = new Set<string>();
  const cases = rows.map((values) => {
    if (values.length !== REQUIRED_HEADERS.length) throw new Error(`Visual case row has ${values.length} columns instead of 7.`);
    const caseId = required(values[0], "case_id", "");
    if (caseIds.has(caseId)) throw new Error(`Duplicate case_id '${caseId}'.`);
    caseIds.add(caseId);

    const primaryNodes = required(values[5], "figma_node", caseId).split("+").map((node) => node.trim());
    const rawCaptures = required(values[6], "captures", caseId).split("|");
    const captures = rawCaptures.map((token): VisualCaptureCase => {
      const match = /^(overlay|responsive)@(\d+:\d+)@(\d+)x(\d+)=(.+\.png)$/.exec(token.trim());
      if (!match) {
        const mode = token.split("@", 1)[0];
        if (mode !== "overlay" && mode !== "responsive") throw new Error(`Invalid visual review mode in '${caseId}': ${mode}.`);
        throw new Error(`Invalid capture token in '${caseId}': ${token}.`);
      }
      const mode = match[1]!;
      const figmaNode = match[2]!;
      const widthText = match[3]!;
      const heightText = match[4]!;
      const filename = match[5]!;
      const width = Number(widthText);
      const height = Number(heightText);
      const filenameParts = /-(\d+)-(\d+)-(\d+)x(\d+)\.png$/.exec(filename);
      if (!filenameParts) throw new Error(`Capture filename in '${caseId}' does not encode its node and viewport: ${filename}.`);
      const filenameNode = `${filenameParts[1]}:${filenameParts[2]}`;
      if (filenameNode !== figmaNode) throw new Error(`Capture filename node mismatch in '${caseId}': ${filename}.`);
      if (Number(filenameParts[3]) !== width || Number(filenameParts[4]) !== height) {
        throw new Error(`Capture filename viewport mismatch in '${caseId}': ${filename}.`);
      }
      const key = `${caseId}|${mode}|${figmaNode}|${width}x${height}|${filename}`;
      if (captureKeys.has(key)) throw new Error(`Duplicate capture token '${key}'.`);
      if (captureFilenames.has(filename)) throw new Error(`Duplicate capture filename '${filename}'.`);
      captureKeys.add(key);
      captureFilenames.add(filename);
      return { key, caseId, mode: mode as VisualReviewMode, figmaNode, width, height, filename };
    });
    const capturedNodes = new Set(captures.map((capture) => capture.figmaNode));
    for (const node of primaryNodes) {
      if (!capturedNodes.has(node)) throw new Error(`Visual case '${caseId}' has primary Figma node '${node}' with no capture token.`);
    }
    return {
      caseId,
      role: required(values[1], "role", caseId),
      fixtureSetup: required(values[2], "fixture_setup", caseId),
      route: required(values[3], "route", caseId),
      assertion: required(values[4], "assertion", caseId),
      figmaNodes: primaryNodes,
      captures,
    };
  });

  if (cases.length !== 81) throw new Error(`Visual case catalog must contain exactly 81 cases; found ${cases.length}.`);
  const totalCaptures = cases.reduce((total, entry) => total + entry.captures.length, 0);
  if (totalCaptures !== 201) throw new Error(`Visual case catalog must contain exactly 201 captures; found ${totalCaptures}.`);
  return cases;
}
