import { expect, test } from "@playwright/test";

test("guest can open the marketplace shell and navigate to search", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "CoVie" })).toBeVisible();
  await page.getByRole("link", { name: "Khám phá sản phẩm và dịch vụ" }).click();
  await expect(page).toHaveURL(/\/search$/);
  await expect(page.getByRole("heading", { name: "Khám phá quanh bạn" })).toBeVisible();
});

test("retired explore route redirects guests to search", async ({ page }) => {
  await page.goto("/explore");
  await expect(page).toHaveURL(/\/search$/);
});
