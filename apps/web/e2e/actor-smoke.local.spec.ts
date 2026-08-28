import { expect, test } from "@playwright/test";
import { localFixtureEnabled, loginAs, readLocalUiFixture } from "./fixtures/local-actors";

test.skip(!localFixtureEnabled, "Run pnpm ui:verify-local with the local Docker stack running.");

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
});

test("deterministic Admin fixture can open operational shop data", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.admin);
  await page.goto("/admin/shops");
  await expect(page.getByRole("heading", { name: "Cửa hàng & điểm bán" })).toBeVisible();
  await expect(page.getByText("Cửa hàng Chợ Xanh").first()).toBeVisible();
});
