import { expect, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import { resolve } from "node:path";

test.use({ channel: "chrome", video: "off", trace: "off", serviceWorkers: "block" });
test.skip(process.env.COVIE_AD_FIXTURE !== "1", "Opt-in isolated browser API fixtures; no real campaign writes.");

for (const viewport of [{ width: 390, height: 844 }, { width: 1366, height: 768 }, { width: 1920, height: 1080 }]) {
  test(`home/search ad geometry and telemetry ${viewport.width}`, async ({ page }) => {
    const events: { type: string; deliveryToken: string }[] = [];
    await page.route("https://ad-destination.test/landing", route => route.fulfill({ contentType: "text/html; charset=utf-8", body: "<h1>Đích quảng cáo thử nghiệm</h1>" }));
    await page.setViewportSize(viewport);
    await page.addInitScript(() => localStorage.setItem("covie:browse-location:v1", JSON.stringify({ version: 1, location: { kind: "coordinates", lat: 10.84, lng: 106.81, label: "Khu vực thử nghiệm" } })));
    await page.route("**/api/v1/**", async route => {
      const request = route.request(); const url = new URL(request.url());
      if (url.pathname === "/api/v1/auth/csrf") return route.fulfill({ json: { headerName: "X-CSRF-TOKEN", token: "fixture" } });
      if (url.pathname.endsWith("/events")) { events.push(request.postDataJSON()); return route.fulfill({ status: 204 }); }
      if (request.method() !== "GET") return route.fulfill({ status: 401, json: { error: { code: "AUTH_REQUIRED" } } });
      if (url.pathname === "/api/v1/ads/active") {
        const placement = url.searchParams.get("placement");
        return route.fulfill({ json: [0, 1].map(i => ({ id: `fixture-ad-${i}`, title: i ? "Gian hàng nổi bật" : "Khám phá quanh bạn", body: "Nội dung quảng cáo thử nghiệm", ctaLabel: "Xem ngay", creativeType: "CSS", template: i ? "SPOTLIGHT" : "NEARBY", targetUrl: "https://ad-destination.test/landing", placement, endsAt: "2099-01-01T00:00:00Z", deliveryExpiresAt: "2099-01-01T00:00:00Z", deliveryToken: `${placement}-${i}` })) });
      }
      if (url.pathname === "/api/v1/categories") return route.fulfill({ json: [{ id: "food", name: "Ăn uống" }] });
      if (url.pathname === "/api/v1/catalog/items") return route.fulfill({ json: { page: { items: Array.from({ length: 8 }, (_, i) => ({ id: `item-${i}`, name: `Sản phẩm thử ${i}`, type: "PRODUCT", listingKind: "STANDARD", priceFrom: { amount: 65000, currency: "VND" }, merchant: { id: "merchant", name: "Gian hàng thử" }, resolvedLocation: { id: "location", name: "Khu vực thử", distanceMeters: 300 } })), hasMore: false } } });
      return route.fulfill({ json: [] });
    });
    const directory = resolve("../../.local/ad-engine/browser"); mkdirSync(directory, { recursive: true });
    for (const path of ["/", "/search?q=fixture"]) {
      await page.goto(path);
      await expect(page).toHaveURL(path === "/" ? /\/$/ : /\/search\?q=fixture$/);
      if (path === "/") await expect(page.getByRole("heading", { name: "Khám phá danh mục", exact: true })).toBeVisible();
      else {
        await expect(page.getByRole("heading", { name: "Sản phẩm thử 0", exact: true })).toBeVisible();
        await expect(page.getByRole("heading", { name: "Kết quả tìm kiếm", exact: true, includeHidden: true })).toBeAttached();
      }
      await expect(page.getByRole("link", { name: "Khám phá quanh bạn — Xem ngay" }).first()).toBeVisible();
      const rail = page.locator('[data-ad-placement="SIDEBAR_LEFT"]');
      if (viewport.width === 1920) {
        await expect(rail).toBeVisible();
        const bounds = await rail.boundingBox(); const content = await page.locator("main.app-main").boundingBox();
        expect(bounds!.x + bounds!.width).toBeLessThan(content!.x);
        await page.evaluate(() => window.scrollTo(0, 300));
        const scrolled = await rail.boundingBox(); expect(scrolled!.y).toBeGreaterThanOrEqual(95);
      } else await expect(rail).toHaveCount(0);
      const overflow = await page.evaluate(() => ({ total: document.documentElement.scrollWidth, nodes: Array.from(document.querySelectorAll("main *")).filter(node => {
        if (node.getBoundingClientRect().right <= innerWidth + 1) return false;
        let parent = node.parentElement;
        while (parent) { if (getComputedStyle(parent).overflowX !== "visible") return false; parent = parent.parentElement; }
        return true;
      }).map(node => ({ tag: node.tagName, className: node.className, right: node.getBoundingClientRect().right, width: node.getBoundingClientRect().width })).slice(0, 14) }));
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), JSON.stringify(overflow)).toBe(true);
      const slot = page.locator('[data-ad-placement="IN_FEED"]').first();
      await slot.scrollIntoViewIfNeeded();
      await expect.poll(() => events.some(event => event.type === "IMPRESSION" && event.deliveryToken === "IN_FEED-0")).toBe(true);
      await slot.getByRole("button", { name: "Tạm dừng xoay quảng cáo" }).click();
      await expect(slot.getByRole("button", { name: "Tiếp tục xoay quảng cáo" })).toBeVisible();
      await slot.getByRole("button", { name: "Quảng cáo tiếp theo" }).click();
      await expect(slot.getByRole("link", { name: "Gian hàng nổi bật — Xem ngay" })).toBeVisible();
      await page.screenshot({ path: resolve(directory, `${path === "/" ? "home" : "search"}-${viewport.width}.png`) });
    }
    await page.locator('[data-ad-placement="IN_FEED"]').first().getByRole("link", { name: "Gian hàng nổi bật — Xem ngay" }).click();
    await expect(page).toHaveURL("https://ad-destination.test/landing");
    await expect(page.getByRole("heading", { name: "Đích quảng cáo thử nghiệm" })).toBeVisible();
    await expect.poll(() => events.some(event => event.type === "CLICK" && event.deliveryToken === "IN_FEED-1")).toBe(true);
  });
}
