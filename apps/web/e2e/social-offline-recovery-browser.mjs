import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const fromWeb = createRequire(new URL('../package.json', import.meta.url));
const { chromium, expect: baseExpect } = fromWeb('@playwright/test');
const expect = baseExpect.configure({ timeout: 20000 });
const WebSocket = fromWeb('next/dist/compiled/ws');
const fixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const origin = 'http://localhost:3000';
const target = `http://localhost:${fixture.webPort}`;
const evidence = { failedRequests: [], previewReads: [], writes: [], commentReads: 0, pageErrors: [] };
let phase = 'online';
let browser;
let closing = false;
try {
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  expect(ready).toBe(true);
  browser = await chromium.launch({ channel: 'chrome', headless: true });
  evidence.browser = browser.version();
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
  await context.grantPermissions(['local-network-access'], { origin });
  await context.addInitScript(() => {
    if (location.origin !== 'http://localhost:3000') return;
    localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Offline fixture' } }));
    window.socialNetworkEvents = [];
    for (const type of ['online', 'offline']) addEventListener(type, event => window.socialNetworkEvents.push({ type: event.type, trusted: event.isTrusted }));
  });
  await context.routeWebSocket('**/*', socket => {
    const url = new URL(socket.url());
    const upstream = new WebSocket(target.replace('http:', 'ws:') + url.pathname + url.search);
    const queued = [];
    socket.onMessage(message => { if (upstream.readyState === WebSocket.OPEN) upstream.send(message); else queued.push(message); });
    upstream.on('open', () => { for (const message of queued) upstream.send(message); });
    upstream.on('message', (message, binary) => socket.send(binary ? message : message.toString()));
    upstream.on('error', () => undefined);
    upstream.on('close', () => socket.close());
    socket.onClose(() => upstream.close());
  });
  await context.route(origin + '/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const headers = await request.allHeaders();
    expect(headers.authorization).toBeUndefined();
    expect(headers.cookie ?? '').not.toMatch(/(?:^|;\s*)covie_(?:at|rt)=/);
    if (url.pathname.startsWith('/api/v1/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())) evidence.writes.push(url.pathname);
    if (/\/api\/v1\/catalog\/items\/[^/]+\/comments$/.test(url.pathname)) evidence.commentReads++;
    if (phase === 'native-offline' && url.pathname.startsWith('/api/v1/')) return route.continue();
    try {
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname === '/api/v1/catalog/social-previews') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        expect(response.headers()['cache-control']).toContain('no-store');
        expect(body[fixture.itemId].socialCaption).toBe(fixture.caption);
        evidence.previewReads.push({ phase, status: response.status(), ids: Object.keys(body) });
      }
      return await route.fulfill({ response });
    } catch (failure) {
      if (closing && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
      if (phase === 'calibration') {
        evidence.calibrationBridgeError = String(failure.message);
        return route.abort('failed');
      }
      throw failure;
    }
  });
  const page = await context.newPage();
  page.on('pageerror', error => evidence.pageErrors.push(error.message));
  page.on('requestfailed', request => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/v1/')) evidence.failedRequests.push({ path: url.pathname, error: request.failure()?.errorText });
  });
  await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=catalog');
  await expect(page.getByRole('heading', { name: 'Social browser product', exact: true })).toBeVisible();
  phase = 'calibration';
  await context.setOffline(true);
  evidence.calibration = await page.evaluate(async itemId => {
    try {
      const response = await fetch('/api/v1/catalog/social-previews?itemIds=' + itemId);
      const body = await response.json();
      return { browserOnline: navigator.onLine, status: response.status, includesTarget: Boolean(body[itemId]) };
    } catch (failure) { return { browserOnline: navigator.onLine, error: failure.name }; }
  }, fixture.itemId);
  expect(evidence.calibration.browserOnline).toBe(false);
  evidence.bridgeBypassesBrowserOffline = evidence.calibration.status === 200;
  phase = 'native-offline';
  await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
  const retries = page.getByRole('button', { name: 'Thử lại nội dung bài đăng', exact: true });
  await expect(retries).toHaveCount(2);
  await expect(page.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toHaveCount(0);
  const nativeFailures = () => evidence.failedRequests.filter(request => request.path === '/api/v1/catalog/social-previews' && request.error === 'net::ERR_INTERNET_DISCONNECTED').length;
  await expect.poll(nativeFailures).toBeGreaterThan(0);
  const failedBeforeRetry = nativeFailures();
  await retries.first().click();
  await expect.poll(nativeFailures).toBeGreaterThan(failedBeforeRetry);
  await expect(retries).toHaveCount(2);
  evidence.offlineRetryFailedNatively = true;
  phase = 'recovery';
  await context.setOffline(false);
  const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social browser product', exact: true }) });
  const gallery = card.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
  await expect(gallery).toBeVisible();
  await expect.poll(() => gallery.getByRole('img').evaluate(image => image.complete && image.naturalWidth === 16)).toBe(true);
  expect(await card.locator('p').filter({ hasText: '<script>Literal Social caption</script>' }).textContent()).toBe(fixture.caption);
  await expect(retries).toHaveCount(0);
  await expect(card.getByRole('button', { name: 'Thích Social browser product', exact: true })).toBeEnabled();
  expect(evidence.previewReads.some(read => read.phase === 'recovery')).toBe(true);
  evidence.networkEvents = await page.evaluate(() => window.socialNetworkEvents);
  expect(evidence.networkEvents).toEqual(expect.arrayContaining([{ type: 'offline', trusted: true }, { type: 'online', trusted: true }]));
  expect(evidence.writes).toEqual([]);
  expect(evidence.commentReads).toBe(0);
  await page.screenshot({ path: path.join(fixture.output, 'online-recovered.png') });
  closing = true;
  await context.close();
  expect(evidence.pageErrors).toEqual([]);
} catch (failure) {
  evidence.failure = String(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  closing = true;
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
