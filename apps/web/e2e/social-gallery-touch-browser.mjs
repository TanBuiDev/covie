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
const evidence = { actualNextApiAndStorage: true, inputSource: 'Chrome CDP, not DOM-dispatched events or physical hardware', writes: [], gestures: [], pageErrors: [] };
const redact = value => String(value).replaceAll(fixture.token, '[redacted]');

(async () => {
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Isolated Next did not become ready');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 360, height: 900 }, isMobile: true, hasTouch: true, locale: 'vi-VN' });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated touch fixture' } }));
      window.galleryTouchEvents = [];
      window.galleryClickEvents = [];
      for (const type of ['touchstart', 'touchmove', 'touchend', 'touchcancel']) {
        document.addEventListener(type, event => {
          if (event.target.closest?.('[aria-label="Ảnh Social browser product"]')) window.galleryTouchEvents.push({ type, trusted: event.isTrusted });
        }, { capture: true, passive: true });
      }
      document.addEventListener('click', event => {
        if (!event.target.closest?.('[aria-label="Ảnh Social browser product"] a')) return;
        const row = { trusted: event.isTrusted };
        window.galleryClickEvents.push(row);
        setTimeout(() => { row.prevented = event.defaultPrevented; }, 0);
      }, true);
    });
    await context.routeWebSocket('**/*', socket => {
      const url = new URL(socket.url());
      const upstream = new WebSocket(target.replace('http:', 'ws:') + url.pathname + url.search);
      const queued = [];
      socket.onMessage(message => { if (upstream.readyState === WebSocket.OPEN) upstream.send(message); else queued.push(message); });
      upstream.on('open', () => { for (const message of queued) upstream.send(message); });
      upstream.on('message', (message, binary) => socket.send(binary ? message : message.toString()));
      upstream.on('error', error => { evidence.socketError = redact(error.message); });
      upstream.on('close', () => socket.close());
      socket.onClose(() => upstream.close());
    });
    await context.route(origin + '/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) evidence.writes.push({ method: request.method(), path: url.pathname });
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    const cdp = await context.newCDPSession(page);
    const frame = () => page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    const gallery = page.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
    await expect(gallery.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    await expect.poll(() => gallery.locator('img').evaluate(image => image.complete && image.naturalWidth === 16)).toBe(true);
    const initialUrl = page.url();
    const center = async () => {
      await gallery.locator('a').evaluate(element => element.scrollIntoView({ block: 'center', behavior: 'instant' }));
      await frame();
      return gallery.locator('a').boundingBox();
    };
    const gesture = async (from, to) => {
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchStart', touchPoints: [{ x: from.x, y: from.y, id: 1 }] });
      for (let step = 1; step <= 8; step++) {
        await cdp.send('Input.dispatchTouchEvent', { type: 'touchMove', touchPoints: [{ x: from.x + (to.x - from.x) * step / 8, y: from.y + (to.y - from.y) * step / 8, id: 1 }] });
        await frame();
      }
      await cdp.send('Input.dispatchTouchEvent', { type: 'touchEnd', touchPoints: [] });
    };
    let box = await center();
    let start = { x: box.x + box.width * 0.8, y: box.y + box.height / 2 };
    let end = { x: box.x + box.width * 0.2, y: start.y };
    await gesture(start, end);
    await cdp.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: end.x, y: end.y, button: 'left', clickCount: 1 });
    await cdp.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: end.x, y: end.y, button: 'left', clickCount: 1 });
    await expect(gallery.getByText('Ảnh 2/6', { exact: true })).toBeVisible();
    await expect.poll(() => page.evaluate(() => window.galleryClickEvents.at(-1)?.prevented)).toBe(true);
    await expect(page).toHaveURL(initialUrl);
    evidence.gestures.push({ kind: 'horizontal-left', counter: '2/6', incidentalClickPrevented: true, sameUrl: true });
    box = await center();
    start = { x: box.x + box.width * 0.2, y: box.y + box.height / 2 };
    end = { x: box.x + box.width * 0.8, y: start.y };
    await gesture(start, end);
    await expect(gallery.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    await expect(page).toHaveURL(initialUrl);
    evidence.gestures.push({ kind: 'horizontal-right', counter: '1/6', sameUrl: true });
    box = await center();
    const beforeScroll = await page.evaluate(() => window.scrollY);
    start = { x: box.x + box.width / 2, y: box.y + box.height * 0.8 };
    end = { x: start.x + 8, y: box.y + box.height * 0.2 };
    await gesture(start, end);
    await expect.poll(() => page.evaluate(() => window.scrollY)).toBeGreaterThan(beforeScroll + 20);
    await expect(gallery.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    await expect(page).toHaveURL(initialUrl);
    evidence.gestures.push({ kind: 'vertical-up', beforeScroll, afterScroll: await page.evaluate(() => window.scrollY), counter: '1/6', sameUrl: true });
    evidence.touchEvents = await page.evaluate(() => window.galleryTouchEvents);
    evidence.clickEvents = await page.evaluate(() => window.galleryClickEvents);
    expect(evidence.touchEvents.length).toBeGreaterThan(10);
    expect(evidence.touchEvents.every(event => event.trusted)).toBe(true);
    expect(evidence.clickEvents.every(event => event.trusted)).toBe(true);
    expect(evidence.touchEvents.some(event => event.type === 'touchend')).toBe(true);
    evidence.captures = [];
    for (const width of [360, 768, 1280, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'instant' }));
      await frame();
      const dimensions = await page.evaluate(() => ({ scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth }));
      expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.clientWidth);
      evidence.captures.push({ width, ...dimensions });
      await page.screenshot({ path: path.join(fixture.output, `social-full-${width}.png`), fullPage: true });
    }
    expect(evidence.writes).toEqual([]);
    expect(evidence.pageErrors).toEqual([]);
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    await context.close();
  } finally { await browser.close(); }
})().catch(error => {
  fs.writeFileSync(path.join(fixture.output, 'failure-evidence.json'), JSON.stringify(evidence, null, 2));
  console.error(redact(error.stack ?? error));
  process.exitCode = 1;
});
