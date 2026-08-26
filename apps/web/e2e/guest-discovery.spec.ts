import { expect, test } from "@playwright/test";

test("guest can open the marketplace shell and navigate to search", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Khám phá danh mục" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Điều hướng chính" })).toBeVisible();
  await page.getByRole("link", { name: "Tìm món đồ, dịch vụ..." }).click();
  await expect(page).toHaveURL(/\/search$/);
  await expect(page.getByRole("heading", { name: "Khám phá" })).toBeVisible();
});

test("retired explore route redirects guests to search", async ({ page }) => {
  await page.goto("/explore");
  await expect(page).toHaveURL(/\/search$/);
});
