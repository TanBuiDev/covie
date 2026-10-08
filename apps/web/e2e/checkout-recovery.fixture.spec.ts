import { expect, test } from "@playwright/test";
import { spawn, type ChildProcess } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { createServer, type Server } from "node:http";
import { resolve } from "node:path";

test.use({ channel: "chrome", video: "off", trace: "off", serviceWorkers:"block" });
test.skip(process.env.COVIE_CHECKOUT_RECOVERY_FIXTURE !== "1", "Requires an existing production build; isolated fixture API only.");

let api: Server;
let web: ChildProcess;
let catalogStatus = 503;
let sessionReadStatus = 200;
let sessionReadGate:Promise<void>|undefined;
const reads: string[] = [];
const writes: string[] = [];
const baseURL = "http://127.0.0.1:3002";
const fixtureSessions=[{id:"session-a",deviceName:"Máy A",current:true},{id:"session-b",deviceName:"Máy B",current:false}];
const fixtureCart = {id:"fixture-cart",cartType:"PRODUCT",version:1,items:[
  {id:"item-a",itemName:"Rau",quantity:2,priceSnapshot:10000},
  {id:"item-b",itemName:"Củ",quantity:1,priceSnapshot:20000},
]};
const checkout = "/checkout?source=DIRECT&itemId=fixture-item&locationId=fixture-location&locationVariantId=fixture-offer&quantity=2";

test("notifications deduplicate cursor pages and recover login after read or write401",async({page})=>{
  test.setTimeout(60_000);
  let mode="paging";
  let cursorReads=0;
  let pending:Promise<void>=Promise.resolve();
  const first={id:"notification-a",type:"ORDER_CONFIRMED",read:false,data:{}};
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();const url=new URL(request.url());
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    const unauthorized=()=>json(401,{error:{code:"AUTH_REQUIRED",message:"private diagnostic"}});
    if(url.pathname==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(url.pathname==="/api/v1/auth/refresh")return unauthorized();
    if(url.pathname.includes("mark-read")||url.pathname.includes("mark-all-read"))return unauthorized();
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){writes.push(`${request.method()} ${url.pathname}`);return route.abort();}
    if(url.pathname==="/api/v1/me/notifications"){
      if(mode==="initial401")return unauthorized();
      if(url.searchParams.has("cursor")){
        cursorReads++;await pending;
        return mode==="cursor401"?unauthorized():json(200,{items:[first,{...first,id:"notification-b"}]});
      }
      return json(200,{items:[first],nextCursor:"fixture-cursor"});
    }
    return json(200,[]);
  });
  for(const width of [1280,1366,1536,360,390]){
    await page.setViewportSize({width,height:width<500?844:768});
    mode="paging";await page.goto(`${baseURL}/me/notifications`);
    const more=page.getByRole("button",{name:"Tải thêm",exact:true});
    await expect(more).toBeVisible();
    let release!:()=>void;pending=new Promise(resolve=>{release=resolve;});const before=cursorReads;
    await more.evaluate(button=>{(button as HTMLButtonElement).click();(button as HTMLButtonElement).click();});
    await expect.poll(()=>cursorReads).toBe(before+1);release();
    await expect(page.getByText("Đơn hàng đã được xác nhận",{exact:true})).toHaveCount(2);
    pending=Promise.resolve();
    for(mode of ["initial401","cursor401","item401","all401"]){
      await page.goto(`${baseURL}/me/notifications`);
      if(mode==="cursor401")await page.getByRole("button",{name:"Tải thêm",exact:true}).click();
      if(mode==="item401")await page.getByText("Đơn hàng đã được xác nhận",{exact:true}).click();
      if(mode==="all401")await page.getByRole("button",{name:"Đánh dấu đã đọc",exact:true}).click();
      await expect(page.getByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fme%2Fnotifications");
      await expect(page.getByText("Đơn hàng đã được xác nhận",{exact:true})).toHaveCount(0);
      await expect(page.getByText("private diagnostic")).toHaveCount(0);
    }
  }
  expect(writes).toEqual([]);
});

