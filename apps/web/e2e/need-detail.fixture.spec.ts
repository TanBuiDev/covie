import { expect, test } from "@playwright/test";

test.use({ channel: "chrome", trace: "off", video: "off", serviceWorkers: "block" });
test.skip(process.env.COVIE_NEED_DETAIL_FIXTURE !== "1", "Opt-in synthetic cancellation fixture; no real writes.");

test("Need responses require login after initial, address or cursor reads expire", async ({ page }) => {
  test.setTimeout(60_000);
  let mode = "responses";
  const writes: string[] = [];
  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname;
    const json = (status: number, body: unknown) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    const unauthorized = () => json(401, { error: { code: "AUTH_REQUIRED", message: "private diagnostic" } });
    if (path === "/api/v1/auth/refresh") return unauthorized();
    if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) { writes.push(`${request.method()} ${path}`); return route.abort(); }
    if (path.endsWith("/fixture-need")) return json(200, { id: "fixture-need", title: "Nhu cầu mẫu", type: "PRODUCT", status: "ACTIVE", allowedActions: [], requestedQuantity: 1, radiusMeters: 1000, responseCount: 1, expiresAt: "2099-01-01" });
    if (path.endsWith("/addresses")) return mode === "addresses" ? unauthorized() : json(200, []);
    if (path.endsWith("/responses")) {
      if (mode === "responses" || url.searchParams.has("cursor")) return unauthorized();
      return json(200, { items: [{ id: "response", merchantName: "Người bán mẫu", status: "ACTIVE", items: [{ id: "item", catalogItemName: "Rau", available: true, actionable: true }] }], hasMore: true, nextCursor: "fixture-cursor" });
    }
    return json(200, []);
  });
  for (const width of [1280, 1366, 1536, 360, 390]) {
    await page.setViewportSize({ width, height: width < 500 ? 844 : 768 });
    for (mode of ["responses", "addresses", "cursor"]) {
      await page.goto("/needs/fixture-need");
      if (mode === "cursor") await page.getByRole("button", { name: "Tải thêm phản hồi", exact: true }).click();
      await expect(page.getByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fneeds%2Ffixture-need");
      await expect(page.getByRole("link", { name: "Xem lại và đặt hàng" })).toHaveCount(0);
      await expect(page.getByText("Chưa có phản hồi phù hợp")).toHaveCount(0);
      await expect(page.getByText("private diagnostic")).toHaveCount(0);
    }
  }
  expect(writes).toEqual([]);
});

test("Need cancellation locks pending actions, retries safely and handles expired sessions", async ({ page }) => {
  test.setTimeout(60_000);
  let status = 503;
  let pending: Promise<void> = Promise.resolve();
  let commands = 0;
  const unexpectedWrites: string[] = [];
  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const json = (code: number, body: unknown) => route.fulfill({ status: code, contentType: "application/json", body: JSON.stringify(body) });
    if (path === "/api/v1/auth/csrf") return json(200, { headerName: "X-CSRF-Token", token: "fixture" });
    if (path === "/api/v1/auth/refresh") return json(401, { error: { code: "AUTH_REQUIRED" } });
    if (path === "/api/v1/me/need-requests/fixture-need/cancel") {
      commands++;
      await pending;
      return json(status, { error: { code: status === 401 ? "AUTH_REQUIRED" : "ERROR", message: "private server diagnostic" } });
    }
    if (!["GET", "HEAD", "OPTIONS"].includes(request.method())) {
      unexpectedWrites.push(`${request.method()} ${path}`);
      return route.abort();
    }
    if (path === "/api/v1/me/need-requests/fixture-need") return json(200, {
      id: "fixture-need", title: "Nhu cầu kiểm thử", type: "PRODUCT", status: "ACTIVE", allowedActions: ["CANCEL"],
      requestedQuantity: 2, radiusMeters: 3000, responseCount: 0, expiresAt: "2099-01-01T12:00:00Z",
    });
    if (path.endsWith("/responses")) return json(200, { items: [], hasMore: false });
    return json(200, []);
  });
  for (const width of [1280, 1366, 1536, 360, 390]) {
    status = 503;
    await page.setViewportSize({ width, height: width < 500 ? 844 : 768 });
    await page.goto("/needs/fixture-need");
    await page.getByRole("button", { name: "Hủy yêu cầu", exact: true }).click();
    const confirm = page.getByRole("button", { name: "Xác nhận hủy", exact: true });
    let release!: () => void;
    pending = new Promise(resolve => { release = resolve; });
    const before = commands;
    await confirm.evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
    await expect.poll(() => commands).toBe(before + 1);
    await expect(page.getByRole("button", { name: "Đang hủy…", exact: true })).toBeDisabled();
    await expect(page.getByRole("button", { name: "Giữ yêu cầu" })).toBeDisabled();
    release();
    await expect(page.locator(".need-detail").getByRole("alert")).toContainText("Không thể hủy yêu cầu. Vui lòng thử lại.");
    await expect(confirm).toBeEnabled();
    await expect(page.getByText("private server diagnostic")).toHaveCount(0);
    pending = Promise.resolve();
    status = 401;
    await confirm.click();
    await expect(page.getByRole("link", { name: "Đăng nhập lại" })).toHaveAttribute("href", "/login?returnTo=%2Fneeds%2Ffixture-need");
    await expect(confirm).toHaveCount(0);
    await expect(page.getByText("Nhu cầu kiểm thử", { exact: true })).toHaveCount(0);
    expect(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)).toBe(false);
  }
  expect(unexpectedWrites).toEqual([]);
});
