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

test("populated Public Buyer P0 journey reaches checkout success on the first commit", async ({ page }) => {
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
  await page.getByLabel("Vĩ độ").fill("10.842");
  await page.getByLabel("Kinh độ").fill("106.842");
  await page.getByLabel("Tên khu vực").fill("Vinhomes Grand Park");
  await page.getByRole("button", { name: "Dùng vị trí thủ công" }).click();
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

  await page.goto("/cart");
  await expect(page.getByRole("heading", { name: "Giỏ hàng", exact: true })).toBeVisible();
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
