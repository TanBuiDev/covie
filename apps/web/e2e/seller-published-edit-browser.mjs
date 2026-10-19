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
const caption = '<script>Published edit literal</script>\nCaption submitted for reapproval';
const evidence = { caption, writes: [], adminReads: [], pageErrors: [] };
const redact = value => String(value).replaceAll(fixture.token, '[redacted]').replaceAll(fixture.adminToken, '[redacted]');
const closing = new WeakSet();
const contexts = [];
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
  const contextFor = async token => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    contexts.push(context);
    await context.addCookies([{ name: 'covie_at', value: token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(redact(error.message))));
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
        if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) {
          const errorCode = response.status() >= 400 ? (await response.json()).error?.code : undefined;
          evidence.writes.push({ path: url.pathname, method: request.method(), status: response.status(),
            ifMatch: headers['if-match'], csrf: Boolean(headers['x-csrf-token']), body: request.postDataJSON(), etag: response.headers().etag, errorCode });
        }
        if (url.pathname === '/api/v1/admin/catalog-items/' + fixture.itemId) {
          const body = await response.json();
          expect(response.status()).toBe(200);
          expect(response.headers()['cache-control']).toContain('no-store');
          expect(body.socialCaption).toBe(caption);
          expect(body.item.moderationStatus).toBe('PENDING');
          expect(body.moderationCases).toHaveLength(1);
          evidence.caseId = body.moderationCases[0].id;
          evidence.adminReads.push({ status: response.status(), moderationStatus: body.item.moderationStatus });
        }
        return await route.fulfill({ response });
      } catch (failure) {
        if (closing.has(context) && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
        throw failure;
      }
    });
    return context;
  };
  const seller = await contextFor(fixture.token);
  const first = await seller.newPage();
  const second = await seller.newPage();
  const editUrl = origin + `/seller/${fixture.merchantId}/catalog/${fixture.itemId}`;
  for (const page of [first, second]) {
    await page.goto(editUrl);
    await expect(page.getByRole('heading', { name: 'Navigation item', exact: true, level: 1 })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Caption', exact: true })).toBeEnabled();
    await expect(page.getByRole('textbox', { name: 'Caption', exact: true })).toHaveValue('Persisted caption');
  }
  await first.getByRole('textbox', { name: 'Caption', exact: true }).fill(caption);
  await first.getByRole('button', { name: 'Lưu thông tin', exact: true }).click();
  await expect.poll(() => evidence.writes.length).toBe(1);
  expect(evidence.writes[0]).toMatchObject({ method: 'PATCH', status: 204, ifMatch: '"v0"', csrf: true, body: { socialCaption: caption }, etag: '"v1"' });
  expect(Object.keys(evidence.writes[0].body)).toEqual(['socialCaption']);
  await expect(first.getByText('Đang chờ kiểm duyệt', { exact: true })).toBeVisible();
  await expect(first.getByRole('textbox', { name: 'Caption', exact: true })).toHaveValue(caption);
  await second.getByRole('textbox', { name: 'Caption', exact: true }).fill('Stale caption must not replace the accepted edit');
  await second.getByRole('button', { name: 'Lưu thông tin', exact: true }).click();
  await expect.poll(() => evidence.writes.length).toBe(2);
  expect(evidence.writes[1]).toMatchObject({ method: 'PATCH', status: 409, errorCode: 'RESOURCE_VERSION_CONFLICT', ifMatch: '"v0"', csrf: true });
  await expect(second.getByText('Dữ liệu đã thay đổi ở nơi khác. Tải dữ liệu mới trước khi tiếp tục.', { exact: true })).toBeVisible();
  await expect(second.getByRole('button', { name: 'Lưu thông tin', exact: true })).toBeDisabled();
  const admin = await contextFor(fixture.adminToken);
  const review = await admin.newPage();
  await review.goto(origin + '/admin/listings?item=' + fixture.itemId);
  await expect(review.getByRole('heading', { name: 'Nội dung hiển thị trên Khám phá', exact: true })).toBeVisible();
  const literal = review.locator('p').filter({ hasText: '<script>Published edit literal</script>' });
  await expect(literal).toHaveText(caption);
  await expect(literal.locator('script')).toHaveCount(0);
  await expect(review.getByText('Commerce baseline', { exact: true })).toBeVisible();
  await expect(review.getByRole('link', { name: 'Mở hồ sơ ' + evidence.caseId, exact: true })).toBeVisible();
  await review.screenshot({ path: path.join(fixture.output, 'admin-published-caption.png') });
  expect(evidence.adminReads.length).toBeGreaterThan(0);
  expect(evidence.writes).toHaveLength(2);
  for (const context of contexts) { closing.add(context); await context.close(); }
  expect(evidence.pageErrors).toEqual([]);
} catch (failure) {
  evidence.failure = redact(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