test("buyer order list recovers reads and supports empty, expired and keyboard states", async ({ page }) => {
  test.setTimeout(60_000);
  let status = 503;
  let items: unknown[] = [];
  let orderReads = 0;
  await page.route("**/api/v1/**", async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (path === "/api/v1/auth/refresh") return route.fulfill({ status: 401, contentType: "application/json", body: JSON.stringify({ error: { code: "AUTH_REQUIRED" } }) });
    if (request.method() !== "GET") throw new Error(`Unexpected order audit write: ${request.method()} ${path}`);
    if (path === "/api/v1/me/orders") {
      orderReads++;
      return route.fulfill({ status, contentType: "application/json", body: JSON.stringify(status === 200 ? { items } : { error: { code: "ERROR", message: "private diagnostic" } }) });
    }
    return route.fulfill({ status: 200, contentType: "application/json", body: "[]" });
  });
  for (const width of [1280, 1366, 1536, 360, 390]) {
    await page.setViewportSize({ width, height: width < 500 ? 844 : 768 });
    status = 503;
    await page.goto(`${baseURL}/orders`);
    await expect(page.getByRole("heading", { name: "Không thể tải đơn hàng" })).toBeVisible();
    await expect(page.getByText("private diagnostic")).toHaveCount(0);
    const before = orderReads;
    status = 200; items = [];
    await page.getByRole("button", { name: "Thử lại", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Chưa có đơn hàng" })).toBeVisible();
    expect(orderReads).toBe(before + 1);
    await expect(page.locator(".buyer-orders-empty").getByRole("link", { name: "Khám phá", exact: true })).toHaveAttribute("href", "/");
    items = [{ id: "fixture-order", orderCode: "CV-KIEM-TRA", type: "PRODUCT", status: "COMPLETED", createdAt: "2026-10-01T00:00:00Z", updatedAt: "2026-10-07T00:00:00Z", pricing: { knownTotal: 100000, totalAmount: null, shippingFeeType: "NEGOTIATED", currency: "VND" } }];
    await page.reload();
    const order = page.locator(".buyer-order-row");
    await expect(order).toContainText("CV-KIEM-TRA");
    await expect(order).toContainText("Hoàn tất");
    await expect(order).toContainText("Số tiền đã biết");
    await expect(order).toContainText(/100.*000.*₫/);
    await expect(order).toContainText("Phí giao hàng cần thỏa thuận.");
    await expect(order).toContainText("Cập nhật");
    await order.focus();
    await expect(order).toBeFocused();
    await expect(order).toHaveAttribute("href", "/orders/fixture-order");
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    const captureDir = resolve("../../.local/buyer-ui-audit/orders-current");
    mkdirSync(captureDir, { recursive: true });
    await page.screenshot({ path: resolve(captureDir, `orders-${width}.png`) });
    status = 401;
    await page.reload();
    await expect(page.getByRole("link", { name: "Đăng nhập ngay" })).toHaveAttribute("href", "/login?returnTo=%2Forders");
    await expect(page.locator(".buyer-order-row")).toHaveCount(0);
  }
});

test("buyer orders page cursor preserves rows on retry and deduplicates pending reads", async ({ page }) => {
  test.setTimeout(60_000);
  let cursorStatus = 503;
  let cursorReads = 0;
  let pending: Promise<void> = Promise.resolve();
  const first = { id: "order-a", orderCode: "CV-A", type: "PRODUCT", status: "COMPLETED" };
  await page.route("**/api/v1/**", async route => {
    const request = route.request(); const url = new URL(request.url());
    const json = (status: number, body: unknown) => route.fulfill({ status, contentType: "application/json", body: JSON.stringify(body) });
    if (url.pathname === "/api/v1/auth/refresh") return json(401, { error: { code: "AUTH_REQUIRED" } });
    if (request.method() !== "GET") throw new Error(`Unexpected paging write: ${request.method()}`);
    if (url.pathname === "/api/v1/me/orders") {
      if (!url.searchParams.has("cursor")) return json(200, { items: [first], nextCursor: "cursor-a" });
      expect(url.searchParams.get("cursor")).toBe("cursor-a");
      expect(url.searchParams.get("limit")).toBe("20");
      cursorReads++; await pending;
      return json(cursorStatus, cursorStatus === 200 ? { items: [first, { ...first, id: "order-b", orderCode: "CV-B" }] } : { error: { code: "ERROR", message: "private diagnostic" } });
    }
    return json(200, []);
  });
  for (const width of [1280, 1366, 1536, 360, 390]) {
    await page.setViewportSize({ width, height: width < 500 ? 844 : 768 });
    cursorStatus = 503; await page.goto(`${baseURL}/orders`);
    await page.getByRole("button", { name: "Tải thêm đơn hàng" }).click();
    await expect(page.locator(".buyer-orders-content").getByRole("alert")).toContainText("Không thể tải thêm đơn hàng");
    await expect(page.getByText("CV-A", { exact: true })).toHaveCount(1);
    await expect(page.getByText("private diagnostic")).toHaveCount(0);
    cursorStatus = 200;
    let release!: () => void; pending = new Promise(done => { release = done; });
    const before = cursorReads;
    try {
      await page.getByRole("button", { name: "Tải thêm đơn hàng" }).evaluate(button => { (button as HTMLButtonElement).click(); (button as HTMLButtonElement).click(); });
      await expect.poll(() => cursorReads).toBe(before + 1);
    } finally { release(); }
    await expect(page.getByText("CV-B", { exact: true })).toBeVisible();
    await expect(page.getByText("CV-A", { exact: true })).toHaveCount(1);
    await expect(page.getByRole("button", { name: "Tải thêm đơn hàng" })).toHaveCount(0);
    pending = Promise.resolve();
    cursorStatus = 401; await page.goto(`${baseURL}/orders`);
    await page.getByRole("button", { name: "Tải thêm đơn hàng" }).click();
    await expect(page.getByRole("link", { name: "Đăng nhập ngay" })).toHaveAttribute("href", "/login?returnTo=%2Forders");
    await expect(page.locator(".buyer-order-row")).toHaveCount(0);
  }
});

test.beforeAll(async () => {
  test.setTimeout(60_000);
  api = createServer(async (request, response) => {
    const path = new URL(request.url!, "http://fixture.invalid").pathname;
    response.setHeader("Content-Type", "application/json");
    if (request.method !== "GET") {
      writes.push(`${request.method} ${path}`);
      response.writeHead(405).end(JSON.stringify({error:{code:"FIXTURE_READ_ONLY"}}));
      return;
    }
    reads.push(path);
    if (path === "/api/v1/me") {
      response.end(JSON.stringify({id:"fixture-buyer",displayName:"Buyer kiểm thử",roles:["BUYER"]}));
    } else if (path === "/api/v1/catalog/items/fixture-item") {
      response.statusCode = catalogStatus;
      response.end(JSON.stringify(catalogStatus === 200 ? {
        id:"fixture-item",name:"Món kiểm tra phục hồi",type:"PRODUCT",listingKind:"STANDARD",
        resolvedLocation:{id:"fixture-location"},variants:[{name:"Hộp lớn",offer:{locationVariantId:"fixture-offer",available:true}}],
      } : {error:{code:catalogStatus === 503 ? "FIXTURE_UNAVAILABLE" : "NOT_FOUND"}}));
    } else if (path === "/api/v1/me/security/sessions") {
      if(sessionReadGate)await sessionReadGate;
      response.statusCode=sessionReadStatus;
      response.end(JSON.stringify(sessionReadStatus===200?fixtureSessions:{error:{code:"ERROR",message:"private diagnostic"}}));
    }
    else if (path === "/api/v1/me/carts/fixture-cart") response.end(JSON.stringify(fixtureCart));
    else if (path === "/api/v1/me/carts") response.end(JSON.stringify([
      {id:"cart-a",cartType:"PRODUCT",merchantLocationId:"location",items:[],version:1},
      {id:"cart-b",cartType:"PRODUCT",merchantLocationId:"location",items:[],version:1},
    ]));
    else response.end("[]");
  });
  await new Promise<void>(resolve => api.listen(0,"127.0.0.1",resolve));
  const address = api.address();
  if (!address || typeof address === "string") throw new Error("Fixture API did not bind");
  web = spawn(process.execPath,[resolve("node_modules/next/dist/bin/next"),"start","--hostname","127.0.0.1","--port","3002"],{
    cwd:process.cwd(),windowsHide:true,stdio:"ignore",
    env:{...process.env,API_INTERNAL_BASE_URL:`http://127.0.0.1:${address.port}`,COVIE_PUBLIC_URL:baseURL},
  });
  const deadline = Date.now()+30_000;
  while (Date.now()<deadline) {
    if (web.exitCode !== null) throw new Error(`Fixture web exited: ${web.exitCode}`);
    if (await fetch(`${baseURL}/login`).then(response=>response.ok).catch(()=>false)) return;
    await new Promise(resolve=>setTimeout(resolve,200));
  }
  throw new Error("Fixture web did not become ready");
});

test("cart guards pending updates and recovers through read retry before handling expired sessions", async ({page}) => {
  test.setTimeout(60_000);
  let readStatus=503;
  let commandStatus=200;
  let commands=0;
  let pending:Promise<void>=Promise.resolve();
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();
    const path=new URL(request.url()).pathname;
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    const error={error:{code:"AUTH_REQUIRED",message:"private diagnostic"}};
    if(path==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(path==="/api/v1/auth/refresh")return json(401,error);
    if(path.includes("/fixture-cart/items/")){
      commands++;
      await pending;
      return json(commandStatus,commandStatus===200?fixtureCart:error);
    }
    if(path==="/api/v1/me/carts/fixture-cart")return json(readStatus,readStatus===200?fixtureCart:error);
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){writes.push(`${request.method()} ${path}`);return route.abort();}
    return json(200,[]);
  });
  for(const width of [1280,1366,1536,360,390]){
    readStatus=503;commandStatus=200;
    await page.setViewportSize({width,height:width<500?844:768});
    await page.goto(`${baseURL}/cart/fixture-cart`);
    const increase=page.getByRole("button",{name:"Tăng số lượng"});
    await expect(increase).toHaveCount(2);
    if(width<1024)await expect(page.getByRole("navigation",{name:"Điều hướng chính"})).toBeHidden();
    await expect(page.getByText("Có sẵn",{exact:true})).toHaveCount(0);
    await expect(page.getByText("Giá và khả dụng sẽ được kiểm tra lại trước khi đặt hàng.")).toBeVisible();
    await increase.first().focus();
    await expect(increase.first()).toBeFocused();
    await page.keyboard.press("Tab");
    await expect(page.getByRole("button",{name:"Xóa",exact:true}).first()).toBeFocused();
    const captures=resolve("../../.local/buyer-ui-audit/cart-current");
    mkdirSync(captures,{recursive:true});
    await page.screenshot({path:resolve(captures,`cart-${width}.png`),fullPage:false});
    let release!:()=>void;
    pending=new Promise(resolve=>{release=resolve;});
    const before=commands;
    await increase.evaluateAll(buttons=>{for(const button of buttons)(button as HTMLButtonElement).click();});
    await expect.poll(()=>commands).toBe(before+1);
    for(const button of await increase.all())await expect(button).toBeDisabled();
    await expect(page.getByRole("link",{name:"Tiếp tục đặt hàng"})).toHaveCount(0);
    release();
    const retry=page.getByRole("button",{name:"Thử lại tải giỏ hàng"});
    await expect(retry).toBeVisible();
    readStatus=200;
    await retry.click();
    await expect(page.getByRole("link",{name:"Tiếp tục đặt hàng"})).toBeVisible();
    expect(commands).toBe(before+1);
    pending=Promise.resolve();commandStatus=401;
    await increase.first().click();
    await expect(page.getByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fcart%2Ffixture-cart");
    await expect(increase).toHaveCount(0);
    await expect(page.getByText("private diagnostic")).toHaveCount(0);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
  }
  expect(writes).toEqual([]);
});

