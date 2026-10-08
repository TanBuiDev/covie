import { expect, test, type Browser, type Page } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { parseVisualCases } from "./support/visual-cases";
import { captureEvidence, collectPageDiagnostics, writeEvidenceFragment, type VisualEvidenceRecord } from "./support/visual-evidence";

type BuyerFixture = {
  buyerEmail: string;
  buyerPassword: string;
  zeroReputationBuyerEmail: string;
  zeroReputationBuyerPassword: string;
  warningReputationBuyerEmail: string;
  warningReputationBuyerPassword: string;
  adminEmail: string;
  adminPassword: string;
  addressId: string;
  reviewEligibleOrderId: string;
  alreadyReviewedOrderId: string;
  reviewDisabledOrderId: string;
};

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
const catalogPath = path.resolve(process.cwd(), "../../docs/visual-acceptance-cases.csv");
const acceptanceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review/acceptance-2026-09-13");
const enabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

test.skip(!enabled, "Run pnpm ui:verify-local with the local Docker stack running.");
test.setTimeout(180_000);

async function login(page: Page, email: string, password: string) {
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(email);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(password);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
}

async function setReviewCreation(browser: Browser, email: string, password: string, enabledValue: boolean) {
  const context = await browser.newContext({ baseURL: "http://localhost:3000" });
  const page = await context.newPage();
  try {
    await login(page, email, password);
    await page.goto("/admin/settings");
    await expect(page.getByRole("heading", { name: "Kiểm duyệt · Đánh giá · Uy tín", level: 1 })).toBeVisible();
    const setting = page.locator("article").filter({ hasText: "review.enabled" });
    const input = setting.getByRole("textbox", { name: "Giá trị" });
    await input.fill(String(enabledValue));
    await setting.getByRole("button", { name: "Lưu", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Xác nhận thay đổi cấu hình" });
    const [response] = await Promise.all([
      page.waitForResponse((candidate) => candidate.request().method() === "PATCH" && candidate.url().includes("/api/v1/admin/settings/review.enabled")),
      dialog.getByRole("button", { name: "Xác nhận lưu", exact: true }).click(),
    ]);
    expect(response.ok(), `Updating review.enabled failed with ${response.status()}: ${await response.text()}`).toBe(true);
    await expect(setting.getByText(new RegExp(`Hiệu lực: ${enabledValue}`))).toBeVisible();
  } finally {
    await context.close();
  }
}

test("Buyer captures every authoritative address, reputation, review and authorization state", async ({ page, browser }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as BuyerFixture;
  const visualCases = parseVisualCases(readFileSync(catalogPath, "utf8"));
  const buyerCases = visualCases.filter((entry) => entry.role === "buyer");
  expect(buyerCases).toHaveLength(19);
  const records: VisualEvidenceRecord[] = [];

  async function capturePrepared(
    targetPage: Page,
    caseId: string,
    prepare: () => Promise<void>,
    expectedConsoleErrors: string[] = [],
  ) {
    const visualCase = buyerCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing Buyer visual case ${caseId}`).toBeTruthy();
    const diagnostics = collectPageDiagnostics(targetPage);
    await targetPage.setViewportSize({ width: 390, height: 844 });
    await targetPage.goto(visualCase!.route);
    await prepare();
    for (const expectedError of expectedConsoleErrors) {
      await expect.poll(
        () => diagnostics.consoleErrors.some((message) => message.includes(expectedError)),
        { message: `Expected console error was not observed for ${caseId}: ${expectedError}` },
      ).toBe(true);
      await targetPage.waitForTimeout(100);
      for (let index = diagnostics.consoleErrors.length - 1; index >= 0; index -= 1) {
        if (diagnostics.consoleErrors[index]?.includes(expectedError)) diagnostics.consoleErrors.splice(index, 1);
      }
    }
    for (const capture of visualCase!.captures) {
      records.push(await captureEvidence({
        page: targetPage,
        capture,
        evidenceDirectory: acceptanceDirectory,
        role: visualCase!.role,
        route: visualCase!.route,
        stateSetup: visualCase!.fixtureSetup,
        assertion: visualCase!.assertion,
        pageErrors: diagnostics.pageErrors,
        consoleErrors: diagnostics.consoleErrors,
        waitForNetworkIdle: false,
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "buyer", records);
  }

  await login(page, fixture.buyerEmail, fixture.buyerPassword);
  await page.evaluate(({ addressId }) => {
    window.localStorage.setItem("covie:browse-location:v1", JSON.stringify({
      version: 1,
      location: { kind: "address", addressId, label: "Nhà" },
    }));
  }, { addressId: fixture.addressId });

  for (const caseId of ["buy-004-new", "buy-004-new-desktop"]) {
    await capturePrepared(page, caseId, async () => {
      const form = page.locator("form:visible");
      await expect(page.getByRole("heading", { name: "Thêm địa chỉ", exact: true }).first()).toBeVisible();
      await expect(form.getByLabel("Nhãn *")).toBeVisible();
      await expect(form.getByText("Có thể dùng GPS hoặc nhập tọa độ đơn giản.", { exact: true })).toBeVisible();
      await expect(form.getByRole("spinbutton", { name: "Vĩ độ *" })).toHaveCount(0);
      await expect(form.getByRole("spinbutton", { name: "Kinh độ *" })).toHaveCount(0);
      await form.getByLabel("Đặt làm địa chỉ mặc định").check();
      await expect(form.getByRole("button", { name: "Lưu địa chỉ", exact: true })).toBeVisible();
    });
  }

  const addressEndpoint = "**/api/v1/me/addresses*";
  await page.route(addressEndpoint, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    await route.fulfill({
      status: 422,
      contentType: "application/json",
      headers: {
        "access-control-allow-origin": "http://localhost:3000",
        "access-control-allow-credentials": "true",
      },
      body: JSON.stringify({ error: { code: "VALIDATION_FAILED", message: "Dữ liệu chưa hợp lệ.", fields: { phone: ["INVALID"], addressText: ["REQUIRED"] } } }),
    });
  });
  for (const caseId of ["buy-004-new-error", "buy-004-new-error-desktop"]) {
    await capturePrepared(page, caseId, async () => {
      const form = page.locator("form:visible");
      await form.getByLabel("Nhãn *").fill("Nhà lỗi");
      await form.getByLabel("Người nhận *").fill("Nguyễn Văn Minh");
      await form.getByLabel("Số điện thoại *").fill("090");
      await form.getByLabel("Địa chỉ *").fill("Địa chỉ sẽ bị từ chối");
      await form.getByLabel("Đặt làm địa chỉ mặc định").check();
      await form.getByRole("button", { name: "Lưu địa chỉ", exact: true }).click();
      await expect(page.getByText("Giá trị không hợp lệ.", { exact: true })).toBeVisible();
      await expect(page.getByText("Trường này là bắt buộc.", { exact: true })).toBeVisible();
      await expect(form.getByLabel("Nhãn *")).toHaveValue("Nhà lỗi");
    }, ["422 (Unprocessable Entity)"]);
  }
  await page.unroute(addressEndpoint);

  await page.goto(`/me/addresses/${fixture.addressId}`);
  const editForm = page.locator("form:visible");
  await editForm.getByLabel("Ghi chú").fill("Gọi trước khi giao");
  await editForm.getByRole("button", { name: "Lưu thay đổi", exact: true }).click();
  await expect(page).toHaveURL(/\/me\/addresses$/);
  for (const caseId of ["buy-004-edit", "buy-004-edit-desktop"]) {
    await capturePrepared(page, caseId, async () => {
      const form = page.locator("form:visible");
      await expect(page.getByRole("heading", { name: "Chỉnh sửa địa chỉ", exact: true }).first()).toBeVisible();
      await expect(form.getByLabel("Nhãn *")).toHaveValue("Nhà");
      await expect(form.getByLabel("Ghi chú")).toHaveValue("Gọi trước khi giao");
    });
  }

  for (const caseId of ["buy-009-main", "buy-009-360"]) {
    await capturePrepared(page, caseId, async () => {
      await expect(page.getByText("96%", { exact: true })).toBeVisible();
      await expect(page.getByText("24/25 giao dịch đủ điều kiện đã hoàn tất.", { exact: true })).toBeVisible();
      await expect(page.getByRole("heading", { name: "Tỷ lệ hoàn thành là gì?", level: 3 })).toBeVisible();
    });
  }

  async function captureReputationIdentity(caseId: string, email: string, password: string, assertion: (identityPage: Page) => Promise<void>) {
    const context = await browser.newContext({ baseURL: "http://localhost:3000" });
    const identityPage = await context.newPage();
    try {
      await login(identityPage, email, password);
      await capturePrepared(identityPage, caseId, () => assertion(identityPage));
    } finally {
      await context.close();
    }
  }

  await captureReputationIdentity("buy-009-zero", fixture.zeroReputationBuyerEmail, fixture.zeroReputationBuyerPassword, async (identityPage) => {
    await expect(identityPage.getByText("Chưa có dữ liệu", { exact: true })).toBeVisible();
    await expect(identityPage.getByText(/\d+(?:[,.]\d+)?%/)).toHaveCount(0);
  });
  await captureReputationIdentity("buy-009-warning", fixture.warningReputationBuyerEmail, fixture.warningReputationBuyerPassword, async (identityPage) => {
    await expect(identityPage.getByText("60%", { exact: true })).toBeVisible();
    await expect(identityPage.getByRole("status")).toContainText("Tỷ lệ hoàn thành đang ở mức cần lưu ý");
  });

  const reputationEndpoint = "**/api/v1/me/reputation";
  await page.route(reputationEndpoint, async (route) => {
    await route.fulfill({
      status: 503,
      contentType: "application/json",
      headers: {
        "access-control-allow-origin": "http://localhost:3000",
        "access-control-allow-credentials": "true",
      },
      body: JSON.stringify({ error: { code: "SERVICE_UNAVAILABLE", message: "Unavailable" } }),
    });
  });
  await capturePrepared(page, "buy-009-error", async () => {
    await expect(page.getByText("Không thể tải uy tín", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Thử lại", exact: true })).toBeVisible();
  }, ["503 (Service Unavailable)"]);
  await page.unroute(reputationEndpoint);

  for (const caseId of ["buy-010-eligible", "buy-010-eligible-360", "buy-010-compact"]) {
    await capturePrepared(page, caseId, async () => {
      await expect(page.getByRole("group", { name: "Đánh giá giao dịch" })).toBeVisible();
      await expect(page.getByRole("button", { name: "Gửi đánh giá", exact: true })).toBeVisible();
    });
  }

  await capturePrepared(page, "buy-010-submitted", async () => {
    await page.getByRole("radio", { name: "4 sao", exact: true }).click();
    await page.getByRole("textbox", { name: "Nhận xét (không bắt buộc)" }).fill("Sản phẩm đúng mô tả.");
    await page.getByRole("button", { name: "Gửi đánh giá", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Đã gửi đánh giá", level: 2 })).toBeVisible();
    await expect(page.getByRole("link", { name: "Xem chi tiết đơn", exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Gửi đánh giá", exact: true })).toHaveCount(0);
  });

  await capturePrepared(page, "buy-010-reviewed", async () => {
    await expect(page.getByRole("heading", { name: "Bạn đã đánh giá nội dung này", level: 3 })).toBeVisible();
    await expect(page.getByRole("button", { name: "Gửi đánh giá", exact: true })).toHaveCount(0);
  });

  await setReviewCreation(browser, fixture.adminEmail, fixture.adminPassword, false);
  try {
    await capturePrepared(page, "buy-010-disabled", async () => {
      await expect(page.getByRole("heading", { name: "Hệ thống đánh giá đang tạm tắt", level: 3 })).toBeVisible();
      await expect(page.getByRole("button", { name: "Gửi đánh giá", exact: true })).toHaveCount(0);
    });
  } finally {
    await setReviewCreation(browser, fixture.adminEmail, fixture.adminPassword, true);
  }

  await capturePrepared(page, "buyer-profile", async () => {
    const reputationLink = page.getByRole("link", { name: /Uy tín của tôi/ });
    await expect(reputationLink).toHaveAttribute("href", "/me/reputation");
    await reputationLink.focus();
    await expect(reputationLink).toBeFocused();
  });

  await capturePrepared(page, "adm-auth-boundary", async () => {
    await expect(page.getByLabel("Điều hướng quản trị")).toHaveCount(0);
    await expect(page.getByText("This page could not be found.", { exact: true })).toBeVisible();
  }, ["404 (Not Found)"]);

  expect(new Set(records.map((record) => record.caseId))).toEqual(new Set(buyerCases.map((entry) => entry.caseId)));
  expect(records).toHaveLength(30);
});
