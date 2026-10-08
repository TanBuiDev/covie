import { expect, test, type Page } from "@playwright/test";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import path from "node:path";

type LocalFixture = {
  buyerEmail: string;
  buyerPassword: string;
  merchantId: string;
  productItemId: string;
  serviceItemId: string;
  resaleItemId: string;
  addressId: string;
};

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
const evidenceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review");
const enabled = process.env.COVIE_UI_FIXTURE === "1" && existsSync(fixturePath);

test.skip(!enabled, "Run pnpm ui:verify-local with the local Docker stack running.");

async function screenshotAt(page: Page, route: string, width: number, height: number) {
  await page.setViewportSize({ width, height });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.addStyleTag({ content: "nextjs-portal{display:none!important}" });
  await page.screenshot({
    path: path.join(evidenceDirectory, `${route}-${width}.png`),
  });
}

async function captureResponsive(page: Page, route: string) {
  await screenshotAt(page, route, 390, 844);
  await screenshotAt(page, route, 360, 800);
  await screenshotAt(page, route, 1280, 900);
  await page.setViewportSize({ width: 390, height: 844 });
}

async function screenshotNamed(page: Page, filename: string, width: number, height: number) {
  await page.setViewportSize({ width, height });
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.addStyleTag({ content: "nextjs-portal{display:none!important}" });
  await page.screenshot({ path: path.join(evidenceDirectory, filename), fullPage: false });
}