test.afterAll(async () => {
  if (web && web.exitCode === null) {
    const exited = new Promise<void>(resolve=>web.once("exit",()=>resolve()));
    web.kill();
    await exited;
  }
  if (api) await new Promise<void>((resolve,reject)=>api.close(error=>error ? reject(error) : resolve()));
});

test("security announces pending server reads and swaps to the list",async({page})=>{
  test.setTimeout(60_000);
  for(const width of [1280,1366,1536,360,390]){
    await page.setViewportSize({width,height:width<500?844:768});
    let release!:()=>void;
    sessionReadStatus=200;
    sessionReadGate=new Promise(resolve=>{release=resolve;});
    const navigation=page.goto(`${baseURL}/me/security/sessions`);
    try{
      await expect(page.getByRole("status").filter({hasText:"Đang tải phiên đăng nhập…"})).toBeVisible();
      await expect(page.locator("strong:visible").filter({hasText:/^Máy A$/})).toHaveCount(0);
    }finally{release();sessionReadGate=undefined;await navigation;}
    await expect(page.locator("strong:visible").filter({hasText:/^Máy A$/})).toBeVisible();
    await expect(page.getByText("Đang tải phiên đăng nhập…",{exact:true})).toHaveCount(0);
  }
});

test("security server read failure retries fresh data safely",async({page})=>{
  test.setTimeout(60_000);
  await page.route("**/api/v1/**",route=>{
    if(!["GET","HEAD","OPTIONS"].includes(route.request().method())){writes.push(route.request().method());return route.abort();}
    return route.fulfill({json:[]});
  });
  for(const width of [1280,1366,1536,360,390]){
    await page.setViewportSize({width,height:width<500?844:768});
    sessionReadStatus=503;
    await page.goto(`${baseURL}/me/security/sessions`);
    await expect(page.getByRole("heading",{name:"Không thể tải phiên đăng nhập"})).toBeVisible();
    await expect(page.getByText("private diagnostic")).toHaveCount(0);
    const count=reads.filter(path=>path==="/api/v1/me/security/sessions").length;
    sessionReadStatus=200;
    await page.getByRole("button",{name:"Thử lại",exact:true}).click();
    await expect(page.getByText("Máy A",{exact:true})).toBeVisible();
    expect(reads.filter(path=>path==="/api/v1/me/security/sessions").length).toBeGreaterThan(count);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
  }
  expect(writes).toEqual([]);
});

