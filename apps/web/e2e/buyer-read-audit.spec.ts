import { expect, test } from "@playwright/test";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";

test.use({ channel: "chrome", video: "off", trace: "off" });
test.skip(process.env.COVIE_BUYER_READ_AUDIT !== "1", "Requires explicit demo-login/read-only authorization.");

test("authenticated Buyer read-only viewport audit", async ({ page }) => {
  test.setTimeout(180_000);
  const evidence = resolve("../../.local/buyer-ui-audit/authenticated");
  mkdirSync(evidence, { recursive: true });
  const seed = readFileSync(resolve("../../.local/demo-seed-regeneration/infra/scripts/generate-demo-seed.py"), "utf8");
  const password = seed.match(/^PASSWORD = "([^"]+)"/m)?.[1];
  expect(Boolean(password)).toBe(true);
  const blockedWrites: string[] = [];
  const errors: string[] = [];
  const results: object[] = [];
  page.on("pageerror", error => errors.push(error.message));
  let loginAllowed = true;
  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (["GET", "HEAD", "OPTIONS"].includes(request.method())) return route.continue();
    if (loginAllowed && request.method() === "POST" && path === "/api/v1/auth/login") {
      loginAllowed = false;
      return route.continue();
    }
    blockedWrites.push(`${request.method()} ${path}`);
    return route.abort("blockedbyclient");
  });
  await page.goto("/login");
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeEnabled();
  await page.getByLabel("Email", { exact: true }).fill("buyer-01@demo.covie.invalid");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password!);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);

  const ordersResponse = await page.request.get("/api/v1/me/orders?limit=50");
  expect(ordersResponse.status()).toBe(200);
  const ordersPage = await ordersResponse.json() as { items: Array<{ id: string }>; nextCursor?: string };
  expect(ordersPage.nextCursor).toBeFalsy();
  const orderIds: string[] = [];
  let orderCursor: string | undefined;
  for (let index = 0; index <= ordersPage.items.length; index++) {
    const query = new URLSearchParams({ limit: "1", ...(orderCursor ? { cursor: orderCursor } : {}) });
    const response = await page.request.get(`/api/v1/me/orders?${query}`);
    expect(response.status()).toBe(200);
    const body = await response.json() as { items: Array<{ id: string }>; nextCursor?: string };
    expect(body.items.length).toBeLessThanOrEqual(1);
    orderIds.push(...body.items.map(item => item.id));
    orderCursor = body.nextCursor;
    if (!orderCursor) break;
  }
  expect(orderIds).toEqual(ordersPage.items.map(item => item.id));
  expect(new Set(orderIds).size).toBe(orderIds.length);
  expect((await page.request.get("/api/v1/me/orders?cursor=invalid")).status()).toBe(400);
  results.push({ route: "/orders", scope: "real GET cursor traversal", count: orderIds.length, ids: orderIds });

  const notificationResponse = await page.request.get("/api/v1/me/notifications?limit=50");
  expect(notificationResponse.status()).toBe(200);
  const notificationPage = await notificationResponse.json() as {items:Array<{id:string;read:boolean;data:{deepLink:string;orderId:string}}>};
  expect(notificationPage.items.length).toBeGreaterThan(0);
  const notificationIds:string[]=[];
  let notificationCursor:string|undefined;
  for(let index=0;index<notificationPage.items.length+1;index++){
    const query=new URLSearchParams({limit:"1",...(notificationCursor?{cursor:notificationCursor}:{})});
    const response=await page.request.get(`/api/v1/me/notifications?${query}`);
    expect(response.status()).toBe(200);
    const body=await response.json() as {items:Array<{id:string}>;nextCursor?:string};
    expect(body.items.length).toBeLessThanOrEqual(1);
    notificationIds.push(...body.items.map(item=>item.id));
    notificationCursor=body.nextCursor;
    if(!notificationCursor)break;
  }
  expect(notificationIds).toEqual(notificationPage.items.map(item=>item.id));
  expect(new Set(notificationIds).size).toBe(notificationIds.length);
  expect((await page.request.get("/api/v1/me/notifications?cursor=invalid")).status()).toBe(422);
  for(const notification of notificationPage.items){
    expect(notification.data.deepLink).toBe(`/orders/${notification.data.orderId}`);
    expect((await page.request.get(`/api/v1/orders/${notification.data.orderId}`)).status()).toBe(200);
  }
  results.push({route:"/me/notifications",scope:"real GET cursor/deep-link ownership",count:notificationIds.length,unread:notificationPage.items.filter(item=>!item.read).length,ids:notificationIds});

  const viewports = [{ width: 1280, height: 720 }, { width: 1366, height: 768 },
    { width: 1536, height: 864 }, { width: 360, height: 800 }, { width: 390, height: 844 }];
  const readNotificationIndex=notificationPage.items.findIndex(item=>item.read);
  expect(readNotificationIndex).toBeGreaterThanOrEqual(0);
  const readNotification=notificationPage.items[readNotificationIndex]!;
  const readOrderResponse=await page.request.get(`/api/v1/orders/${readNotification.data.orderId}`);
  expect(readOrderResponse.status()).toBe(200);
  const readOrder=await readOrderResponse.json() as {orderCode:string};
  expect(readOrder.orderCode).toBeTruthy();
  for(const viewport of viewports){
    await page.setViewportSize(viewport);
    await page.goto("/me/notifications");
    const notificationButtons=page.locator("ol").getByRole("button");
    await expect(notificationButtons).toHaveCount(notificationPage.items.length);
    await notificationButtons.nth(readNotificationIndex).click();
    await expect(page).toHaveURL(new RegExp(`/orders/${readNotification.data.orderId}$`));
    await expect(page.getByRole("heading",{name:readOrder.orderCode,exact:true}).first()).toBeVisible();
    await expect(page.getByText("Không thể tải đơn hàng",{exact:true})).toHaveCount(0);
    await page.waitForLoadState("networkidle");
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
    results.push({route:readNotification.data.deepLink,scope:"already-read notification real navigation",viewport});
  }
  const afterNotificationResponse=await page.request.get("/api/v1/me/notifications?limit=50");
  expect(afterNotificationResponse.status()).toBe(200);
  expect((await afterNotificationResponse.json()).items).toEqual(notificationPage.items);
  for (const viewport of viewports) {
    await page.setViewportSize(viewport);
    await page.goto("/me/addresses/new");
    await expect(page.getByRole("combobox", { name: "Nguồn vị trí", exact: true })).toBeVisible();
    await page.waitForLoadState("networkidle");
    const gpsButton = page.getByRole("button", { name: "Lấy vị trí hiện tại", exact: true });
    await expect(gpsButton).toBeVisible();
    expect((await gpsButton.boundingBox())!.height).toBeGreaterThanOrEqual(44);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    results.push({ route: "/me/addresses/new", viewport, overflow, alerts: await page.getByRole("alert").allTextContents() });
    await page.screenshot({ path: resolve(evidence, `address-new-${viewport.width}.png`), fullPage: true });
    expect(overflow).toBe(false);
    await page.getByRole("combobox", { name: "Nguồn vị trí", exact: true }).selectOption("SAVED");
    await expect(page.getByRole("combobox", { name: "Địa chỉ lấy điểm", exact: true }).locator("option")).not.toHaveCount(1);
    const options = await page.getByRole("combobox", { name: "Địa chỉ lấy điểm", exact: true }).locator("option").evaluateAll(elements =>
      elements.map(element => ({ value: (element as HTMLOptionElement).value, text: element.textContent })));
    await page.getByRole("combobox", { name: "Địa chỉ lấy điểm", exact: true }).selectOption(options.find(option => option.value)!.value);
    await expect(page.getByText(/^Điểm giao hàng:/)).toBeVisible();
    await page.getByLabel("Xác nhận điểm giao hàng cho địa chỉ này").check();
    await page.getByRole("textbox", { name: /^Địa chỉ \*/ }).fill("Địa chỉ chỉ dùng kiểm tra giao diện");
    await expect(page.getByLabel("Xác nhận điểm giao hàng cho địa chỉ này")).not.toBeChecked();
    await page.screenshot({ path: resolve(evidence, `address-saved-${viewport.width}.png`), fullPage: true });
  }
  for (const viewport of [viewports[1]!, viewports[4]!]) {
    await page.setViewportSize(viewport);
    for (const route of ["/me/addresses", "/me", "/orders", "/needs", "/me/notifications", "/me/reputation", "/me/security/sessions"]) {
      await page.goto(route);
      await page.waitForLoadState("networkidle");
      expect(new URL(page.url()).pathname).toBe(route);
      results.push({ route, viewport, headings: await page.getByRole("heading").allTextContents(),
        overflow: await page.evaluate(() => document.documentElement.scrollWidth > innerWidth),
        alerts: await page.getByRole("alert").allTextContents() });
      await page.screenshot({ path: resolve(evidence, `${route.replaceAll("/", "-")}-${viewport.width}.png`), fullPage: true });
    }
  }
  writeFileSync(resolve(evidence, "observations.json"), JSON.stringify({ results, errors, blockedWrites }, null, 2));
  expect(blockedWrites).toEqual([]);
  expect(errors).toEqual([]);
});
