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
const evidence = { actualNextApiAndStorage: true, coordinatePreferenceSeeded: true, previews: [], discovery: [], storageGets: [], storageFailures: [], writes: [], viewports: [], pageErrors: [] };
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
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN', hasTouch: true });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated browser fixture' } }));
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
      if (url.pathname === '/api/v1/catalog/social-previews') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        expect(response.headers()['cache-control']).toContain('no-store');
        const product = body[fixture.itemId];
        expect(product.socialCaption).toBe(fixture.caption);
        expect(product.media.map(media => media.id)).toEqual(fixture.expectedMedia);
        expect(JSON.stringify(body)).not.toContain('"storageKey"');
        evidence.previews.push({ ids: url.searchParams.getAll('itemIds'), returned: Object.keys(body).length,
          mediaIds: product.media.map(media => media.id), cacheControl: response.headers()['cache-control'] });
      }
      if (url.pathname === '/api/v1/catalog/items') evidence.discovery.push({ path: url.pathname + url.search, status: response.status() });
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    page.on('response', response => {
      if (response.url().startsWith(fixture.storageOrigin + '/')) evidence.storageGets.push({ url: response.url(), status: response.status() });
    });
    page.on('requestfailed', request => {
      if (request.url().startsWith(fixture.storageOrigin + '/')) evidence.storageFailures.push({ url: request.url(), error: request.failure()?.errorText });
    });
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=catalog');
    const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social browser product', exact: true }) });
    await expect(card).toHaveCount(1);
    await expect(card.getByRole('img', { name: 'Social browser product', exact: true })).toBeVisible();
    expect(evidence.previews).toHaveLength(0);
    const initialDiscoveryCount = evidence.discovery.length;
    await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
    const gallery = card.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
    await expect(gallery).toBeVisible();
    await expect(card.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    const caption = card.locator('p').filter({ hasText: '<script>Literal Social caption</script>' });
    expect(await caption.textContent()).toBe(fixture.caption);
    expect(await caption.locator('script').count()).toBe(0);
    await expect(card.getByRole('button', { name: 'Xem thêm nội dung', exact: true })).toHaveAttribute('aria-expanded', 'false');
    await card.getByRole('button', { name: 'Xem thêm nội dung', exact: true }).click();
    await expect(card.getByRole('button', { name: 'Thu gọn nội dung', exact: true })).toHaveAttribute('aria-expanded', 'true');
    await expect(card.getByRole('button', { name: 'Thích Social browser product', exact: true })).toBeEnabled();
    await expect(card.getByRole('link', { name: 'Xem & Đặt', exact: true })).toHaveAttribute('href', new RegExp('/items/' + fixture.itemId));
    await expect(card.getByText('25.000 ₫', { exact: true })).toBeVisible();
    const empty = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social empty product', exact: true }) });
    await expect(empty.getByText('Chưa có ảnh', { exact: true })).toBeVisible();
    expect(evidence.discovery.length).toBe(initialDiscoveryCount);
    const loaded = async index => {
      const image = gallery.getByRole('img', { name: `Social browser product — Ảnh ${index}/6`, exact: true });
      await expect.poll(() => image.evaluate(element => element.complete && element.naturalWidth === 16 && element.naturalHeight === 12)).toBe(true);
    };
    await loaded(1);
    await gallery.focus();
    await page.keyboard.press('ArrowRight');
    await expect(card.getByText('Ảnh 2/6', { exact: true })).toBeVisible();
    await loaded(2);
    await page.keyboard.press('ArrowLeft');
    await expect(card.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    for (const width of [360, 768, 1280, 1440]) {
      await page.setViewportSize({ width, height: 900 });
      await card.scrollIntoViewIfNeeded();
      const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
      const box = await gallery.locator('a').boundingBox();
      evidence.viewports.push({ width, documentWidth, clientWidth, galleryWidth: box.width, galleryHeight: box.height });
      expect(documentWidth).toBeLessThanOrEqual(width);
      expect(documentWidth).toBeLessThanOrEqual(clientWidth);
      expect(Math.abs(box.width / box.height - 4 / 3)).toBeLessThan(0.02);
      await page.screenshot({ path: path.join(fixture.output, `social-${width}.png`) });
    }
    for (let index = 2; index <= 6; index++) {
      await gallery.getByRole('button', { name: 'Ảnh tiếp theo', exact: true }).click();
      await expect(card.getByText(`Ảnh ${index}/6`, { exact: true })).toBeVisible();
      if (index < 6) await loaded(index);
    }
    await expect(gallery.getByText('Không tải được ảnh', { exact: true })).toBeVisible();
    await expect(gallery.getByRole('button', { name: 'Ảnh tiếp theo', exact: true })).toBeDisabled();
    await expect.poll(() => evidence.storageGets.some(response => response.url.includes(fixture.expectedMedia[5]) && response.status >= 400)
      || evidence.storageFailures.some(request => request.url.includes(fixture.expectedMedia[5]))).toBe(true);
    evidence.actualMissingObjectFallback = true;
    await gallery.getByRole('button', { name: 'Ảnh trước', exact: true }).click();
    await loaded(5);
    const beforeCatalog = evidence.previews.length;
    await page.getByRole('button', { name: 'Mua sắm nhanh', exact: true }).click();
    await expect(gallery).toHaveCount(0);
    await expect(card.getByRole('img', { name: 'Social browser product', exact: true })).toBeVisible();
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    expect(evidence.previews).toHaveLength(beforeCatalog);
    await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
    await expect.poll(() => evidence.previews.length).toBeGreaterThan(beforeCatalog);
    await expect(card.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    const beforeReload = evidence.previews.length;
    await page.reload();
    await expect.poll(() => evidence.previews.length).toBeGreaterThan(beforeReload);
    await expect(card.getByText('Ảnh 1/6', { exact: true })).toBeVisible();
    expect(await caption.textContent()).toBe(fixture.caption);
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