test("security sessions serialize revokes and retry reads without repeating commands",async({page})=>{
  test.setTimeout(60_000);
  let readStatus=503;let commandStatus=204;let commands=0;
  let pending:Promise<void>=Promise.resolve();
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();const path=new URL(request.url()).pathname;
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    const unauthorized=()=>json(401,{error:{code:"AUTH_REQUIRED",message:"private diagnostic"}});
    if(path==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(path==="/api/v1/auth/refresh")return unauthorized();
    if(path.startsWith("/api/v1/me/security/sessions/") && !["GET","HEAD"].includes(request.method())){
      commands++;await pending;
      return commandStatus===204?route.fulfill({status:204}):unauthorized();
    }
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){writes.push(`${request.method()} ${path}`);return route.abort();}
    if(path==="/api/v1/me/security/sessions")return json(readStatus,readStatus===200?fixtureSessions:{error:{code:"ERROR"}});
    return json(200,[]);
  });
  for(const width of [1280,1366,1536,360,390]){
    readStatus=503;commandStatus=204;
    await page.setViewportSize({width,height:width<500?844:768});
    await page.goto(`${baseURL}/me/security/sessions`);
    const revoke=page.getByRole("button",{name:/^Thu hồi Máy/});
    await expect(revoke).toHaveCount(2);
    let release!:()=>void;pending=new Promise(resolve=>{release=resolve;});const before=commands;
    await revoke.evaluateAll(buttons=>{for(const button of buttons)(button as HTMLButtonElement).click();});
    await expect.poll(()=>commands).toBe(before+1);
    release();
    const retry=page.getByRole("button",{name:"Thử lại tải phiên"});
    await expect(retry).toBeVisible();
    for(const button of await revoke.all())await expect(button).toBeDisabled();
    readStatus=200;await retry.click();
    await expect(retry).toHaveCount(0);expect(commands).toBe(before+1);
    pending=Promise.resolve();commandStatus=401;
    await revoke.first().click();
    await expect(page.getByRole("link",{name:"Đăng nhập lại"})).toHaveAttribute("href","/login?returnTo=%2Fme%2Fsecurity%2Fsessions");
    await expect(revoke).toHaveCount(0);
    await expect(page.getByText("private diagnostic")).toHaveCount(0);
  }
  expect(writes).toEqual([]);
});

