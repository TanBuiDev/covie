import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const fromWeb = createRequire(new URL('../package.json', import.meta.url));
const { chromium, expect: baseExpect } = fromWeb('@playwright/test');
const expect = baseExpect.configure({ timeout: 20000 });
const WebSocket = fromWeb('next/dist/compiled/ws');
const fixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const target = `http://localhost:${fixture.webPort}`;
const origin = 'http://localhost:3000';
const evidence = { actualNext: true, actualApi: true, issuedJwtHttpOnlyCookies: true, syntheticFocus: true, browserOrigin: origin, requests: [] };
const redact = value => String(value).replaceAll(fixture.firstToken, '[redacted-A]').replaceAll(fixture.secondToken, '[redacted-B]');

(async () => {
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Isolated Next did not become ready; inspect next.log');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  let release;
  const delayed = new Promise(resolve => { release = resolve; });
  let held = false;
  let page;
  evidence.pageErrors = [];
  evidence.failedRequests = [];
  evidence.consoleErrors = [];
  evidence.resources = [];
  evidence.layouts = [];
  try {
    const context = await browser.newContext({ viewport: { width: 360, height: 900 }, locale: 'vi-VN', timezoneId: 'Asia/Ho_Chi_Minh' });
    const cookie = token => ({ name: 'covie_at', value: token, url: origin, httpOnly: true, sameSite: 'Lax' });
    await context.addCookies([cookie(fixture.firstToken)]);
    await context.routeWebSocket('**/*', socket => {
      const url = new URL(socket.url());
      const upstream = new WebSocket(target.replace('http:', 'ws:') + url.pathname + url.search);
      const queued = [];
      socket.onMessage(message => { if (upstream.readyState === WebSocket.OPEN) upstream.send(message); else queued.push(message); });
      upstream.on('open', () => { for (const message of queued) upstream.send(message); });
      upstream.on('message', (message, binary) => socket.send(binary ? message : message.toString()));
      upstream.on('error', error => evidence.consoleErrors.push(redact(error.message)));
      upstream.on('close', () => socket.close());
      socket.onClose(() => upstream.close());
    });
    await context.route(origin + '/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const headers = await request.allHeaders();
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      evidence.resources.push({ path: url.pathname, status: response.status(), contentType: response.headers()['content-type'] });
      if (url.pathname === '/api/v1/me/social/activity') {
        const actor = headers.cookie?.includes(fixture.firstToken) ? 'A' : headers.cookie?.includes(fixture.secondToken) ? 'B' : 'none';
        evidence.requests.push({ actor, type: url.searchParams.get('type'), hasCursor: url.searchParams.has('cursor'), status: response.status() });
        expect(url.searchParams.has('userId')).toBe(false);
        if (actor === 'A' && url.searchParams.has('cursor')) {
          const page = await response.json();
          expect(page.items).toHaveLength(1);
          expect(page.items[0].itemName).toBe('A-private-20');
          held = true;
          await delayed;
          return route.fulfill({ response }).catch(() => undefined);
        }
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    page.on('console', message => { if (message.type() === 'error') evidence.consoleErrors.push(redact(message.text())); });
    page.on('requestfailed', request => evidence.failedRequests.push({ path: new URL(request.url()).pathname, error: request.failure()?.errorText }));
    page.setDefaultTimeout(30000);
    await page.goto(origin + '/me/activity?type=LIKED');
    await expect(page).toHaveURL(origin + '/me/activity?type=LIKED');
    await expect(page.getByRole('heading', { name: 'Hoạt động của tôi', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'A-private-0', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.cookie.includes('covie_at'))).toBe(false);
    await page.getByRole('tab', { name: 'Đã lưu', exact: true }).click();
    await expect(page).toHaveURL(origin + '/me/activity?type=SAVED');
    await expect(page.getByRole('tab', { name: 'Đã lưu', exact: true })).toHaveAttribute('aria-selected', 'true');
    await expect(page.getByRole('link', { name: 'A-private-0', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Xem thêm', exact: true })).toHaveCount(0);
    await page.getByRole('tab', { name: 'Đã thích', exact: true }).click();
    await expect(page).toHaveURL(origin + '/me/activity?type=LIKED');
    await expect(page.getByRole('button', { name: 'Xem thêm', exact: true })).toBeVisible();
    for (const width of [360, 768, 1280, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await expect(page).toHaveURL(origin + '/me/activity?type=LIKED');
      await expect(page.getByRole('heading', { name: 'Hoạt động của tôi', exact: true })).toBeVisible();
      evidence.layouts.push(await page.evaluate(() => ({ width: innerWidth, documentWidth: document.documentElement.scrollWidth,
        offenders: Array.from(document.querySelectorAll('body *')).map(element => ({ tag: element.tagName, className: element.className,
          right: Math.round(element.getBoundingClientRect().right), width: Math.round(element.getBoundingClientRect().width) }))
          .filter(element => element.width > 0 && element.right > innerWidth + 1).slice(0, 12) })));
      await page.screenshot({ path: path.join(fixture.output, `activity-${width}.png`) });
    }
    await page.getByRole('button', { name: 'Xem thêm', exact: true }).click();
    await expect.poll(() => held).toBe(true);
    await context.addCookies([cookie(fixture.secondToken)]);
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(page.getByRole('link', { name: 'B-private-21', exact: true })).toBeVisible();
    release();
    await expect(page.getByRole('link', { name: /^A-private-/ })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Xem thêm', exact: true })).toHaveCount(0);
    await page.getByRole('link', { name: 'Quay lại hồ sơ', exact: true }).click();
    await expect(page).toHaveURL(origin + '/me');
    await expect(page.getByRole('heading', { name: 'Browser buyer B', exact: true })).toBeVisible();
    await page.getByRole('link', { name: /Hoạt động của tôi/ }).click();
    await expect(page).toHaveURL(origin + '/me/activity');
    await expect(page.getByRole('link', { name: 'B-private-21', exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Bình luận', exact: true }).click();
    await expect(page).toHaveURL(origin + '/me/activity?type=COMMENTED');
    await expect(page.getByText('Bạn chưa có bình luận nào đang hiển thị.', { exact: true })).toBeVisible();
    await page.getByRole('tab', { name: 'Đã thích', exact: true }).click();
    await expect(page.getByRole('link', { name: 'B-private-21', exact: true })).toBeVisible();
    await context.clearCookies();
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect(page.getByRole('link', { name: 'Đăng nhập lại', exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /^B-private-/ })).toHaveCount(0);
    evidence.browser = browser.version();
    evidence.privacyResult = 'PASS: real Next/API/issued cookies, URL tabs/profile entry, pending-A to B refresh, late-A absent, unauthenticated rows cleared';
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    for (const layout of evidence.layouts) expect(layout.documentWidth).toBeLessThanOrEqual(layout.width);
    evidence.result = 'PASS';
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
  } catch (error) {
    if (page) evidence.bodyAtFailure = await page.locator('body').innerText().catch(() => 'unavailable');
    if (page) evidence.bootstrap = await page.evaluate(() => ({ next: Boolean(window.next), serverData: window.__next_f?.length, scripts: Array.from(document.scripts).filter(script => script.src).map(script => new URL(script.src).pathname) })).catch(() => ({}));
    console.log(redact(JSON.stringify(evidence)));
    throw error;
  } finally { release(); await browser.close(); }
})().catch(error => { console.error(redact(error.stack ?? error)); process.exitCode = 1; });
