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
const evidence = { writes: [], publicChecks: [], adminDetails: [], pageErrors: [] };
const redact = value => String(value).replaceAll(fixture.adminToken, '[redacted]');
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
  const contextFor = async admin => {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    contexts.push(context);
    await context.grantPermissions(['local-network-access'], { origin });
    if (admin) await context.addCookies([{ name: 'covie_at', value: fixture.adminToken, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    context.on('page', page => page.on('pageerror', error => evidence.pageErrors.push(redact(error.message))));
    await context.addInitScript(() => {
      if (location.origin !== 'http://localhost:3000') return;
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Admin moderation fixture' } }));
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
      if (!admin) {
        expect(headers.authorization).toBeUndefined();
        expect(headers.cookie ?? '').not.toMatch(/(?:^|;\s*)covie_(?:at|rt)=/);
      }
      try {
        const response = await route.fetch({ url: target + url.pathname + url.search,
          headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
        if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) {
          expect(admin).toBe(true);
          const body = await response.json();
          evidence.writes.push({ path: url.pathname, method: request.method(), status: response.status(),
            csrf: Boolean(headers['x-csrf-token']), idempotency: Boolean(headers['idempotency-key']), body: request.postDataJSON(), result: body });
          if (url.pathname.endsWith('/moderation-cases')) evidence.caseId = body.caseId;
        }
        if (url.pathname === '/api/v1/admin/catalog-items/' + fixture.itemId) {
          const body = await response.json();
          expect(response.status()).toBe(200);
          expect(response.headers()['cache-control']).toContain('no-store');
          evidence.adminDetails.push(body.item.moderationStatus);
        }
        return await route.fulfill({ response });
      } catch (failure) {
        if (closing.has(context) && /Request context disposed|Target page, context or browser has been closed/.test(String(failure))) return;
        throw failure;
      }
    });
    return context;
  };
  const guest = await contextFor(false);
  const feed = await guest.newPage();
  const card = feed.locator('article').filter({ has: feed.getByRole('heading', { name: 'Social browser product', exact: true }) });
  const publicCheck = async (stage, visible) => {
    if (stage === 'initial') await feed.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    else await feed.reload();
    await expect(feed.getByRole('button', { name: 'Khám phá', exact: true })).toBeVisible();
    await expect(feed.locator('article').filter({ has: feed.getByRole('heading', { name: 'Social empty product', exact: true }) })).toBeVisible();
    await expect(card).toHaveCount(visible ? 1 : 0);
    if (visible) {
      const gallery = card.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
      await expect(gallery).toBeVisible();
      await expect.poll(() => gallery.getByRole('img').evaluate(image => image.complete && image.naturalWidth === 16)).toBe(true);
      expect(await card.locator('p').filter({ hasText: '<script>Literal Social caption</script>' }).textContent()).toBe(fixture.caption);
    }
    const result = await feed.evaluate(async itemId => {
      const preview = await fetch('/api/v1/catalog/social-previews?itemIds=' + itemId);
      const body = await preview.json();
      const comments = await fetch('/api/v1/catalog/items/' + itemId + '/comments');
      return { previewStatus: preview.status, previewCache: preview.headers.get('cache-control'), included: Boolean(body[itemId]), commentStatus: comments.status };
    }, fixture.itemId);
    expect(result).toMatchObject({ previewStatus: 200, included: visible, commentStatus: visible ? 200 : 404 });
    expect(result.previewCache).toContain('no-store');
    evidence.publicChecks.push({ stage, ...result });
  };
  await publicCheck('initial', true);
  const admin = await contextFor(true);
  const review = await admin.newPage();
  await review.goto(origin + '/admin/listings');
  await review.getByLabel('Tìm nội dung', { exact: true }).fill(fixture.itemId);
  await review.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await expect(review.locator('table tbody tr')).toHaveCount(1);
  await review.getByRole('button', { name: 'Xem Social browser product', exact: true }).click();
  await expect(review.getByRole('heading', { name: 'Nội dung hiển thị trên Khám phá', exact: true })).toBeVisible();
  await review.getByLabel('Mã lý do', { exact: true }).fill('BROWSER_REVIEW');
  await review.getByRole('checkbox', { name: 'Tôi xác nhận mở hồ sơ; thao tác này không đổi trạng thái nội dung.', exact: true }).check();
  await review.getByRole('button', { name: 'Xác nhận mở hồ sơ', exact: true }).click();
  await expect.poll(() => Boolean(evidence.caseId)).toBe(true);
  await expect(review).toHaveURL(origin + '/admin/moderation/' + evidence.caseId);
  for (const action of [{ label: 'Ẩn nội dung', stage: 'hidden', visible: false }, { label: 'Khôi phục nội dung', stage: 'restored', visible: true }]) {
    await review.getByRole('button', { name: action.label, exact: true }).click();
    const dialog = review.getByRole('dialog');
    await dialog.getByRole('textbox', { name: /^Mã lý do/ }).fill(action.visible ? 'REVIEW_RESTORED' : 'SPAM');
    await dialog.getByRole('textbox', { name: 'Ghi chú', exact: true }).fill('Private Admin browser note');
    await dialog.getByRole('button', { name: 'Xác nhận', exact: true }).click();
    await expect(dialog).toHaveCount(0);
    if (!action.visible) await expect(review.getByRole('button', { name: 'Khôi phục nội dung', exact: true })).toBeVisible();
    else await expect(review.getByText('Không có hành động phù hợp với trạng thái hồ sơ hiện tại.', { exact: true })).toBeVisible();
    await publicCheck(action.stage, action.visible);
    await review.screenshot({ path: path.join(fixture.output, action.stage + '-case.png') });
  }
  expect(evidence.writes).toHaveLength(3);
  expect(evidence.writes.every(write => write.status === 200 && write.csrf && write.idempotency && write.method === 'POST')).toBe(true);
  expect(evidence.writes.map(write => write.path)).toEqual([
    '/api/v1/admin/catalog-items/' + fixture.itemId + '/moderation-cases',
    '/api/v1/admin/moderation-cases/' + evidence.caseId + '/hide-content',
    '/api/v1/admin/moderation-cases/' + evidence.caseId + '/restore-content',
  ]);
  expect(evidence.writes[0].result.created).toBe(true);
  await review.goto(origin + '/admin/listings?item=' + fixture.itemId);
  await expect(review.getByRole('link', { name: 'Mở hồ sơ ' + evidence.caseId, exact: true })).toBeVisible();
  expect(evidence.adminDetails.at(-1)).toBe('APPROVED');
  for (const context of contexts) { closing.add(context); await context.close(); }
  expect(evidence.pageErrors).toEqual([]);
} catch (failure) {
  evidence.failure = redact(failure.stack ?? failure);
  process.exitCode = 1;
} finally {
  await browser?.close();
  fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
}