test("locks cart selection and deduplicates commit buttons, then retries with the same key",async({page})=>{
  test.setTimeout(60_000);
  const commits:Array<{token:unknown;key:string|undefined}>=[];
  let release!:()=>void;
  let pending:Promise<void>;
  let commitStatus = 503;
  await page.route("**/api/v1/**",async route=>{
    const request=route.request();
    const path=new URL(request.url()).pathname;
    const json=(status:number,body:unknown)=>route.fulfill({status,contentType:"application/json",body:JSON.stringify(body)});
    if(path==="/api/v1/auth/csrf")return json(200,{headerName:"X-CSRF-Token",token:"fixture"});
    if(path==="/api/v1/checkouts/preview")return json(200,{checkoutToken:"fixture-token",subtotal:100,shippingFee:0,total:100,fulfillmentMethod:"PICKUP"});
    if(path==="/api/v1/checkouts/commit"){
      commits.push({token:request.postDataJSON().checkoutToken,key:request.headers()["idempotency-key"]});
      await pending;
      return json(commitStatus,{error:{code:commitStatus === 409 ? "CHECKOUT_CHANGED" : "ERROR",message:"fixture failure"}});
    }
    if(!["GET","HEAD","OPTIONS"].includes(request.method())){writes.push(`${request.method()} ${path}`);return route.abort();}
    return json(200,[]);
  });
  for(const width of [1280,1366,1536,360,390]){
    commitStatus = 503;
    await page.setViewportSize({width,height:width<500?844:768});
    await page.goto(`${baseURL}/checkout?cartId=cart-a`);
    await page.getByRole("button",{name:"Xem trước",exact:true}).click();
    const buttons=page.getByRole("button",{name:"Đặt hàng",exact:true});
    await expect(buttons.first()).toBeAttached();
    pending=new Promise(resolve=>{release=resolve;});
    const previous=commits.length;
    await buttons.evaluateAll(nodes=>{for(const node of nodes)(node as HTMLButtonElement).click();});
    await expect(page.getByRole("combobox",{name:"Giỏ hàng"})).toBeDisabled();
    await expect.poll(()=>commits.length).toBe(previous+1);
    release();
    await expect(page.getByText("Không thể xác nhận đơn hàng. Vui lòng thử lại.",{exact:true})).toBeVisible();
    await expect(page.getByRole("combobox",{name:"Giỏ hàng"})).toBeEnabled();
    const first=commits[previous]!;
    expect(first.token).toBe("fixture-token");expect(first.key).toBeTruthy();
    pending=Promise.resolve();
    await buttons.evaluateAll(nodes=>{for(const node of nodes)(node as HTMLButtonElement).click();});
    await expect.poll(()=>commits.length).toBe(previous+2);
    expect(commits[previous+1]).toEqual(first);
    await expect(page.getByRole("combobox",{name:"Giỏ hàng"})).toBeEnabled();
    commitStatus = 409;
    await buttons.evaluateAll(nodes=>{for(const node of nodes)(node as HTMLButtonElement).click();});
    await expect(page.getByRole("alertdialog")).toContainText("Giá hoặc thông tin đã thay đổi");
    await expect(page.getByRole("button",{name:"Đặt hàng",exact:true})).toHaveCount(0);
    await page.getByRole("button",{name:"Kiểm tra giá/thông tin mới"}).click();
    await expect(page.getByRole("alertdialog")).toHaveCount(0);
  }
  expect(writes).toEqual([]);
});

