// Explicitly launched by SsrRefreshBrowserIntegrationTest against disposable PostgreSQL.
import { chromium } from "@playwright/test";
import assert from "node:assert/strict";

if (process.env.COVIE_SSR_BROWSER_TEST !== "1") throw new Error("Requires the isolated SSR backend fixture.");
let input = "";
for await (const chunk of process.stdin) input += chunk;
const fixture = JSON.parse(input);
const origin = process.env.COVIE_SSR_WEB_URL;
const backend = process.env.COVIE_SSR_API_URL;
const browser = await chromium.launch({ channel: "chrome", headless: true });
try {
  const context = await browser.newContext();
  const page = await context.newPage();
  // Production Nginx routing for hydration requests; the SSR bridge itself goes to real Next.
  await page.route("**/api/v1/**", async route => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/v1/auth/ssr-refresh") return route.continue();
    const response = await route.fetch({ url: `${backend}${url.pathname}${url.search}` });
    return route.fulfill({ response });
  });
  const now = () => Math.floor(Date.now() / 1000);
  await context.addCookies([
    { name: "covie_at", value: fixture.access, domain: new URL(origin).hostname, path: "/", httpOnly: true, sameSite: "Lax", expires: now() + 900 },
    { name: "covie_rt", value: fixture.refresh, domain: new URL(origin).hostname, path: "/api/v1/auth", httpOnly: true, sameSite: "Lax", expires: now() + 2592000 },
  ]);
  await page.goto(`${origin}/cart`);
  assert.equal(new URL(page.url()).pathname, "/cart");
  for (let iteration = 0; iteration < 2; iteration++) {
    const before = (await context.cookies()).find(cookie => cookie.name === "covie_rt").value;
    await context.addCookies([{ name: "covie_at", value: iteration === 0 ? fixture.expiredAccess : "expired",
      domain: new URL(origin).hostname, path: "/", httpOnly: true, sameSite: "Lax", expires: iteration === 0 ? now() + 900 : now() - 1 }]);
    assert.equal((await context.cookies()).some(cookie => cookie.name === "covie_at"), iteration === 0);
    await page.reload();
    assert.equal(new URL(page.url()).pathname, "/cart");
    const cookies = await context.cookies();
    const access = cookies.find(cookie => cookie.name === "covie_at");
    const refresh = cookies.find(cookie => cookie.name === "covie_rt");
    const binding = cookies.find(cookie => cookie.name === "covie_refresh_binding");
    assert.ok(access?.httpOnly && access.expires > now() + 800);
    assert.ok(refresh?.httpOnly && refresh.path === "/api/v1/auth" && refresh.expires > now() + 29 * 86400);
    assert.notEqual(refresh.value, before);
    assert.ok(binding?.httpOnly && binding.path === "/api/v1/auth" && /^[A-Za-z0-9_-]{43}$/.test(binding.value));
  }
  for (const refresh of [fixture.revoked, fixture.expired]) {
    await context.addCookies([
      { name: "covie_at", value: "expired", domain: new URL(origin).hostname, path: "/", expires: now() - 1 },
      { name: "covie_rt", value: refresh, domain: new URL(origin).hostname, path: "/api/v1/auth", httpOnly: true, sameSite: "Lax", expires: now() + 86400 },
    ]);
    await page.goto(`${origin}/me`);
    assert.equal(new URL(page.url()).pathname, "/login");
    assert.equal(new URL(page.url()).searchParams.get("returnTo"), "/me");
    assert.equal((await context.cookies()).some(cookie => cookie.name === "covie_rt"), false);
  }
  process.stdout.write(JSON.stringify({ f5Refreshes: 2, revokedAndExpired: "login", cookiePaths: "preserved" }));
} finally { await browser.close(); }
