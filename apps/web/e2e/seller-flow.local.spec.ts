import { expect, test, type Locator } from "@playwright/test";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { parseVisualCases } from "./support/visual-cases";
import { captureEvidence, collectPageDiagnostics, writeEvidenceFragment, type VisualEvidenceRecord } from "./support/visual-evidence";

type SellerFixture = {
  sellerEmail: string;
  sellerPassword: string;
  merchantId: string;
  emptyNoticeMerchantId: string;
  locationId: string;
  candidateNeedId: string;
  respondedNeedId: string;
  emptyCandidateNeedId: string;
};

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
const catalogPath = path.resolve(process.cwd(), "../../docs/visual-acceptance-cases.csv");
const acceptanceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review/acceptance-2026-09-13");
const enabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

const sellerMainCases = new Map([
  ["sel-001", "Tổng quan"],
  ["sel-002", "Cài đặt shop"],
  ["sel-003", "Điểm bán"],
  ["sel-004-new", "Thêm điểm bán"],
  ["sel-004-edit", "Sửa điểm bán"],
  ["sel-005", "Phạm vi phục vụ"],
  ["sel-006", "Phí giao hàng"],
  ["sel-007", "Đơn tối thiểu"],
  ["sel-008", "Liên hệ"],
  ["sel-009", "Danh mục"],
  ["sel-010-edit", "Chỉnh sửa catalog"],
  ["sel-014", "Đơn hàng"],
  ["sel-015-product", "Chi tiết đơn"],
  ["sel-015-delivery", "Chi tiết đơn"],
  ["sel-015-pickup", "Chi tiết đơn"],
  ["sel-015-service", "Chi tiết đơn"],
  ["need-005", "Cần mua phù hợp"],
  ["sel-018", "Uy tín Seller"],
  ["sel-019", "Thông báo kiểm duyệt"],
]);

test.skip(!enabled, "Run pnpm ui:verify-local with the local Docker stack running.");
test.setTimeout(120_000);
test.use({ serviceWorkers: "block" });

