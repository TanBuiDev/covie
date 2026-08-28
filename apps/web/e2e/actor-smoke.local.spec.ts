import { expect, test } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { localFixtureEnabled, loginAs, readLocalUiFixture } from "./fixtures/local-actors";

test.skip(!localFixtureEnabled, "Run pnpm ui:verify-local with the local Docker stack running.");

const evidenceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review");

async function captureViewports(page: import("@playwright/test").Page, name: string, widths: number[]) {
  mkdirSync(evidenceDirectory, { recursive: true });
  for (const width of widths) {
    const height = width === 360 ? 800 : width === 390 ? 844 : 900;
    await page.setViewportSize({ width, height });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await page.addStyleTag({ content: "nextjs-portal{display:none!important}" });
    await page.screenshot({ path: path.join(evidenceDirectory, `${name}-${width}.png`) });
  }
}

test("deterministic Buyer fixture can open its protected address collection", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.buyer);
  await page.goto("/me/addresses");
  await expect(page.getByRole("heading", { name: "Địa chỉ của tôi" })).toBeVisible();
  await expect(page.getByText("Nhà · Nguyễn Văn Minh").first()).toBeVisible();
});

test("deterministic Seller fixture can open its managed catalog", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.seller);
  await page.goto(`/seller/${fixture.merchantId}/catalog`);
  await expect(page.getByRole("heading", { name: "Danh mục", exact: true })).toBeVisible();
  await expect(page.getByText("Rau củ theo mùa").first()).toBeVisible();
  await page.goto(`/seller/${fixture.merchantId}/catalog/${fixture.productItemId}/edit`);
  await expect(page.getByRole("heading", { name: "Rau củ theo mùa" })).toBeVisible();
  await captureViewports(page, "sel-catalog-edit", [360, 390, 768, 1024, 1280, 1440]);
  await page.goto(`/seller/${fixture.merchantId}/locations/${fixture.locationId}/contacts`);
  await expect(page.getByRole("region", { name: "Kênh liên hệ" })).toBeVisible();
  await captureViewports(page, "sel-contact-edit", [360, 390, 768, 1024, 1280, 1440]);
});

test("deterministic Admin fixture can open operational shop data", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.admin);
  await page.goto("/admin/shops");
  await expect(page.getByRole("heading", { name: "Cửa hàng & điểm bán" })).toBeVisible();
  await expect(page.getByText("Cửa hàng Chợ Xanh").first()).toBeVisible();
  await captureViewports(page, "adm-shops", [1024, 1280, 1440]);
});
