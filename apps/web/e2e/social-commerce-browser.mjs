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
const evidence = { writes: [], pageErrors: [] };
const redact = value => String(value).replaceAll(fixture.buyerToken, '[redacted]').replaceAll(fixture.sellerToken, '[redacted]');
const contexts = [];
const closing = new WeakSet();
let browser;
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
  const contextFor = async (actor, token) => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    contexts.push(context);
    await context.addCookies([{ name: 'covie_at', value: token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(redact(error.message))));
    await context.addInitScript(() => {
      if (location.origin !== 'http://localhost:3000') return;
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Commerce fixture' } }));
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
      try {
        const response = await route.fetch({ url: target + url.pathname + url.search,
          headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
        if (url.pathname.startsWith('/api/v1/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())) {
          evidence.writes.push({ actor, path: url.pathname, method: request.method(), status: response.status(),
            csrf: Boolean(headers['x-csrf-token']), idempotency: Boolean(headers['idempotency-key']) });
        }
        return await route.fulfill({ response });
      } catch (failure) {
        if (closing.has(context) && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
        throw failure;
      }
    });
    return context;
  };
  const buyer = await contextFor('buyer', fixture.buyerToken);
  const page = await buyer.newPage();
  await page.goto(origin + '/search?type=PRODUCT&q=Social&sort=NEWEST&view=social');
  const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social commerce product', exact: true }) });
  await expect(card).toContainText('Social commerce caption');
  await card.getByRole('link', { name: 'Xem & Đặt', exact: true }).click();
  await expect(page).toHaveURL(new RegExp('/items/' + fixture.itemId));
  await expect(page.getByRole('heading', { name: 'Social commerce product', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Thêm vào giỏ', exact: true }).click();
  await expect.poll(() => evidence.writes.some(write => write.path === '/api/v1/me/carts/items' && write.status === 201)).toBe(true);
  await page.goto(origin + '/cart');
  await page.getByRole('link', { name: 'Xem và thanh toán giỏ này', exact: true }).click();
  await page.getByRole('link', { name: 'Tiếp tục đặt hàng', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Xác nhận đơn', exact: true })).toBeVisible();
  await expect.poll(() => evidence.writes.some(write => write.path === '/api/v1/checkouts/preview' && write.status === 200)).toBe(true);
  await page.getByRole('button', { name: 'Đặt hàng', exact: true }).first().click();
  await expect(page).toHaveURL(/\/checkout\/success\/[0-9a-f-]+$/i);
  evidence.orderId = new URL(page.url()).pathname.split('/').at(-1);
  await expect(page.getByRole('heading', { name: 'Đã tạo đơn hàng', exact: true })).toBeVisible();
  const seller = await contextFor('seller', fixture.sellerToken);
  const fulfillment = await seller.newPage();
  await fulfillment.goto(origin + `/seller/${fixture.merchantId}/orders/${evidence.orderId}`);
  const command = async (current, label, confirm) => {
    await current.getByRole('button', { name: label, exact: true }).click();
    await current.getByRole('dialog').getByRole('button', { name: confirm, exact: true }).click();
    await expect(current.getByRole('dialog')).toHaveCount(0);
  };
  await command(fulfillment, 'Xác nhận đơn', 'Xác nhận');
  await command(fulfillment, 'Sẵn sàng nhận', 'Đã sẵn sàng');
  await page.goto(origin + '/orders/' + evidence.orderId);
  await command(page, 'Đã nhận hàng', 'Hoàn tất đơn');
  await page.goto(origin + '/orders/' + evidence.orderId + '/review');
  await page.getByRole('radio', { name: '5 sao', exact: true }).click();
  await page.getByRole('button', { name: 'Gửi đánh giá', exact: true }).click();
  await expect.poll(() => evidence.writes.some(write => write.path === '/api/v1/me/reviews' && write.status === 201)).toBe(true);
  const publicReview = await page.evaluate(async itemId => {
    const response = await fetch('/api/v1/catalog/items/' + itemId + '/reviews');
    return { status: response.status, body: await response.json() };
  }, fixture.itemId);
  expect(publicReview.status).toBe(200);
  evidence.publicReview = publicReview.body;
  expect(JSON.stringify(publicReview.body)).toContain('"verifiedPurchase":true');
  expect(evidence.writes.every(write => write.status >= 200 && write.status < 300 && write.csrf)).toBe(true);
  expect(evidence.writes.filter(write => write.path === '/api/v1/checkouts/commit')).toHaveLength(1);
  expect(evidence.writes.find(write => write.path === '/api/v1/checkouts/commit').idempotency).toBe(true);
  await page.screenshot({ path: path.join(fixture.output, 'verified-review.png') });
  for (const context of contexts) { closing.add(context); await context.close(); }
  expect(evidence.pageErrors).toEqual([]);
} catch (failure) {
  evidence.failure = redact(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
