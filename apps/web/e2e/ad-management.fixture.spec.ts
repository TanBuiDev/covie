import { expect, test } from "@playwright/test";
import { existsSync, mkdirSync, readFileSync } from "node:fs";
import { resolve } from "node:path";
test.use({ channel: "chrome", video: "off", trace: "off" });
const fixturePath = resolve(process.env.COVIE_AD_LOGIN_FIXTURE || "../../.local/dev/ui-fixture.json");
test.skip(process.env.COVIE_AD_MANAGEMENT_FIXTURE !== "1" || !existsSync(fixturePath), "Uses existing local demo login; all campaign commands are intercepted fixtures.");

for (const role of ["admin", "seller"] as const) {
  test(`${role} campaign management across mobile and desktop`, async ({ page }) => {
    test.setTimeout(90_000);
    const fixture = JSON.parse(readFileSync(fixturePath, "utf8"));
    await page.goto("/login");
    await page.getByRole("textbox", { name: "Email" }).fill(fixture[`${role}Email`]);
    await page.getByRole("textbox", { name: "Mật khẩu" }).fill(fixture[`${role}Password`]);
    await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
    await expect(page).toHaveURL(/\/$/);
    const commands: string[] = [];
    let rows: Record<string, unknown>[] = [];
    const campaignBase = role === "admin" ? "/api/v1/admin/ads" : `/api/v1/merchants/${fixture.merchantId}/ads`;
    await page.route("**/api/v1/**", async route => {
      const request = route.request(); const url = new URL(request.url());
      if (!url.pathname.startsWith(campaignBase)) {
        if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) throw new Error(`Unexpected real write blocked: ${request.method()} ${url.pathname}`);
        return route.continue();
      }
      if (url.pathname.endsWith("/stats")) return route.fulfill({ json: { impressions: 100, clicks: 5, ctr: 5, days: [{ date: new Date().toISOString().slice(0, 10), impressions: 100, clicks: 5, ctr: 5 }] } });
      if (request.method() === "GET") return route.fulfill({ json: rows });
      commands.push(`${request.method()} ${url.pathname}`);
      const previous = rows[0] ?? { version: 0 };
      if (request.method() === "DELETE") { rows = []; return route.fulfill({ status: 204 }); }
      if (url.pathname.endsWith("/review") || url.pathname.endsWith("/submit")) {
        expect(request.headers()["if-match"]).toBe(`"v${previous.version}"`);
        rows[0] = { ...previous, version: Number(previous.version) + 1, status: role === "admin" ? "APPROVED" : "PENDING", allowedActions: ["UPDATE", "DELETE", "VIEW_STATS"] };
      } else {
        const input = request.postDataJSON();
        if (request.method() === "PATCH") expect(request.headers()["if-match"]).toBe(`"v${previous.version}"`);
        rows = [{ ...input, id: "fixture-ad", merchantId: role === "seller" ? fixture.merchantId : undefined, version: request.method() === "PATCH" ? Number(previous.version) + 1 : 0, status: "DRAFT", allowedActions: ["UPDATE", "DELETE", "VIEW_STATS", ...(role === "seller" ? ["SUBMIT"] : [])] }];
      }
      await new Promise(resolve => setTimeout(resolve, 100));
      return route.fulfill({ json: rows[0] });
    });
    const path = role === "admin" ? "/admin/ads" : `/seller/${fixture.merchantId}/ads`;
    const directory = resolve("../../.local/ad-engine/browser"); mkdirSync(directory, { recursive: true });
    for (const width of [390, 1366]) {
      rows = []; await page.setViewportSize({ width, height: 900 }); await page.goto(path);
      await expect(page).toHaveURL(path);
      await expect(page.getByRole("heading", { name: "Quảng cáo", exact: true, level: 1 })).toBeVisible();
      await expect(page.getByText("Chưa có chiến dịch quảng cáo.")).toBeVisible();
      await page.getByRole("button", { name: "Tạo chiến dịch", exact: true }).click();
      await page.getByLabel("Tiêu đề", { exact: true }).fill("Chiến dịch kiểm tra");
      await page.getByLabel("Link đích", { exact: true }).fill(`/merchants/${fixture.merchantId}`);
      await expect(page.getByRole("complementary", { name: "Xem trước quảng cáo" })).toContainText("Chiến dịch kiểm tra");
      const before = commands.length;
      await page.getByRole("button", { name: "Lưu chiến dịch" }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
      await expect(page.getByRole("heading", { name: "Chiến dịch kiểm tra", exact: true })).toBeVisible();
      expect(commands.length).toBe(before + 1);
      if (role === "seller") {
        await page.getByRole("button", { name: "Gửi duyệt" }).click();
        await expect(page.getByText("Chờ duyệt", { exact: true })).toBeVisible();
      } else {
        rows[0] = { ...rows[0], status: "PENDING", allowedActions: ["UPDATE", "DELETE", "VIEW_STATS", "APPROVE", "REJECT"] };
        await page.reload();
        await page.getByRole("button", { name: "Duyệt", exact: true }).click();
        await expect(page.getByText("Đã duyệt", { exact: true })).toBeVisible();
      }
      await page.getByRole("button", { name: "Thống kê", exact: true }).click();
      await expect(page.getByText("5.00%", { exact: true }).first()).toBeVisible();
      await expect(page.getByRole("img", { name: "Biểu đồ lượt xem và lượt nhấp theo ngày" })).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      await page.screenshot({ path: resolve(directory, `${role}-ads-${width}.png`), fullPage: true });
      await page.getByRole("button", { name: "Đóng thống kê" }).click();
      await page.getByRole("button", { name: "Xóa", exact: true }).click();
      await page.getByRole("button", { name: "Xác nhận xóa" }).click();
      await expect(page.getByText("Chưa có chiến dịch quảng cáo.")).toBeVisible();
    }
  });
}
