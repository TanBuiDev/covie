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
const destination = '/search?type=PRODUCT&q=Social&sort=NEWEST&view=social';
const evidence = { deliveries: [], events: [], otherWrites: [], pageErrors: [], modes: [], clicks: [] };
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
  context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(error.message)));
  await context.addInitScript(() => {
    if (location.origin !== 'http://localhost:3000') return;
    localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Ads fixture' } }));
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
    try {
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname === '/api/v1/ads/active') {
        expect(response.status()).toBe(200);
        expect(response.headers()['cache-control']).toContain('no-store');
        const body = await response.json();
        for (const ad of body) {
          expect(ad.placement).toBe(url.searchParams.get('placement'));
          expect(ad.targetUrl).toBe(destination);
          expect(ad.deliveryToken).toMatch(/^[\da-f-]{36}$/i);
          evidence.deliveries.push({ campaignId: ad.id, placement: ad.placement, title: ad.title });
        }
      }
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())) {
        if (/^\/api\/v1\/ads\/[^/]+\/events$/.test(url.pathname)) {
          expect(response.status()).toBe(204);
          const body = request.postDataJSON();
          evidence.events.push({ campaignId: url.pathname.split('/')[4], type: body.type, eventId: body.eventId,
            visibleMillis: body.visibleMillis, status: response.status() });
        } else evidence.otherWrites.push({ path: url.pathname, method: request.method() });
      }
      return await route.fulfill({ response });
    } catch (failure) {
      if (closing && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
      throw failure;
    }
  });
  const page = await context.newPage();
  await page.goto(origin + '/?view=catalog');
  await expect(page.getByRole('heading', { name: 'Khám phá danh mục', exact: true })).toBeVisible();
  const adLink = placement => page.getByRole('link', { name: 'Fixture ' + placement + ' — Xem fixture', exact: true });
  const verifyPlacements = async mode => {
    for (const placement of ['HOME_HERO', 'IN_FEED']) {
      await expect(adLink(placement)).toHaveAttribute('href', destination);
      await expect(adLink(placement).getByText('Quảng cáo', { exact: true })).toBeAttached();
    }
    evidence.modes.push(mode);
  };
  await verifyPlacements('initial-catalog');
  await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toBeVisible();
  await verifyPlacements('social');
  await page.getByRole('button', { name: 'Mua sắm nhanh', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toHaveCount(0);
  await verifyPlacements('catalog');
  for (const placement of ['HOME_HERO', 'IN_FEED']) {
    await adLink(placement).scrollIntoViewIfNeeded();
    await expect(adLink(placement)).toBeVisible();
    const campaignId = evidence.deliveries.find(delivery => delivery.placement === placement)?.campaignId;
    expect(campaignId).toBeTruthy();
    await expect.poll(() => evidence.events.filter(event => event.campaignId === campaignId && event.type === 'IMPRESSION').length).toBeGreaterThan(0);
    await adLink(placement).click();
    await expect(page).toHaveURL(origin + destination);
    await expect(page.locator('h1').filter({ hasText: 'Kết quả tìm kiếm' })).toHaveText('Kết quả tìm kiếm');
    await expect(page.getByRole('heading', { name: 'Social browser product', exact: true })).toBeVisible();
    await expect.poll(() => evidence.events.filter(event => event.campaignId === campaignId && event.type === 'CLICK').length).toBe(1);
    evidence.clicks.push({ placement, pathname: new URL(page.url()).pathname, query: new URL(page.url()).search });
    await page.goBack();
    await expect(page.getByRole('heading', { name: 'Khám phá danh mục', exact: true })).toBeVisible();
    await verifyPlacements('back-' + placement);
  }
  expect(evidence.otherWrites).toEqual([]);
  expect(evidence.events.filter(event => event.type === 'IMPRESSION').every(event => event.visibleMillis === 1000)).toBe(true);
  await page.screenshot({ path: path.join(fixture.output, 'home-ads.png') });
  closing = true;
  await context.close();
  expect(evidence.pageErrors).toEqual([]);
} catch (failure) {
  evidence.failure = String(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
