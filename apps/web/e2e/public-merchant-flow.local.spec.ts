import { expect, test } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { parseVisualCases } from "./support/visual-cases";
import { captureEvidence, collectPageDiagnostics, writeEvidenceFragment, type VisualEvidenceRecord } from "./support/visual-evidence";

type PublicFixture = {
  merchantId: string;
  publicServiceMerchantId: string;
  emptyNoticeMerchantId: string;
  publicMixedMerchantId: string;
};

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
const catalogPath = path.resolve(process.cwd(), "../../docs/visual-acceptance-cases.csv");
const acceptanceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review/acceptance-2026-09-13");
const enabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

test.skip(!enabled, "Run pnpm ui:verify-local with the local Docker stack running.");
test.setTimeout(120_000);

test("Public merchant profiles capture authoritative capability and catalog states", async ({ page }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as PublicFixture;
  const visualCases = parseVisualCases(readFileSync(catalogPath, "utf8"));
  const records: VisualEvidenceRecord[] = [];

  async function captureCase(
    caseId: string,
    actualRoute: string,
    prepare: () => Promise<void>,
    options: { preserveScroll?: boolean } = {},
  ) {
    const visualCase = visualCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing visual case ${caseId}`).toBeTruthy();
    const diagnostics = collectPageDiagnostics(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(actualRoute);
    await prepare();
    for (const capture of visualCase!.captures) {
      records.push(await captureEvidence({
        page,
        capture,
        evidenceDirectory: acceptanceDirectory,
        role: visualCase!.role,
        route: visualCase!.route,
        stateSetup: visualCase!.fixtureSetup,
        assertion: visualCase!.assertion,
        pageErrors: diagnostics.pageErrors,
        consoleErrors: diagnostics.consoleErrors,
        waitForNetworkIdle: false,
        preserveScroll: options.preserveScroll,
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "public", records);
  }

  const productRoute = `/merchants/${fixture.merchantId}`;
  const loadingCase = visualCases.find((entry) => entry.caseId === "pub-009-loading");
  expect(loadingCase, "Missing visual case pub-009-loading").toBeTruthy();
  const loadingDiagnostics = collectPageDiagnostics(page);
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto(productRoute);
  await expect(page.getByRole("heading", { name: "Cửa hàng", exact: true, level: 1 })).toBeVisible();
  await page.evaluate(async () => { await document.fonts.ready; });
  const cdp = await page.context().newCDPSession(page);
  await cdp.send("Network.enable");
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false,
    latency: 10_000,
    downloadThroughput: -1,
    uploadThroughput: -1,
  });
  await page.goto(`${productRoute}?loadingProbe=${crypto.randomUUID()}`, { waitUntil: "commit" });
  await page.evaluate(async () => {
    await new Promise<void>((resolve, reject) => {
      const deadline = performance.now() + 5_000;
      const freezeWhenVisible = () => {
        const busyMain = document.querySelector<HTMLElement>("main[aria-busy='true']");
        if (busyMain?.offsetParent) {
          window.stop();
          document.body.replaceWith(document.body.cloneNode(true));
          resolve();
          return;
        }
        if (performance.now() >= deadline) {
          reject(new Error("The public merchant loading state did not become visible."));
          return;
        }
        requestAnimationFrame(freezeWhenVisible);
      };
      freezeWhenVisible();
    });
  });
  await expect(page.locator("main[aria-busy='true']")).toBeVisible();
  await page.addStyleTag({ content: "*,*::before,*::after{animation:none!important;transition:none!important;caret-color:transparent!important}" });
  for (const capture of loadingCase!.captures) {
    records.push(await captureEvidence({
      page,
      capture,
      evidenceDirectory: acceptanceDirectory,
      role: loadingCase!.role,
      route: loadingCase!.route,
      stateSetup: loadingCase!.fixtureSetup,
      assertion: loadingCase!.assertion,
      pageErrors: loadingDiagnostics.pageErrors,
      consoleErrors: loadingDiagnostics.consoleErrors,
      waitForNetworkIdle: false,
      waitForDocumentAssets: false,
    }));
  }
  await cdp.send("Network.emulateNetworkConditions", {
    offline: false,
    latency: 0,
    downloadThroughput: -1,
    uploadThroughput: -1,
  });
  await cdp.detach();
  await page.goto(productRoute);
  await expect(page.getByRole("heading", { name: "Cửa hàng", exact: true, level: 1 })).toBeVisible();
  loadingDiagnostics.dispose();

  const assertProductProfile = async () => {
    await expect(page.getByRole("heading", { name: "Cửa hàng", exact: true, level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Danh mục công khai", exact: true, level: 3 })).toBeVisible();
    await expect(page.locator("a[href^='/items/']")).toHaveCount(3);
    await expect(page.getByRole("link", { name: "Xem thêm", exact: true })).toBeVisible();
    await expect(page.getByText("Dịch vụ", { exact: true })).toHaveCount(0);
  };
  await captureCase("pub-009-product", productRoute, assertProductProfile);
  await captureCase("pub-009-product-360", productRoute, assertProductProfile);
  await captureCase("pub-009-product-catalog", productRoute, async () => {
    await assertProductProfile();
    await page.getByRole("heading", { name: "Danh mục công khai", exact: true, level: 3 }).scrollIntoViewIfNeeded();
  }, { preserveScroll: true });

  await captureCase("pub-009-service", `/merchants/${fixture.publicServiceMerchantId}`, async () => {
    await expect(page.getByRole("heading", { name: "Nhà cung cấp", exact: true, level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Dịch vụ công khai", exact: true, level: 3 })).toBeVisible();
    await expect(page.getByText("Dịch vụ", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Sản phẩm", { exact: true })).toHaveCount(0);
  });

  await captureCase("pub-009-mixed", `/merchants/${fixture.publicMixedMerchantId}`, async () => {
    await expect(page.getByRole("heading", { name: "Đơn vị cung cấp", exact: true, level: 1 })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Danh mục công khai", exact: true, level: 3 })).toBeVisible();
    await expect(page.getByText("Sản phẩm", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Dịch vụ", { exact: true }).first()).toBeVisible();
  });

  await captureCase("pub-009-empty", `/merchants/${fixture.emptyNoticeMerchantId}`, async () => {
    await expect(page.getByRole("heading", { name: "Đơn vị cung cấp", exact: true, level: 1 })).toBeVisible();
    await expect(page.getByText("Chưa có mặt hàng công khai.", { exact: true }).first()).toBeVisible();
    await expect(page.locator("a[href^='/items/']")).toHaveCount(0);
    const catalogHeading = page.getByRole("heading", { name: "Danh mục công khai", exact: true, level: 3 });
    await catalogHeading.evaluate((element) => element.scrollIntoView({ block: "center" }));
    await expect(catalogHeading).toBeInViewport();
    expect(await catalogHeading.evaluate((element) => element.getBoundingClientRect().top)).toBeLessThan(600);
  }, { preserveScroll: true });

  const invalidCursor = "not-a-valid-public-catalog-cursor";
  await captureCase("pub-009-error", `${productRoute}?catalogCursor=${encodeURIComponent(invalidCursor)}`, async () => {
    await expect(page.getByRole("heading", { name: "Cửa hàng Chợ Xanh", exact: true, level: 2 })).toBeVisible();
    await expect(page.getByText("Chưa thể tải danh mục công khai.", { exact: true }).first()).toBeVisible();
    await expect(page.getByRole("link", { name: "Thử lại danh mục", exact: true }).first()).toHaveAttribute(
      "href",
      `${productRoute}?catalogCursor=${encodeURIComponent(invalidCursor)}`,
    );
    const catalogHeading = page.getByRole("heading", { name: "Danh mục công khai", exact: true, level: 3 });
    await catalogHeading.evaluate((element) => element.scrollIntoView({ block: "center" }));
    await expect(catalogHeading).toBeInViewport();
    expect(await catalogHeading.evaluate((element) => element.getBoundingClientRect().top)).toBeLessThan(600);
  }, { preserveScroll: true });
});
