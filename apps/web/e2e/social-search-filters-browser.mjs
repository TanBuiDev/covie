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
const evidence = { stages: [], reads: [], writes: [], commentReads: 0, pageErrors: [] };
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
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Search fixture' } }));
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
    try {
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname === '/api/v1/catalog/items') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        evidence.reads.push({ query: url.search, status: response.status(), ids: body.page.items.map(item => item.id) });
      }
      return await route.fulfill({ response });
    } catch (failure) {
      if (closing && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
      throw failure;
    }
  });
  const page = await context.newPage();
  const cheap = 'Social browser product';
  const expensive = 'Social empty product';
  const stage = async (name, titles, sort, maxPrice) => {
    await expect(page.locator('article').getByRole('heading')).toHaveText(titles);
    await expect.poll(() => new URL(page.url()).searchParams.get('sort')).toBe(sort);
    await expect.poll(() => new URL(page.url()).searchParams.get('maxPrice')).toBe(maxPrice ?? null);
    const params = new URL(page.url()).searchParams;
    expect(params.get('q')).toBe('Social');
    expect(params.get('type')).toBe('PRODUCT');
    expect(params.get('maxPrice')).toBe(maxPrice ?? null);
    evidence.stages.push({ name, titles, query: new URL(page.url()).search });
  };
  await page.goto(origin + '/search?type=PRODUCT&q=Social&sort=PRICE_ASC&view=social');
  await stage('initial-price-asc', [cheap, expensive], 'PRICE_ASC');
  await page.getByRole('combobox', { name: 'Sắp xếp', exact: true }).click();
  await page.getByRole('option', { name: 'Giá cao nhất', exact: true }).click();
  await stage('native-price-desc', [expensive, cheap], 'PRICE_DESC');
  await page.getByPlaceholder('Đến (₫)', { exact: true }).fill('30000');
  await page.getByRole('button', { name: 'Áp dụng giá', exact: true }).click();
  await stage('native-max-price', [cheap], 'PRICE_DESC', '30000');
  await page.reload();
  await stage('deep-reload', [cheap], 'PRICE_DESC', '30000');
  await expect(page.getByPlaceholder('Đến (₫)', { exact: true })).toHaveValue('30000');
  await page.getByRole('button', { name: 'Mua sắm nhanh', exact: true }).click();
  await stage('catalog-retains-filter', [cheap], 'PRICE_DESC', '30000');
  await page.getByPlaceholder('Đến (₫)', { exact: true }).fill('');
  await page.getByRole('button', { name: 'Áp dụng giá', exact: true }).click();
  await stage('clear-restores-desc', [expensive, cheap], 'PRICE_DESC');
  expect(evidence.reads.some(read => new URLSearchParams(read.query).get('maxPrice') === '30000' && read.ids.length === 1 && read.ids[0] === fixture.itemId)).toBe(true);
  expect(evidence.reads.some(read => new URLSearchParams(read.query).get('sort') === 'PRICE_ASC' && read.ids.length === 2 && read.ids[0] === fixture.itemId)).toBe(true);
  expect(evidence.reads.some(read => new URLSearchParams(read.query).get('sort') === 'PRICE_DESC' && read.ids.length === 2 && read.ids[1] === fixture.itemId)).toBe(true);
  expect(evidence.writes).toEqual([]);
  expect(evidence.commentReads).toBe(0);
  await page.screenshot({ path: path.join(fixture.output, 'search-price-desc.png') });
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
