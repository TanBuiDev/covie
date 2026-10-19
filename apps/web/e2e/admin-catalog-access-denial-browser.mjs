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
const evidence = { actualNextApiAndIssuedAdminCookie: true, reads: [], browserWrites: [], pageErrors: [] };
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
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
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
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname.startsWith('/api/v1/')) {
        if (request.method() === 'GET') evidence.reads.push({ path: url.pathname + url.search, status: response.status() });
        else evidence.browserWrites.push({ method: request.method(), path: url.pathname, status: response.status() });
        expect(await response.text()).not.toContain('admin-browser-private-key-');
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/admin/listings?item=' + fixture.itemId);
    await expect(page.getByRole('heading', { name: 'Chi tiết nội dung', exact: true })).toBeVisible();
    await expect(page.locator('table tbody tr')).toHaveCount(20);
    await expect(page.getByText('<script>Literal admin caption</script>\nSecond line', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /^Mở hồ sơ / })).toHaveCount(50);
    await expect(page.getByRole('button', { name: 'Xem thêm hồ sơ', exact: true })).toBeVisible();
    const revoke = await fetch(fixture.apiBase + '/api/v1/auth/logout', {
      method: 'POST', headers: { Authorization: `Bearer ${fixture.token}` },
    });
    expect(revoke.status).toBe(204);
    evidence.sessionRevocation = { endpoint: '/api/v1/auth/logout', status: revoke.status, bearerFixtureCommand: true };
    expect((await context.cookies(origin)).some(cookie => cookie.name === 'covie_at' && cookie.value === fixture.token)).toBe(true);
    await page.getByRole('button', { name: 'Xem thêm hồ sơ', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Phiên đăng nhập đã hết hạn' })).toBeVisible();
    await expect(page.getByRole('table')).toHaveCount(0);
    await expect(page.getByRole('link', { name: /^Mở hồ sơ / })).toHaveCount(0);
    await expect(page.getByText('Separate commerce description', { exact: true })).toHaveCount(0);
    await expect(page.getByText('<script>Literal admin caption</script>\nSecond line', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Xác nhận mở hồ sơ', exact: true })).toHaveCount(0);
    expect(evidence.reads.some(read => read.path.includes('/moderation-cases?') && read.status === 401)).toBe(true);
    expect(evidence.browserWrites).toEqual([]);
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Phiên đăng nhập đã hết hạn' })).toBeVisible();
    await expect(page.getByRole('table')).toHaveCount(0);
    expect(evidence.pageErrors).toEqual([]);
    evidence.oldCookieRetainedButRejected = true;
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
