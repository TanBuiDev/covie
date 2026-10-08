import { expect, test } from "@playwright/test";
import type { components } from "@covie/api-client";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";

test.use({ channel: "chrome", trace: "off", video: "off" });
const fixturePath = resolve(process.env.COVIE_PROFILE_LOGIN_FIXTURE || "../../.local/dev/ui-fixture.json");
test.skip(process.env.COVIE_PROFILE_EDIT_FIXTURE !== "1" || !existsSync(fixturePath), "Uses existing private buyer login; all profile/media writes are intercepted fixtures.");

test("profile editor preserves fields, serializes saves and previews avatar at mobile and desktop", async ({ page }) => {
  test.setTimeout(90_000);
  const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));
  const loginEmail = fixture.buyerEmail ?? fixture.adminEmail;
  const loginPassword = fixture.buyerPassword ?? fixture.adminPassword;
  const directory = resolve("../../.local/profile-address/browser");
  mkdirSync(directory, { recursive: true });
  await page.goto("/login");
  await page.waitForLoadState("networkidle");
  await page.getByRole("textbox", { name: /^Email/ }).fill(loginEmail);
  await page.getByRole("textbox", { name: /^Mật khẩu/ }).fill(loginPassword);
  const login = page.waitForResponse(response => new URL(response.url()).pathname === "/api/v1/auth/login" && response.request().method() === "POST");
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  expect((await login).status(), "Existing local fixture login must succeed; no account reset is permitted.").toBe(200);
  await expect(page).toHaveURL(/\/$/);
  const currentResponse = await page.request.get("/api/v1/me");
  expect(currentResponse.status()).toBe(200);
  const current: components["schemas"]["MeResponse"] = await currentResponse.json();
  const mediaId = "019cc73b-435e-7000-8000-000000000001";
  const pixel = Buffer.from("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jC4cAAAAASUVORK5CYII=", "base64");
  let failNext = true;
  const commands: components["schemas"]["UpdateMeRequest"][] = [];
  const blocked: string[] = [];
  await page.route("https://fixture-media.invalid/avatar.png", route => route.fulfill({ status: 200 }));
  await page.route("**/api/v1/**", async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (path === "/api/v1/me" && request.method() === "PATCH") {
      const input: components["schemas"]["UpdateMeRequest"] = request.postDataJSON();
      expect(input).not.toHaveProperty("email");
      commands.push(input);
      await new Promise(done => setTimeout(done, 150));
      if (failNext) { failNext = false; return route.fulfill({ status: 503, json: { error: { code: "FIXTURE_UNAVAILABLE", message: "private diagnostic" } } }); }
      return route.fulfill({ json: { ...current, ...input, avatarUrl: input.avatarMediaId ? `data:image/png;base64,${pixel.toString("base64")}` : null } });
    }
    if (path === "/api/v1/media/uploads" && request.method() === "POST") {
      expect(request.postDataJSON()).toMatchObject({ purpose: "USER_AVATAR", contentType: "image/png" });
      return route.fulfill({ json: { mediaId, uploadUrl: "https://fixture-media.invalid/avatar.png", requiredHeaders: {} } });
    }
    if (path === `/api/v1/media/${mediaId}/complete` && request.method() === "POST") return route.fulfill({ json: { mediaId, purpose: "USER_AVATAR", contentType: "image/png", fileSize: pixel.length } });
    if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) { blocked.push(`${request.method()} ${path}`); return route.abort(); }
    return route.continue();
  });
  for (const width of [390, 1366]) {
    failNext = true;
    await page.setViewportSize({ width, height: 900 });
    await page.goto("/me");
    await page.waitForLoadState("networkidle");
    await expect(page).toHaveURL("/me");
    const edit = page.getByRole("button", { name: "Chỉnh sửa hồ sơ", exact: true });
    await expect(edit).toBeVisible();
    await edit.click();
    const form = page.getByRole("form", { name: "Chỉnh sửa hồ sơ" });
    await expect(page.getByLabel("Tên hiển thị", { exact: true })).toBeFocused();
    await page.getByLabel("Tên hiển thị", { exact: true }).fill("Hồ sơ thử nghiệm");
    await page.getByLabel("Số điện thoại", { exact: true }).fill("0907777777");
    await page.getByLabel("Ảnh đại diện", { exact: true }).setInputFiles({ name: "avatar.png", mimeType: "image/png", buffer: pixel });
    const preview = page.getByRole("img", { name: "Xem trước ảnh đại diện" });
    await expect(preview).toHaveAttribute("src", /^blob:/);
    await expect(preview).toBeVisible();
    await expect(page.getByRole("button", { name: "Lưu hồ sơ", exact: true })).toBeEnabled();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: resolve(directory, `profile-edit-${width}.png`), fullPage: true });
    const before = commands.length;
    await form.evaluate(element => { const form = element as HTMLFormElement; form.requestSubmit(); form.requestSubmit(); });
    await expect(form.getByRole("alert")).toContainText("Chưa thể lưu hồ sơ");
    await expect(form.getByRole("alert")).not.toContainText("private diagnostic");
    expect(commands.length).toBe(before + 1);
    await expect(page.getByLabel("Tên hiển thị", { exact: true })).toHaveValue("Hồ sơ thử nghiệm");
    await form.getByRole("button", { name: "Lưu hồ sơ", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Hồ sơ thử nghiệm", exact: true })).toBeVisible();
    await expect(page.getByRole("status")).toContainText("Hồ sơ đã được cập nhật.");
    await expect(edit).toBeFocused();
    await expect(page.getByText("0907777777", { exact: true })).toBeVisible();
    await expect(page.getByRole("img", { name: "Ảnh đại diện của bạn" })).toBeVisible();
    expect(commands.length).toBe(before + 2);
    expect(commands.at(-1)).toMatchObject({ displayName: "Hồ sơ thử nghiệm", phone: "0907777777", avatarMediaId: mediaId });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: resolve(directory, `profile-saved-${width}.png`), fullPage: true });
  }
  expect(blocked).toEqual([]);
});
