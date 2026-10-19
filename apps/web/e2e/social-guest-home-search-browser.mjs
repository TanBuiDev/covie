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
const evidence = { actualNextApiAndStorage: true, coordinatePreferenceSeeded: true,
  routes: [], discovery: [], previews: [], summaries: [], commentReads: 0, writes: [], pageErrors: [] };
const redact = value => fixture.token ? String(value).replaceAll(fixture.token, '[redacted]') : String(value);

(async () => {
  expect(fixture.token).toBe('');
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Isolated Next did not become ready');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated guest fixture' } }));
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
    let pendingDiscovery = 0;
    await context.route(origin + '/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const headers = await request.allHeaders();
      expect(headers.authorization).toBeUndefined();
      expect(headers.cookie ?? '').not.toMatch(/(?:^|;\s*)covie_(?:at|rt)=/);
      const discovery = url.pathname === '/api/v1/catalog/items';
      if (discovery) pendingDiscovery++;
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method()))
        evidence.writes.push({ method: request.method(), path: url.pathname });
      try {
        const response = await route.fetch({ url: target + url.pathname + url.search,
          headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
        if (discovery) evidence.discovery.push({ path: url.pathname + url.search, status: response.status() });
        if (url.pathname === '/api/v1/catalog/social-previews') {
          const body = await response.json();
          expect(response.status()).toBe(200);
          expect(response.headers()['cache-control']).toContain('no-store');
          expect(JSON.stringify(body)).not.toContain('"storageKey"');
          if (url.searchParams.getAll('itemIds').includes(fixture.itemId)) {
            expect(body[fixture.itemId].socialCaption).toBe(fixture.caption);
            expect(body[fixture.itemId].media.map(media => media.id)).toEqual(fixture.expectedMedia);
          }
          evidence.previews.push({ path: url.pathname + url.search, status: response.status(), returned: Object.keys(body).length });
        }
        if (url.pathname === '/api/v1/catalog/social-summary') {
          const body = await response.json();
          expect(response.status()).toBe(200);
          for (const summary of Object.values(body)) {
            expect(summary.liked).toBe(false);
            expect(summary.saved).toBe(false);
          }
          evidence.summaries.push({ path: url.pathname + url.search, status: response.status() });
        }
        if (/\/catalog\/items\/[^/]+\/comments$/.test(url.pathname)) evidence.commentReads++;
        return await route.fulfill({ response }).catch(() => undefined);
      } finally { if (discovery) pendingDiscovery--; }
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    for (const scenario of [
      { path: '/?view=catalog', pathname: '/', landmark: 'Khám phá danh mục' },
      { path: '/search?type=PRODUCT&sort=NEWEST&view=catalog', pathname: '/search', landmark: 'Kết quả tìm kiếm' },
    ]) {
      const previewStart = evidence.previews.length;
      await page.goto(origin + scenario.path);
      const routeHeading = page.locator('h1').filter({ hasText: scenario.landmark });
      await expect(routeHeading).toHaveText(scenario.landmark);
      if (scenario.pathname === '/') await expect(routeHeading).toBeVisible();
      else await expect(page.getByRole('button', { name: 'Mua sắm nhanh', exact: true })).toBeVisible();
      expect(new URL(page.url()).pathname).toBe(scenario.pathname);
      const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social browser product', exact: true }) });
      await expect(card).toHaveCount(1);
      await expect(card.getByRole('img', { name: 'Social browser product', exact: true })).toBeVisible();
      if (scenario.pathname === '/') {
        await expect(page.getByText('Chưa có dịch vụ nào gần bạn.', { exact: true })).toBeVisible();
        await expect(page.getByText('Chưa có đồ thanh lý nào gần bạn.', { exact: true })).toBeVisible();
      }
      await expect.poll(() => pendingDiscovery).toBe(0);
      expect(evidence.previews.length).toBe(previewStart);
      const discoveryStart = evidence.discovery.length;
      await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
      const gallery = card.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
      await expect(gallery).toBeVisible();
      expect(await card.locator('p').filter({ hasText: '<script>Literal Social caption</script>' }).textContent()).toBe(fixture.caption);
      await expect.poll(() => gallery.getByRole('img').evaluate(image => image.complete && image.naturalWidth === 16)).toBe(true);
      expect(evidence.previews.length).toBeGreaterThan(previewStart);
      const catalog = page.getByRole('button', { name: 'Mua sắm nhanh', exact: true });
      await catalog.click();
      await expect(catalog).toHaveAttribute('aria-pressed', 'true');
      await expect(gallery).toHaveCount(0);
      const previewAfterSocial = evidence.previews.length;
      await page.getByRole('button', { name: 'Khám phá', exact: true }).click();
      await expect(gallery).toBeVisible();
      expect(evidence.discovery.length).toBe(discoveryStart);
      expect((await context.cookies(origin)).some(cookie => ['covie_at', 'covie_rt'].includes(cookie.name))).toBe(false);
      evidence.routes.push({ pathname: scenario.pathname, catalogPreviews: previewStart,
        previewsAfterSocial: previewAfterSocial, discoveryReadsUnchangedOnToggle: true, noAuthCookies: true });
    }
    expect(evidence.commentReads).toBe(0);
    expect(evidence.writes).toEqual([]);
    expect(evidence.pageErrors).toEqual([]);
    expect(evidence.discovery.every(read => read.status === 200)).toBe(true);
    evidence.actualGuestNoAuthCookieOrSession = true;
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
