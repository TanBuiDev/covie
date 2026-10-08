import { expect, test } from "@playwright/test";

test.use({ channel: "chrome", trace: "off", video: "off" });
test.skip(process.env.COVIE_PASSWORD_RESET_FIXTURE !== "1", "Isolated recovery responses; never changes a real password.");

for (const outcome of ["success", "validation", "expired"] as const) {
  test(`password reset save gives visible feedback: ${outcome}`, async ({ page }) => {
    let writes = 0;
    await page.route("**/api/v1/auth/csrf", route => route.fulfill({ json: { headerName: "X-CSRF-Token", token: "fixture" } }));
    await page.route("**/api/v1/auth/password/reset-with-google", async route => {
      writes++;
      expect(route.request().method()).toBe("POST");
      expect(route.request().postDataJSON()).toEqual({ password: "FixturePassword123!" });
      if (outcome === "success") return route.fulfill({ status: 200, json: {} });
      if (outcome === "validation") return route.fulfill({ status: 422, json: { error: { code: "VALIDATION_FAILED", fields: { password: ["Mật khẩu thử nghiệm bị từ chối."] } } } });
      return route.fulfill({ status: 409, json: { error: { code: "PASSWORD_RECOVERY_NOT_AVAILABLE" } } });
    });
    await page.goto("/reset-password");
    await expect(page.getByRole("heading", { name: "Đặt lại mật khẩu", exact: true })).toBeVisible();
    await page.getByLabel("Mật khẩu mới", { exact: true }).fill("FixturePassword123!");
    await page.getByLabel("Xác nhận mật khẩu mới", { exact: true }).fill("FixturePassword123!");
    await page.getByRole("button", { name: "Lưu mật khẩu", exact: true }).click();
    if (outcome === "success") await expect(page).toHaveURL(/\/$/);
    else if (outcome === "validation") await expect(page.getByText("Mật khẩu thử nghiệm bị từ chối.", { exact: true })).toBeVisible();
    else await expect(page.getByRole("alert").filter({ hasText: "Hãy xác thực Google lại" })).toBeVisible();
    expect(writes).toBe(1);
  });
}
