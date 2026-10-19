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
const evidence = { blocked: [], writes: [], pageErrors: [] };
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
  context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(error.message)));
  await context.grantPermissions(['local-network-access'], { origin });
  await context.addInitScript(() => {
    if (location.origin !== 'http://localhost:3000') return;
    localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Eligibility fixture' } }));
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
    if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) evidence.writes.push(url.pathname);
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
  await expect(page.getByRole('button', { name: 'Khám phá', exact: true })).toBeVisible();
  await expect(page.locator('article')).toHaveCount(2);
  await expect(page.getByRole('heading', { name: 'Social browser product', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Social empty product', exact: true })).toBeVisible();
  for (const entry of fixture.ineligibleItems) await expect(page.getByRole('heading', { name: 'Ineligible ' + entry.state, exact: true })).toHaveCount(0);
  const result = await page.evaluate(async ({ itemId, ineligibleItems }) => {
    const query = new URLSearchParams();
    for (const id of [itemId, ...ineligibleItems.map(entry => entry.id)]) query.append('itemIds', id);
    const reads = [];
    for (const route of ['social-previews', 'social-summary']) {
      const response = await fetch('/api/v1/catalog/' + route + '?' + query);
      const body = await response.json();
      reads.push({ route, status: response.status, cache: response.headers.get('cache-control'), ids: Object.keys(body), leaked: JSON.stringify(body).includes('Private ') });
    }
    const comments = [];
    for (const entry of ineligibleItems) {
      const response = await fetch('/api/v1/catalog/items/' + entry.id + '/comments');
      const body = await response.json();
      comments.push({ ...entry, status: response.status, code: body.error?.code, leaked: JSON.stringify(body).includes('Private ') });
    }
    const visibleComments = await fetch('/api/v1/catalog/items/' + itemId + '/comments');
    return { reads, comments, visibleCommentsStatus: visibleComments.status };
  }, { itemId: fixture.itemId, ineligibleItems: fixture.ineligibleItems });
  for (const read of result.reads) {
    expect(read.status).toBe(200);
    expect(read.cache).toContain('no-store');
    expect(read.ids).toEqual([fixture.itemId]);
    expect(read.leaked).toBe(false);
  }
  expect(result.visibleCommentsStatus).toBe(200);
  for (const entry of result.comments) expect(entry).toMatchObject({ status: 404, code: 'RESOURCE_NOT_FOUND', leaked: false });
  evidence.blocked = result.comments;
  evidence.reads = result.reads;
  evidence.visibleCommentsStatus = result.visibleCommentsStatus;
  await page.screenshot({ path: path.join(fixture.output, 'eligible-search.png') });
  expect(evidence.writes).toEqual([]);
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
