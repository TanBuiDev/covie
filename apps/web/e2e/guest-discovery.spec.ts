import { expect, test } from "@playwright/test";

test("guest can open the marketplace shell and navigate to search", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Khám phá danh mục" })).toBeVisible();
  const buyerNavigation = page.getByRole("navigation", { name: "Điều hướng Buyer" });
  await expect(buyerNavigation).toBeVisible();
  await buyerNavigation.getByRole("link", { name: "Khám phá", exact: true }).click();
  await expect(page).toHaveURL(/\/search$/);
  await expect(page.getByRole("searchbox", { name: "Tìm món đồ, dịch vụ", exact: true })).toBeVisible();
});

test("retired explore route redirects guests to search", async ({ page }) => {
  await page.goto("/explore");
  await expect(page).toHaveURL(/\/search$/);
});