test("Seller authenticates and captures the deterministic main-state journey", async ({ page }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as SellerFixture;

  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(fixture.sellerEmail);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture.sellerPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);

  const visualCases = parseVisualCases(readFileSync(catalogPath, "utf8"));
  const records: VisualEvidenceRecord[] = [];

  for (const [caseId, expectedHeading] of sellerMainCases) {
    const visualCase = visualCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing visual case ${caseId}`).toBeTruthy();
    const route = visualCase!.route.replace("33333333-3333-4333-8333-333333333333", fixture.merchantId);
    const diagnostics = collectPageDiagnostics(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(route);
    await expect(page).toHaveURL(route);
    await expect(page.getByRole("banner").getByRole("heading", { name: expectedHeading, exact: true, level: 1 })).toBeVisible();
    await expect(page.getByRole("navigation", { name: "Điều hướng Seller Center" })).toBeVisible();
    if (caseId.startsWith("sel-015-")) {
      const expectedFulfillment = {
        "sel-015-product": { method: "Người bán giao", distance: "0 km" },
        "sel-015-delivery": { method: "Người bán giao", distance: "4,2 km" },
        "sel-015-pickup": { method: "Nhận tại điểm bán" },
        "sel-015-service": { method: "Dịch vụ", distance: "4,2 km" },
      }[caseId];
      const fulfillment = page.getByRole("region", { name: "Thực hiện đơn" });
      await expect(fulfillment).toBeVisible();
      await expect(fulfillment.getByText(expectedFulfillment!.method, { exact: true })).toBeVisible();
      if (expectedFulfillment!.distance) {
        await expect(fulfillment.getByText(expectedFulfillment!.distance, { exact: true })).toBeVisible();
      }
      if (caseId === "sel-015-product") {
        await expect(page.getByRole("button", { name: "Xác nhận đơn", exact: true })).toBeVisible();
      }
    }
    if (caseId === "sel-019") {
      await expect(page.getByRole("heading", { name: "Có cập nhật kiểm duyệt cho cửa hàng", exact: true, level: 2 })).toBeVisible();
    }

    for (const capture of visualCase!.captures) {
      if (caseId === "need-005" && capture.width >= 1024) {
        await page.setViewportSize({ width: capture.width, height: capture.height });
        const candidates = page.getByRole("region", { name: "Chọn nội dung phản hồi" });
        if (await candidates.count() === 0) {
          const candidateNeed = page.getByRole("article").filter({ hasText: "Cần rau củ theo mùa" });
          await candidateNeed.getByRole("button", { name: /Phản hồi/ }).click();
          await expect(candidates).toBeVisible();
          await expect(candidates.getByRole("checkbox").first()).toBeVisible();
        }
        const summaryBox = await page.locator(".seller-need-card__summary").first().boundingBox();
        expect(summaryBox?.width, "Desktop Need title must retain a readable column").toBeGreaterThan(140);
      }
      records.push(await captureEvidence({
        page,
        capture,
        evidenceDirectory: acceptanceDirectory,
        role: visualCase!.role,
        route,
        stateSetup: visualCase!.fixtureSetup,
        assertion: visualCase!.assertion,
        pageErrors: diagnostics.pageErrors,
        consoleErrors: diagnostics.consoleErrors,
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "seller", records);
  }

  async function capturePreparedCase(
    caseId: string,
    prepare: () => Promise<void>,
    options: { expectedHeading?: string; expectedConsoleErrors?: string[]; merchantId?: string; evidenceFocus?: () => Locator } = {},
  ) {
    const visualCase = visualCases.find((entry) => entry.caseId === caseId);
    expect(visualCase, `Missing visual case ${caseId}`).toBeTruthy();
    const route = visualCase!.route.replace(
      "33333333-3333-4333-8333-333333333333",
      options.merchantId ?? fixture.merchantId,
    );
    const diagnostics = collectPageDiagnostics(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(route);
    await expect(page).toHaveURL(route);
    await expect(page.getByRole("banner").getByRole("heading", { name: options.expectedHeading ?? "Chi tiết đơn", exact: true, level: 1 })).toBeVisible();
    await prepare();
    for (const expectedError of options.expectedConsoleErrors ?? []) {
      const errorIndex = diagnostics.consoleErrors.indexOf(expectedError);
      expect(errorIndex, `Expected console error was not observed for ${caseId}: ${expectedError}`).toBeGreaterThanOrEqual(0);
      diagnostics.consoleErrors.splice(errorIndex, 1);
    }
    for (const capture of visualCase!.captures) {
      if ((caseId.startsWith("sel-016-") || caseId === "sel-017-cancel") && capture.width >= 1024) {
        await page.setViewportSize({ width: capture.width, height: capture.height });
        const summaryBox = await page.locator(".seller-order-action__summary").boundingBox();
        expect(summaryBox?.width, "Desktop order-action summary must not collapse").toBeGreaterThan(180);
      }
      if (options.evidenceFocus) {
        await page.setViewportSize({ width: capture.width, height: capture.height });
        await options.evidenceFocus().scrollIntoViewIfNeeded();
      }
      records.push(await captureEvidence({
        page,
        capture,
        evidenceDirectory: acceptanceDirectory,
        role: visualCase!.role,
        route,
        stateSetup: visualCase!.fixtureSetup,
        assertion: visualCase!.assertion,
        pageErrors: diagnostics.pageErrors,
        consoleErrors: diagnostics.consoleErrors,
        preserveScroll: Boolean(options.evidenceFocus),
      }));
    }
    diagnostics.dispose();
    await writeEvidenceFragment(path.join(acceptanceDirectory, "fragments"), "seller", records);
  }

  await capturePreparedCase("sel-010-product", async () => {
    await expect(page.getByRole("combobox", { name: "Loại", exact: true })).toHaveText("Sản phẩm");
    await expect(page.locator('input[name="type"]')).toHaveValue("PRODUCT");
    await expect(page.getByRole("combobox", { name: "Quản lý tồn kho", exact: true })).toBeVisible();
    await expect(page.getByRole("region", { name: "Biến thể và gói tùy chọn", exact: true })).toBeVisible();
    await expect(page.getByRole("checkbox", { name: "Nội dung có lựa chọn / biến thể", exact: true })).toBeVisible();
  }, { expectedHeading: "Thêm sản phẩm / dịch vụ" });

  await capturePreparedCase("sel-011-service", async () => {
    await page.getByRole("combobox", { name: "Loại", exact: true }).click();
    await page.getByRole("option", { name: "Dịch vụ", exact: true }).click();
    await expect(page.getByRole("combobox", { name: "Quản lý tồn kho", exact: true })).toHaveCount(0);
    await expect(page.getByRole("region", { name: "Biến thể và gói tùy chọn", exact: true })).toBeVisible();
    await expect(page.getByRole("checkbox", { name: "Dịch vụ có nhiều gói", exact: true })).toBeVisible();
  }, { expectedHeading: "Thêm sản phẩm / dịch vụ" });

  await capturePreparedCase("sel-012-variants", async () => {
    await page.getByRole("combobox", { name: "Loại", exact: true }).click();
    await page.getByRole("option", { name: "Dịch vụ", exact: true }).click();
    await page.getByRole("checkbox", { name: "Dịch vụ có nhiều gói" }).check();
    await page.getByRole("textbox", { name: "Tên lựa chọn", exact: true }).fill("Số máy");
    await page.getByRole("textbox", { name: "Các giá trị", exact: true }).fill("1 máy, 2 máy, 3 máy");
    const prices = page.getByRole("spinbutton", { name: /Giá chính xác \(VND\)/ });
    await expect(prices).toHaveCount(3);
    await prices.nth(0).fill("150000");
    await prices.nth(1).fill("280000");
    await prices.nth(2).fill("400000");
    await expect(page.getByText("Mỗi gói có giá và trạng thái nhận đơn riêng tại điểm bán. Nội dung đã tạo hiện chưa hỗ trợ chỉnh cấu trúc gói.", { exact: true })).toBeVisible();
  }, {
    expectedHeading: "Thêm sản phẩm / dịch vụ",
    evidenceFocus: () => page.getByLabel("Giá các gói dịch vụ"),
  });

  await capturePreparedCase("sel-013-resale", async () => {
    await page.getByRole("combobox", { name: "Loại sản phẩm", exact: true }).click();
    await page.getByRole("option", { name: "Thanh lý", exact: true }).click();
    await page.getByRole("combobox", { name: "Danh mục", exact: true }).click();
    await page.getByRole("option", { name: "Tạp hóa", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Danh mục này chưa có tùy chọn tình trạng để chọn.");
  }, { expectedHeading: "Thêm sản phẩm / dịch vụ" });

  await capturePreparedCase("sel-catalog-lifecycle", async () => {
    await expect(page.getByRole("main").getByLabel("Hành động vòng đời")).toBeVisible();
  }, {
    expectedHeading: "Chỉnh sửa catalog",
    evidenceFocus: () => page.getByRole("main").getByLabel("Hành động vòng đời"),
  });

  const mediaUploadEndpoint = "**/api/v1/media/uploads";
  await page.route(mediaUploadEndpoint, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    await route.fulfill({
      status: 503,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "MEDIA_STORAGE_UNAVAILABLE", message: "Storage unavailable." } }),
    });
  });
  await capturePreparedCase("sel-catalog-media-error", async () => {
    await page.getByRole("textbox", { name: "Tên", exact: true }).fill("Sản phẩm giữ nguyên khi tải ảnh lỗi");
    await page.getByRole("combobox", { name: "Danh mục", exact: true }).click();
    await page.getByRole("option", { name: "Tạp hóa", exact: true }).click();
    await page.getByRole("combobox", { name: "Điểm bán", exact: true }).click();
    await page.getByRole("option", { name: /^Chợ Xanh Grand Park(?: —|$)/ }).click();
    await expect(page.locator('input[name="locationId"]')).toHaveValue(fixture.locationId);
    await page.getByRole("spinbutton", { name: "Giá chính xác (VND)", exact: true }).fill("85000");
    await page.getByLabel("Ảnh catalog", { exact: true }).setInputFiles(
      path.resolve(process.cwd(), "../../docs/academic-report/assets/success.png"),
    );
    const submit = page.getByRole("button", { name: "Tạo nội dung", exact: true });
    await expect(submit).toBeEnabled();
    await submit.click();
    await expect(page.getByText("Không thể tải ảnh lên. Thông tin đã nhập được giữ lại; hãy thử lại.", { exact: true })).toBeVisible();
    await expect(page.getByRole("textbox", { name: "Tên", exact: true })).toHaveValue("Sản phẩm giữ nguyên khi tải ảnh lỗi");
    await expect(page.getByRole("status").filter({ hasText: "Đã chọn 1 ảnh (Ảnh đầu tiên sẽ là ảnh chính):" })).toBeVisible();
  }, {
    expectedHeading: "Thêm sản phẩm / dịch vụ",
    expectedConsoleErrors: ["Failed to load resource: the server responded with a status of 503 (Service Unavailable)"],
    evidenceFocus: () => page.getByRole("alert").filter({ hasText: "Không thể tải ảnh lên" }),
  });
  await page.unroute(mediaUploadEndpoint);

  const coverageConflictEndpoint = "**/api/v1/merchants/*/locations/*/delivery-coverages/*";
  await page.route(coverageConflictEndpoint, async (route) => {
    if (route.request().method() !== "PATCH") return route.continue();
    await route.fulfill({
      status: 412,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "RESOURCE_VERSION_CONFLICT", message: "Stale coverage version." } }),
    });
  });
  await capturePreparedCase("sel-005-conflict", async () => {
    const productCoverage = page.locator("article").filter({ hasText: "Giao sản phẩm" });
    await productCoverage.getByRole("button", { name: "Sửa", exact: true }).click();
    await page.getByRole("button", { name: "Lưu phạm vi", exact: true }).click();
    await expect(page.getByText("Phạm vi đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.", { exact: true })).toBeVisible();
  }, {
    expectedHeading: "Phạm vi phục vụ",
    expectedConsoleErrors: ["Failed to load resource: the server responded with a status of 412 (Precondition Failed)"],
  });
  await page.unroute(coverageConflictEndpoint);

  const shippingConflictEndpoint = "**/api/v1/merchants/*/locations/*/shipping-policies/*";
  await page.route(shippingConflictEndpoint, async (route) => {
    if (route.request().method() !== "PATCH") return route.continue();
    await route.fulfill({
      status: 412,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "RESOURCE_VERSION_CONFLICT", message: "Stale shipping policy version." } }),
    });
  });
  await capturePreparedCase("sel-006-conflict", async () => {
    await page.getByRole("button", { name: "Lưu phí giao", exact: true }).click();
    await expect(page.getByText("Phí giao hàng đã được thay đổi ở nơi khác. Tải dữ liệu mới rồi thử lại.", { exact: true })).toBeVisible();
  }, {
    expectedHeading: "Phí giao hàng",
    expectedConsoleErrors: ["Failed to load resource: the server responded with a status of 412 (Precondition Failed)"],
  });
  await page.unroute(shippingConflictEndpoint);

  const inventoryConflictEndpoint = "**/api/v1/merchants/*/inventory/*/set-stock";
  await page.route(inventoryConflictEndpoint, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    await route.fulfill({
      status: 422,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "INVENTORY_BELOW_RESERVED_QUANTITY", message: "Reserved quantity changed." } }),
    });
  });
  await capturePreparedCase("sel-inventory-conflict", async () => {
    const productInventory = page.getByRole("listitem").filter({ hasText: "Rau củ theo mùa" });
    const reservedDefinition = productInventory.getByText("Đã giữ", { exact: true }).locator("..").getByRole("definition");
    const reservedQuantity = Number(await reservedDefinition.textContent());
    expect(reservedQuantity).toBeGreaterThan(0);
    await expect(reservedDefinition).toHaveText(String(reservedQuantity));
    await productInventory.getByRole("spinbutton", { name: "Đặt tồn thực tế" }).fill(String(reservedQuantity));
    await productInventory.getByRole("button", { name: "Lưu tồn kho", exact: true }).click();
    await expect(productInventory.getByRole("alert")).toContainText(`Không thể đặt tồn kho thấp hơn số lượng đã giữ (${reservedQuantity})`);
  }, {
    expectedHeading: "Tồn kho",
    expectedConsoleErrors: ["Failed to load resource: the server responded with a status of 422 (Unprocessable Entity)"],
    evidenceFocus: () => page.getByRole("listitem").filter({ hasText: "Rau củ theo mùa" }).getByRole("alert"),
  });
  await page.unroute(inventoryConflictEndpoint);

  await capturePreparedCase("need-006-candidates", async () => {
    const candidateNeed = page.getByRole("article").filter({ hasText: "Cần rau củ theo mùa" });
    await candidateNeed.getByRole("button", { name: /Phản hồi/ }).click();
    const candidates = page.getByRole("region", { name: "Chọn nội dung phản hồi" });
    await expect(candidates).toBeVisible();
    const productOffer = candidates.getByRole("checkbox", { name: /Rau củ theo mùa/ });
    await expect(productOffer).toBeVisible();
    await productOffer.check();
    await expect(candidates.getByText(/^Đã chọn 1\//)).toBeVisible();
  }, {
    expectedHeading: "Cần mua phù hợp",
    evidenceFocus: () => page.locator(".seller-need-candidates__summary"),
  });

  await capturePreparedCase("need-006-existing", async () => {
    const respondedNeed = page.getByRole("article").filter({ hasText: "Cần kệ gỗ thanh lý" });
    await expect(respondedNeed.getByText("Đã phản hồi", { exact: true })).toBeVisible();
    await expect(respondedNeed.getByRole("button", { name: /Phản hồi/ })).toHaveCount(0);
    await respondedNeed.getByRole("button", { name: /Xem phản hồi/ }).click();
    const existingResponse = page.getByRole("region", { name: "Phản hồi đã gửi" });
    await expect(existingResponse).toBeVisible();
    await expect(existingResponse.getByText("Phản hồi đang hoạt động", { exact: true })).toBeVisible();
    await expect(existingResponse.locator(".seller-need-existing__item")).toHaveCount(1);
    await expect(existingResponse.locator(".seller-need-existing__sent")).toContainText("Đã gửi");
  }, {
    expectedHeading: "Cần mua phù hợp",
  });

  const emptyCandidateEndpoint = `**/api/v1/merchants/*/need-requests/${fixture.emptyCandidateNeedId}/eligible-items*`;
  await page.route(emptyCandidateEndpoint, async (route) => {
    if (route.request().method() !== "GET") return route.continue();
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ items: [], hasMore: false, nextCursor: null, maxResponseItems: 3 }),
    });
  });
  await capturePreparedCase("need-006-empty", async () => {
    const emptyNeed = page.getByRole("article").filter({ hasText: "Cần mật ong địa phương" });
    await emptyNeed.getByRole("button", { name: /Phản hồi/ }).click();
    const candidates = page.getByRole("region", { name: "Chọn nội dung phản hồi" });
    await expect(candidates.getByText("Chưa có nội dung phù hợp để chọn.", { exact: true })).toBeVisible();
    await expect(candidates.getByRole("button", { name: "Phản hồi nhu cầu" })).toHaveCount(0);
  }, {
    expectedHeading: "Cần mua phù hợp",
    evidenceFocus: () => page.locator(".seller-need-candidates__summary"),
  });
  await page.unroute(emptyCandidateEndpoint);

  for (const caseId of ["sel-016-reject", "sel-016-reject-360"]) {
    await capturePreparedCase(caseId, async () => {
      await page.getByRole("button", { name: "Từ chối", exact: true }).click();
      const dialog = page.getByRole("dialog", { name: "Từ chối đơn" });
      await expect(dialog).toBeVisible();
      await expect(dialog.getByLabel("Lý do (bắt buộc)")).toBeVisible();
      await expect(dialog.getByRole("button", { name: "Từ chối", exact: true })).toBeDisabled();
    });
  }

  await capturePreparedCase("sel-017-cancel", async () => {
    await page.getByRole("button", { name: "Hủy đơn", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Hủy đơn" });
    await expect(dialog).toBeVisible();
    await expect(dialog.getByLabel("Lý do (bắt buộc)")).toBeVisible();
    await expect(dialog.getByRole("button", { name: "Hủy đơn", exact: true })).toBeDisabled();
  });

  const conflictOrderId = "12121212-1212-4121-8121-121212121221";
  const conflictEndpoint = `**/api/v1/orders/${conflictOrderId}/reject`;
  const conflictDetailEndpoint = `**/api/v1/orders/${conflictOrderId}`;
  let conflictRaised = false;
  await page.route(conflictDetailEndpoint, async (route) => {
    if (route.request().method() !== "GET" || !conflictRaised) return route.continue();
    const response = await route.fetch();
    const detail = await response.json();
    await route.fulfill({ response, json: { ...detail, allowedActions: [] } });
  });
  await page.route(conflictEndpoint, async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    conflictRaised = true;
    await route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({ error: { code: "ORDER_STATE_CONFLICT", message: "Order state changed." } }),
    });
  });
  await capturePreparedCase("sel-015-conflict", async () => {
    await page.getByRole("button", { name: "Từ chối", exact: true }).click();
    const dialog = page.getByRole("dialog", { name: "Từ chối đơn" });
    await dialog.getByLabel("Lý do (bắt buộc)").fill("Đơn đã thay đổi trong lúc xử lý");
    await dialog.getByRole("button", { name: "Từ chối", exact: true }).click();
    await expect(page.getByRole("status")).toContainText("Đơn hàng đã thay đổi");
    await expect(page.getByRole("button", { name: "Xác nhận đơn", exact: true })).toHaveCount(0);
    await expect(page.getByRole("button", { name: "Từ chối", exact: true })).toHaveCount(0);
    await expect(page.getByText("Hiện không có thao tác khả dụng cho đơn này.", { exact: true })).toBeVisible();
  }, {
    expectedConsoleErrors: ["Failed to load resource: the server responded with a status of 409 (Conflict)"],
    evidenceFocus: () => page.getByRole("region", { name: "Hành động khả dụng" }),
  });
  await page.unroute(conflictEndpoint);
  await page.unroute(conflictDetailEndpoint);

  await capturePreparedCase("sel-019-empty", async () => {
    await expect(page.getByRole("heading", { name: "Chưa có thông báo nội dung", exact: true, level: 2 })).toBeVisible();
  }, {
    expectedHeading: "Thông báo kiểm duyệt",
    merchantId: fixture.emptyNoticeMerchantId,
  });
});
