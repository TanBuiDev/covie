import { expect, test, type Page } from "@playwright/test";
import {
  loginStagingRole,
  readStagingAuthConfig,
  stagingAuthIsConfigured,
} from "./fixtures/staging-auth-fixture";

test.skip(!stagingAuthIsConfigured(), "Set the HTTPS staging target and all six normal-account credentials.");
test.describe.configure({ mode: "serial" });
test.setTimeout(10 * 60_000);

async function confirmOrderAction(page: Page, openLabel: string, confirmLabel: string) {
  await expect(page.getByRole("button", { name: openLabel, exact: true })).toBeVisible({ timeout: 30_000 });
  await page.getByRole("button", { name: openLabel, exact: true }).click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("dialog").getByRole("button", { name: confirmLabel, exact: true }).click();
}

async function chooseSavedLocation(page: Page) {
  await page.goto("/");
  const chooser = page.getByRole("button", { name: "Chọn vị trí", exact: true });
  await expect(chooser).toBeVisible();
  await chooser.click();
  await page.getByRole("radio").first().check();
  await page.getByRole("button", { name: "Dùng địa chỉ đã chọn", exact: true }).click();
}

async function addNamedItem(page: Page, type: "PRODUCT" | "SERVICE", name: string) {
  await page.goto(`/search?type=${type}&q=${encodeURIComponent(name)}`);
  const result = page.getByRole("link", { name, exact: true }).first();
  await expect(result).toBeVisible({ timeout: 30_000 });
  await result.click();
  await expect(page.getByRole("heading", { name, exact: true })).toBeVisible();
  await page.getByRole("button", { name: "Thêm vào giỏ", exact: true }).click();
  await expect(page.getByRole("status")).toContainText("Đã thêm vào giỏ hàng");
}

async function commitCart(page: Page, kind: "product" | "service"): Promise<string> {
  await page.goto("/cart");
  const linkName = kind === "service" ? "Xem giỏ dịch vụ" : "Xem và thanh toán giỏ này";
  await page.getByRole("link", { name: linkName, exact: true }).first().click();
  await page.getByRole("link", { name: "Tiếp tục đặt hàng", exact: true }).click();
  if (kind === "product") await page.getByRole("radio", { name: "SELLER_DELIVERY" }).check();
  await page.getByRole("button", { name: "Xem trước", exact: true }).click();
  await expect(page.getByRole("button", { name: "Đặt hàng", exact: true }).first()).toBeVisible();
  await page.getByRole("button", { name: "Đặt hàng", exact: true }).first().click();
  await expect(page).toHaveURL(/\/checkout\/success\/[0-9a-f-]+$/i, { timeout: 30_000 });
  return new URL(page.url()).pathname.split("/").at(-1)!;
}

async function readActiveCatalogName(page: Page, view: "PRODUCT" | "SERVICE"): Promise<string> {
  if (view === "SERVICE") {
    await page.getByRole("group", { name: "Lọc loại nội dung" }).getByRole("button", { name: "Dịch vụ" }).click();
  }
  const activeCard = page.locator(".seller-catalog-card").filter({ hasText: "Đang hiển thị" }).first();
  await expect(activeCard).toBeVisible();
  const name = (await activeCard.locator("strong").first().textContent())?.trim();
  expect(name, `Staging Seller needs an ACTIVE ${view} item`).toBeTruthy();
  return name!;
}

