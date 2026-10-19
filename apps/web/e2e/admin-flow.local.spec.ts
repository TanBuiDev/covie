import { expect, test } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { parseVisualCases } from "./support/visual-cases";
import { captureEvidence, collectPageDiagnostics, writeEvidenceFragment, type VisualEvidenceRecord } from "./support/visual-evidence";

type AdminFixture = {
  adminEmail: string;
  adminPassword: string;
};

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
const catalogPath = path.resolve(process.cwd(), "../../docs/visual-acceptance-cases.csv");
const acceptanceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review/acceptance-2026-09-13");
const enabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

const adminMainCases = new Map<string, string | RegExp>([
  ["adm-001", "Tổng quan"],
  ["adm-002", "Người dùng"],
  ["adm-003", "Người bán / Nhà cung cấp"],
  ["adm-004", "Cửa hàng & Điểm bán"],
  ["adm-005", "Nội dung đăng bán"],
  ["adm-006", "Tra cứu đơn hàng"],
  ["adm-007", /^Lịch sử đơn /],
  ["adm-008", "Cần mua & Nhu cầu cư dân"],
  ["adm-009", "Hàng đợi báo cáo"],
  ["adm-010-settings", "Kiểm duyệt · Đánh giá · Uy tín"],
  ["adm-013", "Thời hạn đơn hàng"],
  ["adm-014", "Ghép nối Cần mua"],
  ["adm-015", "Khu vực"],
  ["adm-016", "Danh mục"],
  ["adm-017", "Nhật ký quản trị"],
]);

test.skip(!enabled, "Run pnpm ui:verify-local with the local Docker stack running.");
test.setTimeout(120_000);
test.use({ serviceWorkers: "block" });

test("Admin authenticates and captures the deterministic main-state journey", async ({ page }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as AdminFixture;

  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(fixture.adminEmail);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture.adminPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);

  const visualCases = parseVisualCases(readFileSync(catalogPath, "utf8"));
  const records: VisualEvidenceRecord[] = [];

  for (const [caseId, expectedHeading] of adminMainCases) {
    const visualCase = visualCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing visual case ${caseId}`).toBeTruthy();
    const diagnostics = collectPageDiagnostics(page);
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto(visualCase!.route);
    await expect(page).toHaveURL(visualCase!.route);
    await expect(page.getByRole("heading", { name: expectedHeading, exact: typeof expectedHeading === "string", level: 1 })).toBeVisible();
    await expect(page.getByLabel("Điều hướng quản trị")).toBeVisible();
    if (caseId === "adm-017") {
      await page.getByLabel("Loại đối tượng", { exact: true }).fill("UI_VISUAL_ACCEPTANCE");
      await page.getByLabel("Từ thời điểm", { exact: true }).fill("2026-09-16T00:00");
      await page.getByLabel("Đến thời điểm", { exact: true }).fill("2026-09-17T00:00");
      await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
      await expect(page.locator(".admin-audit-list").getByRole("listitem")).toHaveCount(5);
    }

    for (const capture of visualCase!.captures) {
      if (caseId === "adm-017") {
        await page.setViewportSize({ width: capture.width, height: capture.height });
        const scroller = page.getByRole("region", { name: "Nhật ký quản trị có thể cuộn ngang", exact: true });
        await expect(scroller).toBeVisible();
        if (capture.width <= 1180) {
          await scroller.focus();
          await page.keyboard.press("ArrowRight");
          await expect.poll(() => scroller.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0);
          expect(await page.evaluate(() => scrollX)).toBe(0);
          await scroller.evaluate((element) => element.scrollTo({ left: 0 }));
        }
      }
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
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "admin", records);
  }

  async function capturePreparedCase(
    caseId: string,
    expectedHeading: string,
    prepare: () => Promise<void>,
    expectedConsoleError: string,
  ) {
    const visualCase = visualCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing visual case ${caseId}`).toBeTruthy();
    const diagnostics = collectPageDiagnostics(page);
    await page.setViewportSize({ width: 1440, height: 900 });
    await page.goto(visualCase!.route);
    await expect(page).toHaveURL(visualCase!.route);
    await expect(page.getByRole("heading", { name: expectedHeading, exact: true, level: 1 })).toBeVisible();
    await prepare();
    const errorIndex = diagnostics.consoleErrors.indexOf(expectedConsoleError);
    expect(errorIndex, `Expected console error was not observed for ${caseId}`).toBeGreaterThanOrEqual(0);
    diagnostics.consoleErrors.splice(errorIndex, 1);
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
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "admin", records);
  }

  const reportQueueEndpoint = "**/api/v1/admin/moderation-cases?*";
  await page.route(reportQueueEndpoint, async (route) => {
    if (route.request().method() === "GET" && page.url().includes("/admin/reports")) {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ error: { code: "RESOURCE_VERSION_CONFLICT", message: "Hàng đợi đã thay đổi. Hãy tải lại." } }),
      });
      return;
    }
    await route.continue();
  });
  await capturePreparedCase("adm-009-conflict", "Hàng đợi báo cáo", async () => {
    await expect(page.locator("p.admin-error")).toContainText("Hàng đợi đã thay đổi. Hãy tải lại.");
    await expect(page.getByRole("button", { name: "Thử lại", exact: true })).toBeVisible();
  }, "Failed to load resource: the server responded with a status of 409 (Conflict)");
  await page.unroute(reportQueueEndpoint);

  async function exerciseSettingError(
    caseId: string,
    heading: string,
    settingKey: string,
    value: string,
    status: number,
    statusText: string,
    message: string,
  ) {
    const settingEndpoint = `**/api/v1/admin/settings/${settingKey}`;
    await page.route(settingEndpoint, async (route) => {
      if (route.request().method() === "PATCH") {
        await route.fulfill({
          status,
          contentType: "application/json",
          body: JSON.stringify({ error: { code: status === 412 ? "RESOURCE_VERSION_CONFLICT" : "VALIDATION_ERROR", message } }),
        });
        return;
      }
      await route.continue();
    });
    await capturePreparedCase(caseId, heading, async () => {
      const setting = page.locator("article").filter({ hasText: settingKey });
      const input = setting.getByRole("textbox", { name: "Giá trị" });
      await input.fill(value);
      await setting.getByRole("button", { name: "Lưu", exact: true }).click();
      const dialog = page.getByRole("dialog", { name: "Xác nhận thay đổi cấu hình" });
      await dialog.getByRole("button", { name: "Xác nhận lưu", exact: true }).click();
      await expect(setting.getByRole("alert")).toContainText(message);
      await expect(input).toHaveValue(value);
      await expect(setting.getByRole("button", { name: "Tải lại cấu hình mới nhất", exact: true })).toBeVisible();
    }, `Failed to load resource: the server responded with a status of ${status} (${statusText})`);
    await page.unroute(settingEndpoint);
  }

  await exerciseSettingError(
    "adm-010-settings-conflict",
    "Kiểm duyệt · Đánh giá · Uy tín",
    "moderation.mode",
    "HYBRID",
    412,
    "Precondition Failed",
    "Cấu hình đã được thay đổi. Hãy tải lại.",
  );
  await exerciseSettingError(
    "adm-014-validation",
    "Ghép nối Cần mua",
    "need_request.max_response_items_per_location",
    "99",
    422,
    "Unprocessable Entity",
    "Giá trị phải nằm trong khoảng từ 1 đến 10.",
  );
});