test("populated Public Buyer P0 journey reaches checkout success on the first commit", async ({ page }) => {
  test.setTimeout(120_000);
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as LocalFixture;
  mkdirSync(evidenceDirectory, { recursive: true });
  const pageErrors: string[] = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeEnabled();
  await page.getByRole("textbox", { name: "Email" }).fill(fixture.buyerEmail);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture.buyerPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);

  await page.getByRole("button", { name: "Chọn vị trí" }).click();
  await page.getByRole("radio", { name: /Nhà — S5\.03/ }).check();
  await page.getByRole("button", { name: "Dùng địa chỉ đã chọn" }).click();
  await expect(page.getByRole("heading", { name: "Rau củ theo mùa" })).toBeVisible();
  await captureResponsive(page, "pub-001-home");

  await page.getByRole("link", { name: "Tìm món đồ, dịch vụ..." }).click();
  await expect(page.getByRole("heading", { name: "Khám phá" })).toBeVisible();
  await page.getByRole("searchbox", { name: "Tìm món đồ, dịch vụ" }).fill("Rau củ");
  await page.getByRole("searchbox", { name: "Tìm món đồ, dịch vụ" }).press("Enter");
  await expect(page.getByRole("heading", { name: "Kết quả tìm kiếm" })).toBeVisible();
  await expect(page.getByRole("link", { name: "Rau củ theo mùa", exact: true })).toBeVisible();
  await captureResponsive(page, "pub-004-search-results");

  await page.getByRole("link", { name: "Rau củ theo mùa", exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/items/${fixture.productItemId}`));
  await expect(page.getByRole("heading", { name: "Rau củ theo mùa" })).toBeVisible();
  await captureResponsive(page, "pub-006-item-detail");
  await page.getByRole("button", { name: "Thêm vào giỏ" }).click();
  await expect(page.getByText("Đã thêm vào giỏ hàng.")).toBeVisible();

  await page.goto(`/items/${fixture.serviceItemId}`);
  await expect(page).toHaveURL(new RegExp(`/items/${fixture.serviceItemId}`));
  await expect(page.getByRole("heading", { name: "Chi tiết dịch vụ", exact: true })).toBeVisible();
  await expect(page.getByRole("radio", { name: /2 máy/ })).toBeChecked();
  await captureResponsive(page, "pub-007-service-detail");

  await page.goto(`/items/${fixture.resaleItemId}`);
  await expect(page).toHaveURL(new RegExp(`/items/${fixture.resaleItemId}`));
  const resaleDetail = page.getByRole("article");
  await expect(resaleDetail.getByRole("heading", { name: "Chi tiết thanh lý", exact: true })).toBeVisible();
  await expect(resaleDetail.getByRole("heading", { name: "Kệ gỗ thanh lý", exact: true })).toBeVisible();
  await expect(resaleDetail.getByText("Tình trạng", { exact: true })).toBeVisible();
  await expect(resaleDetail.getByText("Vị trí giao dịch", { exact: true })).toBeVisible();
  await expect(resaleDetail.getByRole("status", { name: "Số lượng" })).toHaveCount(0);
  await captureResponsive(page, "pub-008-resale-detail");

  await page.goto(`/merchants/${fixture.merchantId}`);
  await expect(page).toHaveURL(new RegExp(`/merchants/${fixture.merchantId}`));
  const merchantProfile = page.getByRole("article");
  await expect(merchantProfile.getByRole("heading", { name: "Cửa hàng", exact: true })).toBeVisible();
  await expect(merchantProfile.getByRole("heading", { name: "Cửa hàng Chợ Xanh", exact: true })).toBeVisible();
  await expect(merchantProfile.getByText("96%")).toBeVisible();
  await expect(merchantProfile.getByText("4,8 · 96 đánh giá")).toBeVisible();
  await expect(merchantProfile.getByRole("link", { name: "Mở Zalo" })).toHaveAttribute("href", "https://zalo.me/0909123456");
  await captureResponsive(page, "pub-009-shop-profile");
  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
  await page.screenshot({ path: path.join(evidenceDirectory, "pub-009-shop-profile-bottom-390.png") });
  await page.evaluate(() => window.scrollTo(0, 0));

  await page.goto("/me/addresses");
  await expect(page.getByRole("heading", { name: "Địa chỉ của tôi" })).toBeVisible();
  await expect(page.getByText("Nhà · Nguyễn Văn Minh").first()).toBeVisible();
  await expect(page.getByText("Công ty · Nguyễn Văn Minh").first()).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Điều hướng chính" })).toHaveCount(0);
  await captureResponsive(page, "buy-003-address-list");
  await page.getByRole("button", { name: "Xóa Nhà" }).click();
  await expect(page.getByRole("heading", { name: "Xóa địa chỉ này?" })).toBeVisible();
  await screenshotAt(page, "buy-003-address-delete", 390, 844);
  await page.getByRole("button", { name: "Giữ địa chỉ" }).click();

  await page.goto("/me/addresses/new");
  await expect(page.getByRole("heading", { name: "Thêm địa chỉ" }).first()).toBeVisible();
  await screenshotNamed(page, "buy-004-new-327-4086-390x844.png", 390, 844);
  await screenshotNamed(page, "buy-004-new-327-4086-360x800.png", 360, 800);
  await screenshotNamed(page, "buy-004-new-564-5448-1024x900.png", 1024, 900);
  await screenshotNamed(page, "buy-004-new-564-5448-1280x900.png", 1280, 900);

  await page.setViewportSize({ width: 390, height: 844 });
  const createAddressForm = page.locator("form:visible");
  await createAddressForm.getByLabel("Nhãn *").fill("Nhà E2E");
  await createAddressForm.getByLabel("Người nhận *").fill("Nguyễn Văn Minh");
  await createAddressForm.getByLabel("Số điện thoại *").fill("0909123456");
  await createAddressForm.getByLabel("Địa chỉ *").fill("S5.03, Vinhomes Grand Park");
  await createAddressForm.getByLabel("Ghi chú").fill("Giao tại sảnh chính");
  await createAddressForm.getByRole("button", { name: "Lưu địa chỉ" }).click();
  await expect(page).toHaveURL(/\/me\/addresses$/);
  await expect(page.getByText("Nhà E2E · Nguyễn Văn Minh").first()).toBeVisible();

  await page.goto(`/me/addresses/${fixture.addressId}`);
  await expect(page.getByRole("heading", { name: "Chỉnh sửa địa chỉ" }).first()).toBeVisible();
  await expect(page.getByLabel("Nhãn *").first()).toHaveValue("Nhà");
  await screenshotNamed(page, "buy-004-edit-327-4086-390x844.png", 390, 844);
  await screenshotNamed(page, "buy-004-edit-327-4086-360x800.png", 360, 800);
  await screenshotNamed(page, "buy-004-edit-564-5873-1024x900.png", 1024, 900);
  await screenshotNamed(page, "buy-004-edit-564-5448-1280x900.png", 1280, 900);
  const editAddressForm = page.locator("form:visible");
  await editAddressForm.getByLabel("Ghi chú").fill("Gọi trước khi giao");
  await editAddressForm.getByRole("button", { name: "Lưu thay đổi" }).click();
  await expect(page).toHaveURL(/\/me\/addresses$/);

  await page.route("**/api/v1/me/addresses*", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    await route.fulfill({
      status: 422,
      contentType: "application/json",
      headers: {
        "access-control-allow-origin": "http://localhost:3000",
        "access-control-allow-credentials": "true",
      },
      body: JSON.stringify({
        error: {
          code: "VALIDATION_FAILED",
          message: "Dữ liệu chưa hợp lệ.",
          fields: { phone: ["INVALID"], addressText: ["REQUIRED"] },
        },
      }),
    });
  });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/me/addresses/new");
  const invalidAddressForm = page.locator("form:visible");
  await invalidAddressForm.getByLabel("Nhãn *").fill("Nhà lỗi");
  await invalidAddressForm.getByLabel("Người nhận *").fill("Nguyễn Văn Minh");
  await invalidAddressForm.getByLabel("Số điện thoại *").fill("090");
  await invalidAddressForm.getByLabel("Địa chỉ *").fill("Địa chỉ sẽ bị từ chối");
  await invalidAddressForm.getByRole("button", { name: "Lưu địa chỉ" }).click();
  await expect(page.getByText("Giá trị không hợp lệ.")).toBeVisible();
  await expect(page.getByText("Trường này là bắt buộc.")).toBeVisible();
  await expect(invalidAddressForm.getByLabel("Nhãn *")).toHaveValue("Nhà lỗi");
  await screenshotNamed(page, "buy-004-new-error-327-4316-390x844.png", 390, 844);
  await screenshotNamed(page, "buy-004-new-error-564-5613-1280x900.png", 1280, 900);
  await page.unroute("**/api/v1/me/addresses*");

  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: "Giỏ hàng của bạn", exact: true })).toBeVisible();
  await captureResponsive(page, "buy-001-cart-list");
  await page.getByRole("link", { name: "Xem và thanh toán giỏ này" }).click();
  await expect(page).toHaveURL(/\/cart\/[0-9a-f-]+$/);
  await expect(page.getByRole("heading", { name: "Giỏ sản phẩm", exact: true })).toBeVisible();
  await captureResponsive(page, "buy-002-cart-detail");

  await page.getByRole("link", { name: "Tiếp tục đặt hàng" }).click();
  await expect(page.getByRole("heading", { name: "Xác nhận đơn" })).toBeVisible();
  await page.getByRole("button", { name: "Xem trước" }).click();
  await expect(page.getByRole("button", { name: "Đặt hàng" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Điều hướng chính" })).toHaveCount(0);
  await captureResponsive(page, "buy-005-checkout-preview");

  await page.getByRole("button", { name: "Đặt hàng" }).click();
  await expect(page).toHaveURL(/\/checkout\/success\/[0-9a-f-]+$/);
  await expect(page.getByRole("heading", { name: "Đã tạo đơn hàng" })).toBeVisible();
  await expect(page.getByText(/^Mã đơn: #CV-/)).toBeVisible();
  await captureResponsive(page, "buy-006-checkout-success");

  await page.goto("/orders");
  await expect(page.getByText("HTTP 500")).toHaveCount(0);
  await expect(page.getByText(/^CV-/).first()).toBeVisible();
  expect(pageErrors).toEqual([]);
});
