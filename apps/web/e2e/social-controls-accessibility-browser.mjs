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
const evidence = { widths: [], violations: [], writes: [], pageErrors: [] };
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
  const context = await browser.newContext({ viewport: { width: 360, height: 900 }, locale: 'vi-VN', reducedMotion: 'reduce' });
  await context.grantPermissions(['local-network-access'], { origin });
  context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(error.message)));
  await context.addInitScript(() => {
    if (location.origin !== 'http://localhost:3000') return;
    localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Control measurement fixture' } }));
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
    try {
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      return await route.fulfill({ response });
    } catch (failure) {
      if (closing && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
      throw failure;
    }
  });
  const page = await context.newPage();
  await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
  const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social browser product', exact: true }) });
  await expect(card.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toBeVisible();
  await expect(card.getByRole('button', { name: 'Thích Social browser product', exact: true })).toBeEnabled();
  for (const width of [360, 768, 1280, 1440]) {
    await page.setViewportSize({ width, height: 900 });
    await page.evaluate(() => document.fonts.ready);
    const controls = await card.locator('button').evaluateAll(elements => elements.map(element => {
      const bounds = element.getBoundingClientRect();
      return { name: element.getAttribute('aria-label') || element.textContent.trim(), width: bounds.width, height: bounds.height };
    }));
    for (const locator of [card.getByRole('link', { name: 'Xem & Đặt', exact: true }),
      page.getByRole('button', { name: 'Khám phá', exact: true }), page.getByRole('button', { name: 'Mua sắm nhanh', exact: true })]) {
      await expect(locator).toBeVisible();
      controls.push(await locator.evaluate(element => {
        const bounds = element.getBoundingClientRect();
        return { name: element.getAttribute('aria-label') || element.textContent.trim(), width: bounds.width, height: bounds.height };
      }));
    }
    const motion = await card.evaluate(element => ({ reduced: matchMedia('(prefers-reduced-motion: reduce)').matches,
      transitionDuration: getComputedStyle(element).transitionDuration }));
    const toggleDuration = await page.getByRole('button', { name: 'Khám phá', exact: true }).evaluate(element => getComputedStyle(element).transitionDuration);
    expect(motion).toEqual({ reduced: true, transitionDuration: '0s' });
    expect(toggleDuration).toBe('0s');
    const documentBounds = await page.evaluate(() => ({ viewport: innerWidth, client: document.documentElement.clientWidth, scroll: document.documentElement.scrollWidth }));
    expect(documentBounds.viewport).toBe(width);
    expect(documentBounds.scroll).toBeLessThanOrEqual(documentBounds.client);
    evidence.widths.push({ width, documentBounds, motion, toggleDuration, controls });
    for (const control of controls) if (!control.name || control.width < 44 || control.height < 44)
      evidence.violations.push({ viewport: width, ...control });
    await card.screenshot({ path: path.join(fixture.output, 'social-controls-' + width + '.png') });
  }
  expect(evidence.writes).toEqual([]);
  closing = true;
  await context.close();
  expect(evidence.pageErrors).toEqual([]);
  expect(evidence.violations).toEqual([]);
} catch (failure) {
  evidence.failure = String(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  closing = true;
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
