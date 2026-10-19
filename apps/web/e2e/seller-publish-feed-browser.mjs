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
const root = '/seller/' + fixture.merchantId + '/catalog';
const evidence = { actualNextApiAndStorage: true, items: [], writes: [], storagePuts: [], guestPreviews: [], pageErrors: [] };
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
    const closingContexts = new WeakSet();
    const contextFor = async owner => {
      const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
      await context.grantPermissions(['local-network-access'], { origin });
      if (owner) await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
      await context.addInitScript(() => {
        if (location.origin !== 'http://localhost:3000') return;
        localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
          location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Seller publish fixture' } }));
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
        const headers = await request.allHeaders();
        if (!owner) {
          expect(headers.authorization).toBeUndefined();
          expect(headers.cookie ?? '').not.toMatch(/(?:^|;\s*)covie_(?:at|rt)=/);
        }
        try {
          const response = await route.fetch({ url: target + url.pathname + url.search,
            headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
          if (url.pathname.startsWith('/api/v1/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())) {
            expect(owner).toBe(true);
            evidence.writes.push({ path: url.pathname, method: request.method(), status: response.status(),
              csrfHeader: Boolean(headers['x-csrf-token']), idempotencyKey: Boolean(headers['idempotency-key']), ifMatch: headers['if-match'] });
          }
          if (!owner && url.pathname === '/api/v1/catalog/social-previews') {
            const body = await response.json();
            expect(response.status()).toBe(200);
            expect(response.headers()['cache-control']).toContain('no-store');
            expect(JSON.stringify(body)).not.toContain('"storageKey"');
            for (const item of evidence.items) {
              expect(body[item.id].socialCaption).toBe(item.caption);
              expect(body[item.id].media).toHaveLength(1);
            }
            evidence.guestPreviews.push({ ids: Object.keys(body), status: response.status() });
          }
          return await route.fulfill({ response });
        } catch (failure) {
          if (closingContexts.has(context) && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
          throw failure;
        }
      });
      return context;
    };
    const owner = await contextFor(true);
    const page = await owner.newPage();
    page.on('pageerror', error => evidence.pageErrors.push({ message: redact(error.message), url: redact(page.url()), stack: redact(error.stack ?? '') }));
    page.on('response', response => {
      if (response.url().startsWith(fixture.storageOrigin + '/') && response.request().method() === 'PUT')
        evidence.storagePuts.push({ status: response.status() });
    });
    for (const entry of [
      { label: 'PRODUCT', type: 'PRODUCT', listingKind: 'STANDARD', file: 'first.png' },
      { label: 'SERVICE', type: 'SERVICE', listingKind: 'STANDARD', file: 'second.png' },
      { label: 'RESALE', type: 'PRODUCT', listingKind: 'RESALE', file: 'third.png' },
    ]) {
      await page.goto(origin + root + '/new');
      await expect(page.getByRole('heading', { name: 'Tạo nội dung bán', exact: true })).toBeVisible();
      if (entry.type === 'SERVICE') {
        await page.getByRole('combobox', { name: 'Loại', exact: true }).click();
        await page.getByRole('option', { name: 'Dịch vụ', exact: true }).click();
      }
      if (entry.listingKind === 'RESALE') {
        await page.getByRole('combobox', { name: 'Loại sản phẩm', exact: true }).click();
        await page.getByRole('option', { name: 'Thanh lý', exact: true }).click();
      }
      await page.getByRole('combobox', { name: 'Danh mục', exact: true }).click();
      await page.getByRole('option', { name: 'Gallery category', exact: true }).click();
      await page.getByRole('combobox', { name: 'Điểm bán', exact: true }).click();
      await page.getByRole('option', { name: /^Gallery counter/ }).click();
      entry.name = 'Seller journey ' + entry.label;
      entry.caption = '<b>Literal ' + entry.label + ' caption</b>\nPublished from Seller';
      await page.getByRole('textbox', { name: 'Tên', exact: true }).fill(entry.name);
      await page.getByRole('textbox', { name: 'Mô tả', exact: true }).fill('Commercial ' + entry.label);
      await page.getByLabel('Giá chính xác (VND)', { exact: true }).fill('25000');
      await page.getByRole('textbox', { name: 'Caption', exact: true }).fill(entry.caption);
      await page.getByLabel('Ảnh Social', { exact: true }).setInputFiles(path.join(fixture.output, entry.file));
      const created = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/merchants/' + fixture.merchantId + '/catalog-items'
        && response.request().method() === 'POST');
      await page.getByRole('button', { name: 'Tạo nội dung', exact: true }).click();
      const createdResponse = await created;
      expect(createdResponse.status()).toBe(201);
      const body = await createdResponse.json();
      expect(body).toMatchObject({ type: entry.type, listingKind: entry.listingKind, listingStatus: 'DRAFT', socialCaption: entry.caption });
      entry.id = body.id;
      entry.createVersion = body.version;
      await expect(page).toHaveURL(origin + root);
      await page.goto(origin + root + '/' + entry.id);
      await expect(page.getByRole('textbox', { name: 'Caption', exact: true })).toHaveValue(entry.caption);
      const published = page.waitForResponse(response => new URL(response.url()).pathname.endsWith('/' + entry.id + '/publish')
        && response.request().method() === 'POST');
      await page.getByRole('button', { name: 'Đăng bán', exact: true }).click();
      const publishResponse = await published;
      expect(publishResponse.status()).toBe(204);
      expect(publishResponse.headers().etag).toBeTruthy();
      await expect(page.getByRole('button', { name: 'Tạm dừng', exact: true })).toBeVisible();
      evidence.items.push(entry);
    }
    closingContexts.add(owner);
    await owner.close();
    const guest = await contextFor(false);
    const feed = await guest.newPage();
    feed.on('pageerror', error => evidence.pageErrors.push({ message: redact(error.message), url: redact(feed.url()), stack: redact(error.stack ?? '') }));
    await feed.goto(origin + '/search?q=Seller%20journey&sort=NEWEST&view=social');
    for (const item of evidence.items) {
      const card = feed.locator('article').filter({ has: feed.getByRole('heading', { name: item.name, exact: true }) });
      await expect(card).toHaveCount(1);
      const caption = card.locator('p').filter({ hasText: '<b>Literal ' + item.label + ' caption</b>' });
      expect(await caption.textContent()).toBe(item.caption);
      expect(await caption.locator('b').count()).toBe(0);
      await expect(card.getByText('25.000 ₫', { exact: true })).toBeVisible();
      const gallery = card.getByRole('region', { name: 'Ảnh ' + item.name, exact: true });
      await expect.poll(() => gallery.getByRole('img').evaluate(image => image.complete && image.naturalWidth === 8)).toBe(true);
    }
    expect((await guest.cookies(origin)).some(cookie => ['covie_at', 'covie_rt'].includes(cookie.name))).toBe(false);
    expect(evidence.storagePuts).toEqual([{ status: 200 }, { status: 200 }, { status: 200 }]);
    const creates = evidence.writes.filter(write => write.path === '/api/v1/merchants/' + fixture.merchantId + '/catalog-items');
    const publishes = evidence.writes.filter(write => write.path.endsWith('/publish'));
    expect(creates).toHaveLength(3);
    expect(publishes).toHaveLength(3);
    expect(creates.every(write => write.status === 201 && write.idempotencyKey)).toBe(true);
    expect(publishes.every(write => write.status === 204 && write.ifMatch)).toBe(true);
    expect(evidence.writes.every(write => write.csrfHeader)).toBe(true);
    expect(evidence.guestPreviews.length).toBeGreaterThan(0);
    evidence.guestVerifiedPublishedItems = true;
    evidence.browser = browser.version();
    closingContexts.add(guest);
    await guest.close();
    expect(evidence.pageErrors).toEqual([]);
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
  } finally { await browser.close(); }
})().catch(error => {
  fs.writeFileSync(path.join(fixture.output, 'failure-evidence.json'), JSON.stringify(evidence, null, 2));
  console.error(redact(error.stack ?? error));
  process.exitCode = 1;
});