test("re-fetches checkout after a server read failure and keeps missing-source errors separate", async ({page}) => {
  test.setTimeout(90_000);
  const directory = resolve("../../.local/buyer-ui-audit/checkout-recovery-fixture");
  mkdirSync(directory,{recursive:true});
  await page.route("**/api/v1/**", route => {
    if (!["GET","HEAD","OPTIONS"].includes(route.request().method())) {
      writes.push(`${route.request().method()} browser API`);
      return route.abort("blockedbyclient");
    }
    return route.fulfill({json:[]});
  });
  for (const viewport of [{width:1280,height:720},{width:1366,height:768},{width:1536,height:864},{width:360,height:800},{width:390,height:844}]) {
    await page.setViewportSize(viewport);
    catalogStatus = 503;
    await page.goto(`${baseURL}${checkout}`);
    await expect(page.locator("section[role=alert]")).toContainText("Không thể tải thông tin đặt hàng");
    await expect(page.getByText(/đã hết hiệu lực/)).toHaveCount(0);
    const retryBounds = await page.getByRole("button",{name:"Thử lại",exact:true}).boundingBox();
    expect(retryBounds!.width).toBeLessThanOrEqual(220);
    expect(retryBounds!.height).toBeGreaterThanOrEqual(44);
    await page.screenshot({path:resolve(directory,`error-${viewport.width}.png`),fullPage:false});
    const previousReads = reads.filter(path=>path.includes("/catalog/items/")).length;
    catalogStatus = 200;
    await page.getByRole("button",{name:"Thử lại",exact:true}).click();
    await expect(page.getByText("Món kiểm tra phục hồi",{exact:true})).toBeVisible();
    await expect(page.getByRole("button",{name:"Xem trước",exact:true})).toBeVisible();
    expect(reads.filter(path=>path.includes("/catalog/items/")).length).toBeGreaterThan(previousReads);
    expect(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth)).toBe(false);
    await page.screenshot({path:resolve(directory,`recovered-${viewport.width}.png`),fullPage:false});
    catalogStatus = 404;
    await page.reload();
    await expect(page.locator("section[role=alert]")).toContainText("Không thể tiếp tục đặt hàng");
    await expect(page.getByRole("button",{name:"Thử lại",exact:true})).toHaveCount(0);
  }
  writeFileSync(resolve(directory,"observations.json"),JSON.stringify({evidence:"Isolated production web + synthetic read-only API; no real backend or login",reads,writes},null,2));
  expect(writes).toEqual([]);
});
