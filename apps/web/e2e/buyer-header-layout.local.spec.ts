import { expect, test } from "@playwright/test";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import path from "node:path";

const fixturePath = path.resolve(process.cwd(), "../../.local/dev/ui-fixture.json");
test.skip(process.env.COVIE_UI_FIXTURE !== "1" || !existsSync(fixturePath), "Requires an explicitly owned local fixture.");

test("current shared header fits real viewports without losing navigation or accessible controls", async ({ page }) => {
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8")) as { buyerEmail: string; buyerPassword: string; candidateNeedId: string };
  const evidence = path.resolve(process.cwd(), "../../.local/dev/visual-review/billing-header");
  mkdirSync(evidence, { recursive: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/login");
  await page.getByRole("textbox", { name: "Email" }).fill(fixture.buyerEmail);
  await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture.buyerPassword);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL((url) => url.pathname === "/");
  await page.goto("/me/addresses/new");
  await expect(page).toHaveURL((url) => url.pathname === "/me/addresses/new");
  await expect(page.getByRole("heading", { name: "Thêm địa chỉ", exact: true })).toBeVisible();
  for (const width of [360, 390, 1024, 1280, 1366, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.evaluate(() => document.fonts.ready);
    await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    const dimensions = await page.evaluate(() => ({ viewport: innerWidth, scroll: document.documentElement.scrollWidth }));
    expect(dimensions.scroll, "Document must not overflow at " + width + "px").toBeLessThanOrEqual(dimensions.viewport);
    if (width < 1024) continue;
    const header = page.locator(".buyer-desktop-header");
    await expect(header).toBeVisible();
    const headerBox = await header.boundingBox();
    expect(headerBox).not.toBeNull();
    const boxes: Array<{ x: number; y: number; width: number; height: number }> = [];
    for (const control of await header.locator("a,button,input[type='search']").all()) {
      await expect(control).toBeVisible();
      await expect(control).toHaveAccessibleName(/\S/);
      const box = await control.boundingBox();
      expect(box).not.toBeNull();
      if (!box || !headerBox) throw new Error("Visible header control requires real geometry");
      expect(box.x).toBeGreaterThanOrEqual(0);
      expect(box.x + box.width).toBeLessThanOrEqual(width);
      expect(box.y).toBeGreaterThanOrEqual(headerBox.y);
      expect(box.y + box.height).toBeLessThanOrEqual(headerBox.y + headerBox.height + 1);
      for (const previous of boxes) {
        const horizontal = Math.min(box.x + box.width, previous.x + previous.width) - Math.max(box.x, previous.x);
        const vertical = Math.min(box.y + box.height, previous.y + previous.height) - Math.max(box.y, previous.y);
        expect(horizontal > 1 && vertical > 1, "Header controls must not overlap").toBe(false);
      }
      boxes.push(box);
    }
    for (const name of ["Trang chủ", "Khám phá", "Cần mua", "Đơn", "Mở quầy / Pass đồ", "Thông báo", "Mở giỏ hàng"]) {
      await expect(header.getByRole("link", { name, exact: true })).toBeVisible();
    }
    await header.getByRole("link", { name: "Khám phá", exact: true }).focus();
    await page.keyboard.press("Tab");
    await expect(header.getByRole("link", { name: "Cần mua", exact: true })).toBeFocused();
    await header.screenshot({ path: path.join(evidence, "header-" + width + ".png") });
  }
  for (const route of ["/needs/new", "/needs/" + fixture.candidateNeedId]) {
    await page.goto(route);
    await expect(page).toHaveURL((url) => url.pathname === route);
    const sidebar = page.locator(route === "/needs/new" ? ".need-create-preview-column" : ".need-detail__sidebar");
    for (const width of [1024, 1280, 1366]) {
      await page.setViewportSize({ width, height: 700 });
      await expect(sidebar).toBeVisible();
      await expect.poll(async () => sidebar.evaluate((element) => {
        const header = document.querySelector(".buyer-desktop-header");
        return Number.parseFloat(getComputedStyle(element).top) - (header?.getBoundingClientRect().height ?? 0);
      })).toBeGreaterThanOrEqual(15);
      if (route === "/needs/new") {
        await page.evaluate(() => scrollTo(0, 500));
        await expect.poll(async () => sidebar.evaluate((element) => element.getBoundingClientRect().top - (document.querySelector(".buyer-desktop-header")?.getBoundingClientRect().bottom ?? 0))).toBeGreaterThanOrEqual(15);
      }
    }
  }
});