test("normal-auth Guest, Buyer, Seller and Admin P0 staging journey", async ({ browser }) => {
  const config = readStagingAuthConfig();
  const guestContext = await browser.newContext({ baseURL: config.baseURL });
  const buyerContext = await browser.newContext({ baseURL: config.baseURL });
  const sellerContext = await browser.newContext({ baseURL: config.baseURL });
  const adminContext = await browser.newContext({ baseURL: config.baseURL });
  const contexts = [guestContext, buyerContext, sellerContext, adminContext];
  const guest = await guestContext.newPage();
  const buyer = await buyerContext.newPage();
  const seller = await sellerContext.newPage();
  const admin = await adminContext.newPage();

  try {
    await guest.goto("/");
    await expect(guest.getByRole("heading", { name: "Khám phá danh mục" })).toBeVisible();
    await guest.goto("/search");
    await expect(guest.getByText("Tìm quanh vị trí bạn đã chọn")).toBeVisible();

    await loginStagingRole(seller, "seller", config);
    await seller.goto("/seller");
    await expect(seller).toHaveURL(/\/seller\/[0-9a-f-]+$/i);
    const merchantId = new URL(seller.url()).pathname.split("/").at(-1)!;
    await seller.goto(`/seller/${merchantId}/catalog`);
    const productName = await readActiveCatalogName(seller, "PRODUCT");
    const serviceName = await readActiveCatalogName(seller, "SERVICE");

    await loginStagingRole(buyer, "buyer", config);
    await chooseSavedLocation(buyer);

    await addNamedItem(buyer, "PRODUCT", productName);
    const productOrderId = await commitCart(buyer, "product");
    await seller.goto(`/seller/${merchantId}/orders/${productOrderId}`);
    await confirmOrderAction(seller, "Xác nhận đơn", "Xác nhận");
    await confirmOrderAction(seller, "Bắt đầu giao", "Bắt đầu giao");
    await confirmOrderAction(seller, "Đã giao", "Đánh dấu đã giao");
    await buyer.goto(`/orders/${productOrderId}`);
    await confirmOrderAction(buyer, "Đã nhận hàng", "Hoàn tất đơn");
    await buyer.goto(`/orders/${productOrderId}/review`);
    await buyer.getByRole("radio", { name: "5 sao" }).click();
    await buyer.getByRole("button", { name: "Gửi đánh giá", exact: true }).click();
    await expect(buyer.getByRole("heading", { name: "Đã gửi đánh giá" })).toBeVisible();

    await addNamedItem(buyer, "SERVICE", serviceName);
    const serviceOrderId = await commitCart(buyer, "service");
    await seller.goto(`/seller/${merchantId}/orders/${serviceOrderId}`);
    await confirmOrderAction(seller, "Xác nhận đơn", "Xác nhận");
    await confirmOrderAction(seller, "Đã thực hiện dịch vụ", "Xác nhận đã thực hiện");
    await buyer.goto(`/orders/${serviceOrderId}`);
    await confirmOrderAction(buyer, "Dịch vụ đã hoàn thành", "Hoàn tất đơn");

    const needTitle = `Staging P0 ${Date.now()}`;
    await buyer.goto("/needs/new");
    await buyer.getByLabel("Tên / nhu cầu").fill(needTitle);
    await buyer.getByLabel("Từ khóa tìm kiếm").fill(productName);
    const expiry = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString().slice(0, 16);
    await buyer.getByLabel("Thời hạn").fill(expiry);
    await buyer.getByRole("button", { name: "Đăng Cần mua", exact: true }).click();
    await expect(buyer).toHaveURL(/\/needs\/[0-9a-f-]+$/i);
    const needId = new URL(buyer.url()).pathname.split("/").at(-1)!;

    await seller.goto(`/seller/${merchantId}/needs`);
    const needCard = seller.getByRole("article").filter({ hasText: needTitle });
    await expect(needCard).toBeVisible({ timeout: 30_000 });
    await needCard.getByRole("button", { name: /Phản hồi/ }).click();
    const responseRegion = seller.getByRole("region", { name: "Chọn nội dung phản hồi" });
    await responseRegion.getByRole("checkbox").first().check();
    await responseRegion.getByRole("button", { name: "Phản hồi nhu cầu", exact: true }).click();
    await expect(responseRegion.getByRole("status")).toContainText("Đã gửi phản hồi");

    await buyer.goto(`/needs/${needId}`);
    await expect(buyer.getByRole("heading", { name: needTitle })).toBeVisible();
    await expect(buyer.getByRole("button", { name: "Nhận tại cửa hàng", exact: true })).toBeVisible({ timeout: 30_000 });
    await buyer.getByRole("button", { name: "Nhận tại cửa hàng", exact: true }).click();
    const createdOrderLink = buyer.getByRole("link", { name: "Xem đơn hàng", exact: true });
    await expect(createdOrderLink).toBeVisible();
    const needOrderHref = await createdOrderLink.getAttribute("href");
    const needOrderId = needOrderHref?.split("/").at(-1);
    expect(needOrderId).toBeTruthy();
    await seller.goto(`/seller/${merchantId}/orders/${needOrderId}`);
    await confirmOrderAction(seller, "Xác nhận đơn", "Xác nhận");
    await confirmOrderAction(seller, "Sẵn sàng nhận", "Đã sẵn sàng");
    await buyer.goto(`/orders/${needOrderId}`);
    await confirmOrderAction(buyer, "Đã nhận hàng", "Hoàn tất đơn");
    await buyer.goto(`/needs/${needId}`);
    await expect(buyer.getByText("Đã đáp ứng", { exact: true })).toBeVisible();

    await expect.poll(async () => {
      await buyer.goto("/me/notifications");
      await buyer.waitForLoadState("networkidle");
      return buyer.locator("ol li").count();
    }, { timeout: 30_000 }).toBeGreaterThan(0);

    await loginStagingRole(admin, "admin", config);
    await admin.goto("/admin/settings");
    await expect(admin.getByRole("heading", { name: "Kiểm duyệt · Đánh giá · Uy tín" })).toBeVisible();
    await admin.goto("/admin/reports");
    await expect(admin.getByRole("heading", { name: "Hàng đợi báo cáo" })).toBeVisible();
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});
