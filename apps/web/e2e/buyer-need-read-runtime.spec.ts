import { expect, test } from "@playwright/test";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { createHash } from "node:crypto";

test.use({ channel: "chrome", video: "off", trace: "off" });
test.skip(process.env.COVIE_BUYER_READ_AUDIT !== "1", "Requires approved demo login and read-only inspection.");

test("Need responses load and cursor pages match the real Buyer API", async ({ page, playwright }) => {
  test.setTimeout(90_000);
  const directory = resolve("../../.local/buyer-ui-audit/need-runtime");
  mkdirSync(directory, { recursive: true });
  const seed = readFileSync(resolve("../../.local/demo-seed-regeneration/infra/scripts/generate-demo-seed.py"), "utf8");
  const password = seed.match(/^PASSWORD = "([^"]+)"/m)?.[1];
  expect(Boolean(password)).toBe(true);
  const writes: string[] = [];
  let allowLogin = true;
  await page.route("**/api/v1/**", route => {
    const request = route.request();
    if (["GET", "HEAD", "OPTIONS"].includes(request.method())) return route.continue();
    if (allowLogin && request.method() === "POST" && new URL(request.url()).pathname === "/api/v1/auth/login") {
      allowLogin = false;
      return route.continue();
    }
    writes.push(`${request.method()} ${new URL(request.url()).pathname}`);
    return route.abort("blockedbyclient");
  });
  await page.goto("/login");
  await expect(page.getByRole("button", { name: "Đăng nhập", exact: true })).toBeEnabled();
  await page.getByLabel("Email", { exact: true }).fill("buyer-01@demo.covie.invalid");
  await page.getByLabel("Mật khẩu", { exact: true }).fill(password!);
  await page.getByRole("button", { name: "Đăng nhập", exact: true }).click();
  await expect(page).toHaveURL(/\/$/);
  const needs = await page.request.get("/api/v1/me/need-requests?status=ACTIVE");
  expect(needs.status()).toBe(200);
  const need = (await needs.json()).items[0];
  expect(need).toBeTruthy();
  const namespace = seed.match(/^NAMESPACE = uuid.UUID\("([^"]+)"\)/m)?.[1];
  expect(Boolean(namespace)).toBe(true);
  const seedId = (name: string) => {
    const bytes = createHash("sha1").update(Buffer.from(namespace!.replaceAll("-", ""), "hex"))
      .update(name).digest().subarray(0, 16);
    bytes[6] = (bytes[6]! & 0x0f) | 0x50;
    bytes[8] = (bytes[8]! & 0x3f) | 0x80;
    const hex = bytes.toString("hex");
    return `${hex.slice(0,8)}-${hex.slice(8,12)}-${hex.slice(12,16)}-${hex.slice(16,20)}-${hex.slice(20)}`;
  };
  expect(seedId("need:1")).toBe(need.id);
  const otherBuyer = await page.request.get(`/api/v1/me/need-requests/${seedId("need:2")}/responses`);
  expect(otherBuyer.status()).toBe(404);
  const path = `/api/v1/me/need-requests/${need.id}/responses`;
  const response = await page.request.get(path);
  expect(response.status()).toBe(200);
  const responses = await response.json();
  expect(responses.items.length).toBe(2);
  const firstResponse = await page.request.get(`${path}?limit=1`);
  expect(firstResponse.status()).toBe(200);
  const first = await firstResponse.json();
  expect(first.items).toHaveLength(1);
  expect(first.hasMore).toBe(true);
  expect(first.nextCursor).toBeTruthy();
  const secondResponse = await page.request.get(`${path}?limit=1&cursor=${encodeURIComponent(first.nextCursor)}`);
  expect(secondResponse.status()).toBe(200);
  const second = await secondResponse.json();
  expect(second.items).toHaveLength(1);
  expect(second.hasMore).toBe(false);
  expect([first.items[0].id, second.items[0].id]).toEqual(responses.items.map((item: { id: string }) => item.id));
  const invalid = await page.request.get(`${path}?cursor=invalid`);
  // NeedTimestampCursorCodec reports malformed cursors as VALIDATION_FAILED/422.
  expect(invalid.status()).toBe(422);
  expect((await invalid.json()).error.code).toBe("VALIDATION_FAILED");
  const guest = await playwright.request.newContext({ baseURL: "http://localhost:3000" });
  let guestStatus: number;
  try {
    guestStatus = (await guest.get(path)).status();
    expect(guestStatus).toBe(401);
  } finally { await guest.dispose(); }
  // Request one response per page from the real API without adding fixtures to the database.
  await page.route("**/api/v1/me/need-requests/*/responses*", route => {
    if (route.request().method() !== "GET") return route.fallback();
    const url = new URL(route.request().url());
    url.searchParams.set("limit", "1");
    return route.continue({ url: url.toString() });
  });
  const captures: object[] = [];
  const previewFixtures: object[] = [];
  let expectedNeedQuantity = 1;
  let expectedDirectQuantity = 2;
  // Browser-only preview fixture: the real recovered database receives no checkout POST.
  await page.route("**/api/v1/checkouts/preview", async route => {
    const input = route.request().postDataJSON();
    if (input.source.type === "DIRECT") {
      expect(input.source).toEqual({type:"DIRECT"});
      expect(input.items[0].quantity).toBe(expectedDirectQuantity);
    } else {
      expect(input.source).toEqual({type:"NEED_REQUEST",needRequestId:need.id,needResponseItemId:responses.items[0].items[0].id});
      expect(input.items).toEqual([{locationVariantId:responses.items[0].items[0].locationVariantId,quantity:expectedNeedQuantity}]);
    }
    previewFixtures.push(input);
    const direct = input.source.type === "DIRECT";
    const subtotal = 65000*input.items[0].quantity;
    await route.fulfill({json:{checkoutToken:"browser-only-preview-token",subtotal,shippingFee:direct ? 0 : 12000,total:subtotal+(direct ? 0 : 12000),
      currency:"VND",fulfillmentMethod:input.fulfillmentMethod}});
  });
  for (const viewport of [{ width: 1280, height: 720 }, { width: 1366, height: 768 },
    { width: 1536, height: 864 }, { width: 360, height: 800 }, { width: 390, height: 844 }]) {
    await page.setViewportSize(viewport);
    expectedNeedQuantity = 1;
    expectedDirectQuantity = 2;
    await page.goto(`/needs/${need.id}`);
    await expect(page.getByRole("heading", { name: "Phản hồi Cần mua", exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: first.items[0].merchantName, exact: true })).toBeVisible();
    await expect(page.getByRole("heading", { name: second.items[0].merchantName, exact: true })).toHaveCount(0);
    await page.getByRole("button", { name: "Tải thêm phản hồi", exact: true }).click();
    for (const item of responses.items) await expect(page.getByRole("heading", { name: item.merchantName, exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Tải thêm phản hồi", exact: true })).toHaveCount(0);
    await expect(page.getByText("Không thể tải phản hồi. Vui lòng thử lại.", { exact: true })).toHaveCount(0);
    await page.waitForLoadState("networkidle");
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth > innerWidth);
    captures.push({ viewport, overflow, headings: await page.getByRole("heading").allTextContents() });
    expect(overflow).toBe(false);
    await page.evaluate(() => window.scrollTo(0, 0));
    await page.screenshot({ path: resolve(directory, `need-${viewport.width}.png`), fullPage: true });
    const selectedItem = responses.items[0].items[0];
    await expect(page.getByRole("link",{name:selectedItem.catalogItemName,exact:true})).not.toHaveCount(0);
    await page.getByRole("link",{name:"Xem lại và đặt hàng",exact:true}).first().click();
    await expect(page).toHaveURL(new RegExp(`/checkout\\?source=NEED_REQUEST&needRequestId=${need.id}`));
    await expect(page.getByText(selectedItem.catalogItemName,{exact:true})).toBeVisible();
    await expect(page.getByText(/^Phân loại: /)).toBeVisible();
    await expect(page.getByRole("button",{name:"Xem trước",exact:true})).toBeVisible();
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toHaveCount(0);
    await page.waitForLoadState("networkidle");
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
    await page.screenshot({path:resolve(directory,`checkout-before-preview-${viewport.width}.png`),fullPage:false});
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    await expect(page.getByRole("heading",{name:"Tóm tắt thanh toán",exact:true})).toBeVisible();
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toBeVisible();
    await expect(page.getByText("77.000 ₫",{exact:true}).first()).toBeVisible();
    await page.evaluate(()=>window.scrollTo(0,0));
    await page.screenshot({path:resolve(directory,`checkout-fixture-preview-${viewport.width}.png`),fullPage:false});
    expectedNeedQuantity = 2;
    await page.getByRole("button",{name:"Tăng số lượng",exact:true}).click();
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toHaveCount(0);
    await expect(page.getByLabel("Số lượng",{exact:true})).toHaveText("2");
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    await expect(page.getByRole("heading",{name:"Tóm tắt thanh toán",exact:true})).toBeVisible();
    await page.getByRole("button",{name:"Thay đổi",exact:true}).click();
    const addressSelect = page.getByLabel("Địa chỉ",{exact:true});
    const previousAddress = await addressSelect.inputValue();
    await addressSelect.selectOption("");
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toHaveCount(0);
    await expect(page.getByRole("button",{name:"Xem trước",exact:true})).toBeDisabled();
    await addressSelect.selectOption(previousAddress);
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    await expect(page.getByRole("heading",{name:"Tóm tắt thanh toán",exact:true})).toBeVisible();
    await page.goto(`/items/${selectedItem.catalogItemId}?locationId=${responses.items[0].merchantLocationId}`);
    await expect(page.getByRole("button",{name:"Mua ngay",exact:true})).toBeEnabled();
    await page.getByRole("button",{name:"Tăng số lượng",exact:true}).click();
    await page.getByRole("button",{name:"Mua ngay",exact:true}).click();
    await expect(page).toHaveURL(/\/checkout\?source=DIRECT/);
    await expect(page).toHaveURL(/quantity=2/);
    await expect(page.getByText(selectedItem.catalogItemName,{exact:true})).toBeVisible();
    await expect(page.getByText(/^Phân loại: /)).toBeVisible();
    await expect(page.getByText(/Số lượng: 2/)).toBeVisible();
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toBeVisible();
    expectedDirectQuantity = 3;
    await page.getByRole("button",{name:"Tăng số lượng",exact:true}).focus();
    await page.getByRole("button",{name:"Tăng số lượng",exact:true}).press("Enter");
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toHaveCount(0);
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toBeVisible();
    const plus = await page.getByRole("button",{name:"Tăng số lượng",exact:true}).boundingBox();
    expect(plus!.width).toBeGreaterThanOrEqual(44);
    expect(plus!.height).toBeGreaterThanOrEqual(44);
    const icon = await page.getByRole("button",{name:"Tăng số lượng",exact:true}).locator("img").boundingBox();
    expect(icon!.width).toBe(20);
    expect(icon!.height).toBe(20);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
    await page.screenshot({path:resolve(directory,`checkout-direct-fixture-${viewport.width}.png`),fullPage:false});
  }
  writeFileSync(resolve(directory, "observations.json"), JSON.stringify({ needId: need.id, responses,
    cursorPages: [first, second], invalidStatus: invalid.status(), otherBuyerStatus: otherBuyer.status(), guestStatus, captures, writes,
    previewEvidence:"Browser-intercepted fixture only; no real checkout preview or commit POST",previewFixtures }, null, 2));
  expect(previewFixtures).toHaveLength(25);
  expect(writes).toEqual([]);
});
