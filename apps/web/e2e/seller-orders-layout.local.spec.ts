import { expect, test } from "@playwright/test";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import path from "node:path";

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
test.skip(process.env.COVIE_UI_FIXTURE !== "1" || !existsSync(fixturePath), "Requires an explicitly owned local fixture.");

test("Seller order controls fit narrow screens while status tabs retain keyboard scrolling", async ({ page }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as { sellerEmail: string; sellerPassword: string; merchantId: string };
  const evidence = path.resolve(process.cwd(), "../../.local/dev/visual-review/billing-seller-orders");
  mkdirSync(evidence, { recursive: true });
  await page.setViewportSize({ width: 360, height: 900 });
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(fixture.sellerEmail);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture.sellerPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL((url) => url.pathname === "/");
  const route = "/seller/" + fixture.merchantId + "/orders";
  await page.goto(route);
  await expect(page).toHaveURL((url) => url.pathname === route);
  await expect(page.getByRole("banner").getByRole("heading", { name: "Đơn hàng", exact: true })).toBeVisible();
  const summary = page.getByRole("region", { name: "Tổng quan tình trạng đơn", exact: true });
  await expect(summary.getByRole("button")).toHaveCount(4);
  for (const width of [360, 390, 1024, 1366]) {
    await page.setViewportSize({ width, height: 900 });
    const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
    expect(scrollWidth, "Seller order document overflow at " + width).toBeLessThanOrEqual(width);
    for (const control of await summary.getByRole("button").all()) {
      await expect(control).toBeVisible();
      const box = await control.boundingBox();
      expect(box).not.toBeNull();
      if (!box) throw new Error("Visible KPI requires real bounds");
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(width);
    }
    const tabs = page.getByRole("tablist", { name: "Lọc đơn theo trạng thái", exact: true }).getByRole("tab");
    await tabs.first().focus();
    for (let index = 1; index < await tabs.count(); index++) await page.keyboard.press("Tab");
    await expect(tabs.last()).toBeFocused();
    const last = await tabs.last().boundingBox();
    expect(last).not.toBeNull();
    if (!last) throw new Error("Keyboard-accessible tab requires real bounds");
    expect(last.x).toBeGreaterThanOrEqual(0);
    expect(last.x + last.width).toBeLessThanOrEqual(width);
    await expect(page.getByRole("searchbox", { name: "Tìm kiếm đơn hàng", exact: true })).toBeVisible();
    await page.screenshot({ path: path.join(evidence, "orders-" + width + ".png") });
  }
});
