import { expect, test } from "@playwright/test";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

test.use({ channel: "chrome", trace: "off", video: "off", permissions: ["geolocation"], geolocation: { latitude: 10.842, longitude: 106.842 } });
const fixturePath = resolve("../../.local/ad-engine/recovered-login.json");
test.skip(process.env.COVIE_ADDRESS_UNIFIED_FIXTURE !== "1" || !existsSync(fixturePath), "Existing local login; address writes and reverse lookups intercepted.");

test("one delivery address supports GPS, map adjustment and a single verified save at mobile/desktop", async ({ page }) => {
  test.setTimeout(90_000);
  const login = JSON.parse(readFileSync(fixturePath, "utf8"));
  await page.goto("/login");
  await page.getByRole("textbox", { name: /^Email/ }).fill(login.adminEmail);
  await page.getByRole("textbox", { name: /^Mật khẩu/ }).fill(login.adminPassword);
  const authenticated = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/auth/login" && response.request().method() === "POST");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  expect((await authenticated).status()).toBe(200);
  await expect(page).toHaveURL(/\/$/);
  const writes: Record<string, unknown>[] = [];
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path === "/api/v1/me/addresses/reverse-geocode") return route.fulfill({ json: { addressText: "Đường thử nghiệm, TP. Hồ Chí Minh", attribution: "© OpenStreetMap contributors", attributionUrl: "https://www.openstreetmap.org/copyright" } });
    if (path === "/api/v1/me/addresses" && request.method() === "POST") { writes.push(request.postDataJSON()); return route.fulfill({ status: 201, json: { id: "99999999-9999-4999-8999-999999999999", ...request.postDataJSON() } }); }
    if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) return route.abort();
    return route.continue();
  });
  const directory = resolve("../../.local/profile-address/browser"); mkdirSync(directory, { recursive: true });
  for (const width of [390, 1366]) {
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/me/addresses/new");
    await expect(page).toHaveURL("/me/addresses/new");
    const address = page.getByRole("group", { name: "Địa chỉ giao hàng", exact: true });
    await expect(address).toBeVisible();
    await expect(address.getByRole("checkbox")).toHaveCount(0);
    await expect(page.getByText("Điểm giao hàng & Tọa độ", { exact: true })).toHaveCount(0);
    await expect(address.locator("details")).not.toHaveAttribute("open", "");
    await page.getByRole("textbox", { name: /^Nhãn/ }).fill("Nhà thử nghiệm");
    await page.getByRole("textbox", { name: /^Người nhận/ }).fill("Người nhận thử nghiệm");
    await page.getByRole("textbox", { name: /^Số điện thoại/ }).fill("0909123456");
    await address.getByRole("button", { name: "Dùng vị trí hiện tại", exact: true }).click();
    await expect(address.getByLabel(/^Địa chỉ \*/)).toHaveValue("Đường thử nghiệm, TP. Hồ Chí Minh");
    await address.getByLabel(/^Địa chỉ \*/).fill("Căn 1205, tòa S5, địa chỉ thử nghiệm");
    const map = address.getByRole("region", { name: "Bản đồ địa chỉ giao hàng" });
    await expect(map.getByRole("button", { name: "Zoom in" })).toBeVisible();
    await expect(address.getByRole("button", { name: "Chọn điểm ở giữa bản đồ" })).toHaveCount(0);
    const locate = address.getByRole("button", { name: "Dùng vị trí hiện tại", exact: true });
    await expect(locate).toHaveText("");
    await map.scrollIntoViewIfNeeded();
    const mapBounds = (await map.boundingBox())!, locateBounds = (await locate.boundingBox())!;
    expect(locateBounds.x).toBeGreaterThan(mapBounds.x + mapBounds.width - 70);
    expect(locateBounds.y).toBeLessThan(mapBounds.y + 20);
    const beforePreview = await address.locator('input[type="hidden"][name="latitude"]').inputValue();
    await page.mouse.move(mapBounds.x + 90, mapBounds.y + 110);
    await expect(address.getByTestId("delivery-pin-preview")).toBeVisible();
    await expect(address.locator('input[type="hidden"][name="latitude"]')).toHaveValue(beforePreview);
    await map.click({ position: { x: 90, y: 110 } });
    await expect(address.getByLabel(/^Địa chỉ \*/)).toHaveValue("Căn 1205, tòa S5, địa chỉ thử nghiệm");
    await expect(address.locator('input[type="hidden"][name="latitude"]')).not.toHaveValue("10.842");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: resolve(directory, `address-unified-${width}.png`), fullPage: true });
    const before = writes.length;
    await page.getByRole("button", { name: "Lưu địa chỉ", exact: true }).click();
    await expect(page).toHaveURL("/me/addresses");
    expect(writes.length).toBe(before + 1);
    expect(writes.at(-1)).toMatchObject({ addressText: "Căn 1205, tòa S5, địa chỉ thử nghiệm", pinSource: "USER_ADJUSTED" });
    expect(writes.at(-1)?.latitude).not.toBe(10.842);
  }
});
