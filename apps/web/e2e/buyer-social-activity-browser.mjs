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
const commentText = '<script>Buyer literal comment</script>\nActivity chain';
const evidence = { actualNextApiAndIssuedBuyerCookie: true, summaries: [], mutations: [], activity: [], pageErrors: [] };
const redact = value => String(value).replaceAll(fixture.token, '[redacted]');

(async () => {
  expect(fixture.token).not.toBe('');
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
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated buyer fixture' } }));
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
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname === '/api/v1/catalog/social-summary') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        evidence.summaries.push(body[fixture.itemId]);
      }
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method())) {
        evidence.mutations.push({ method: request.method(), path: url.pathname, status: response.status(), csrfHeader: Boolean(headers['x-csrf-token']) });
      }
      if (url.pathname === '/api/v1/me/social/activity') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        expect(response.headers()['cache-control']).toContain('private');
        expect(response.headers()['cache-control']).toContain('no-store');
        expect(url.searchParams.has('userId')).toBe(false);
        expect(body.items).toHaveLength(1);
        expect(body.items[0].itemId).toBe(fixture.itemId);
        if (url.searchParams.get('type') === 'COMMENTED') {
          expect(body.items[0].commentId).toBe(evidence.createdCommentId);
          expect(body.items[0].commentExcerpt).toBe(commentText);
        }
        evidence.activity.push({ type: url.searchParams.get('type'), itemId: body.items[0].itemId, commentId: body.items[0].commentId,
          status: response.status(), cacheControl: response.headers()['cache-control'] });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    const card = page.locator('article').filter({ has: page.getByRole('heading', { name: 'Social browser product', exact: true }) });
    const like = name => card.getByRole('button', { name: name + 'Social browser product', exact: true });
    await expect(like('Thích ')).toBeEnabled();
    expect(evidence.summaries.at(-1)).toMatchObject({ liked: false, saved: false, likes: 0, comments: 0 });
    await like('Thích ').click();
    await expect(like('Bỏ thích ')).toHaveAttribute('aria-pressed', 'true');
    await like('Lưu ').click();
    await expect(like('Bỏ lưu ')).toHaveAttribute('aria-pressed', 'true');
    await card.getByRole('button', { name: 'Bình luận Social browser product', exact: true }).click();
    const thread = card.getByRole('region', { name: 'Bình luận sản phẩm', exact: true });
    await expect(thread).toBeVisible();
    await thread.getByRole('textbox', { name: 'Viết bình luận', exact: true }).fill(commentText);
    const posted = page.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/me/catalog/items/' + fixture.itemId + '/comments');
    await thread.getByRole('button', { name: 'Gửi bình luận', exact: true }).click();
    const response = await posted;
    expect(response.status()).toBe(201);
    const created = await response.json();
    evidence.createdCommentId = created.id;
    expect(created.body).toBe(commentText);
    await expect(thread.getByText('Đã đăng bình luận.', { exact: true })).toBeVisible();
    const visibleComment = thread.getByText(commentText, { exact: true });
    expect(await visibleComment.textContent()).toBe(commentText);
    expect(await visibleComment.locator('script').count()).toBe(0);
    const beforeReload = evidence.summaries.length;
    await page.reload();
    await expect(like('Bỏ thích ')).toHaveAttribute('aria-pressed', 'true');
    await expect(like('Bỏ lưu ')).toHaveAttribute('aria-pressed', 'true');
    expect(evidence.summaries.length).toBeGreaterThan(beforeReload);
    expect(evidence.summaries.at(-1)).toMatchObject({ liked: true, saved: true, likes: 1, comments: 1 });
    evidence.actualReloadConfirmedServerState = true;
    await page.goto(origin + '/me/activity?type=LIKED');
    await expect(page.getByRole('heading', { name: 'Hoạt động của tôi', exact: true })).toBeVisible();
    for (const tab of [{ type: 'LIKED', name: 'Đã thích' }, { type: 'SAVED', name: 'Đã lưu' }, { type: 'COMMENTED', name: 'Bình luận' }]) {
      if (tab.type !== 'LIKED') await page.getByRole('tab', { name: tab.name, exact: true }).click();
      await expect(page).toHaveURL(origin + '/me/activity?type=' + tab.type);
      await expect(page.getByRole('tab', { name: tab.name, exact: true })).toHaveAttribute('aria-selected', 'true');
      const panel = page.getByRole('tabpanel');
      await expect(panel.getByRole('link', { name: 'Social browser product', exact: true })).toHaveAttribute('href', '/items/' + fixture.itemId);
      if (tab.type === 'COMMENTED') {
        const excerpt = panel.getByText(commentText, { exact: true });
        expect(await excerpt.textContent()).toBe(commentText);
        expect(await excerpt.locator('script').count()).toBe(0);
      }
      expect(evidence.activity.some(read => read.type === tab.type)).toBe(true);
    }
    expect(evidence.mutations).toEqual([
      { method: 'PUT', path: '/api/v1/me/catalog/items/' + fixture.itemId + '/likes', status: 200, csrfHeader: true },
      { method: 'PUT', path: '/api/v1/me/catalog/items/' + fixture.itemId + '/saves', status: 200, csrfHeader: true },
      { method: 'POST', path: '/api/v1/me/catalog/items/' + fixture.itemId + '/comments', status: 201, csrfHeader: true },
    ]);
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
