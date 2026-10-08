import { expect, test } from "@playwright/test";
test.use({channel:"chrome",trace:"off",video:"off"});
test.skip(process.env.COVIE_SESSION_EXPIRY_FIXTURE !== "1", "Opt-in local read-only browser fixtures.");

test("Buyer lists distinguish expired sessions from server failures and preserve login destinations", async ({page}) => {
  test.setTimeout(60_000);
  const writes: string[] = [];
  let status = 401;
  await page.route("**/api/v1/**", route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    // Synthetic refresh refusal; this POST never reaches the real backend.
    if (path === "/api/v1/auth/refresh") return route.fulfill({status:401,contentType:"application/json",body:JSON.stringify({error:{code:"AUTH_REQUIRED",message:"opaque"}})});
    if (path === "/api/v1/auth/csrf") return route.fulfill({status:200,contentType:"application/json",body:JSON.stringify({headerName:"X-CSRF-Token",token:"fixture"})});
    if (!["GET","HEAD","OPTIONS"].includes(request.method())) {
      writes.push(request.method());
      return route.abort();
    }
    if (path === "/api/v1/me/orders" || path === "/api/v1/orders/order-1" || path === "/api/v1/me/need-requests" || path === "/api/v1/me/need-requests/need-1") {
      return route.fulfill({status,contentType:"application/json",body:JSON.stringify({error:{code:"TEST_ERROR",message:status === 401 ? "opaque" : "token service unavailable"}})});
    }
    return route.continue();
  });
  for (const width of [1280,1366,1536,360,390]) {
    await page.setViewportSize({width,height:width < 500 ? 844 : 768});
    for (const path of ["/orders","/orders/order-1","/orders/order-1/review","/needs","/needs/need-1"]) {
      status = 401;
      await page.goto(path);
      const link = page.getByRole("link",{name:/^Đăng nhập (ngay|lại)$/});
      await expect(link).toHaveAttribute("href", `/login?returnTo=${encodeURIComponent(path)}`);
      await link.click();
      await expect(page).toHaveURL(new RegExp(`login\\?returnTo=${encodeURIComponent(path)}`));
      await expect(page.getByRole("button",{name:"Đăng nhập",exact:true})).toBeEnabled();
      status = 503;
      await page.goto(path);
      await expect(page.getByRole("button",{name:/^(Thử lại|Tải lại)$/})).toBeVisible();
      await expect(page.getByRole("link",{name:/^Đăng nhập (ngay|lại)$/})).toHaveCount(0);
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    }
  }
  expect(writes).toEqual([]);
});
