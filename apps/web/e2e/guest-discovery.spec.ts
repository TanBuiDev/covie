import { expect, test } from "@playwright/test";

test("guest can open the marketplace shell and navigate to search", async ({ page }) => {
  await page.goto("/");
  await expect(page.getByRole("heading", { name: "Khám phá danh mục" })).toBeVisible();
  const navigation = page.getByRole("navigation", { name: "Điều hướng Buyer" });
  await expect(navigation).toBeVisible();
  await navigation.getByRole("link", { name: "Khám phá" }).click();
  await expect(page).toHaveURL(/\/search$/);
  await expect(page.getByText("Tìm quanh vị trí bạn đã chọn")).toBeVisible();
});

test("retired explore route redirects guests to search", async ({ page }) => {
  await page.goto("/explore");
  await expect(page).toHaveURL(/\/search$/);
});
