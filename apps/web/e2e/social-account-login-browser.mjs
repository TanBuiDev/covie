import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
const fromWeb = createRequire(new URL('../package.json', import.meta.url));
const { chromium, expect: baseExpect } = fromWeb('@playwright/test');
const expect = baseExpect.configure({ timeout: 20000 });
const WebSocket = fromWeb('next/dist/compiled/ws');
const fixture = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const target = `http://localhost:${fixture.webPort}`;
const origin = 'http://localhost:3000';
const evidence = { realLoginForms: true, manualCookieChanges: false, syntheticFocus: false, auth: [], activity: [], privateAFlashes: [], pageErrors: [], loginDocumentResets: [] };
const redact = value => [fixture.password, fixture.firstToken, fixture.secondToken].filter(Boolean)
  .reduce((text, secret) => text.replaceAll(secret, '[redacted]'), String(value));

(async () => {
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Isolated Next did not become ready');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  let forbidA = false;
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    await context.exposeBinding('observePrivateA', (_source, visible) => {
      if (forbidA && visible) evidence.privateAFlashes.push('visible A activity after logout');
    });
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated account fixture' } }));
      const observe = () => {
        const panel = document.getElementById('activity-panel');
        void window.observePrivateA(Boolean(panel?.getClientRects().length && panel.textContent.includes('A-private-')));
      };
      new MutationObserver(observe).observe(document, { childList: true, subtree: true, characterData: true });
      document.addEventListener('DOMContentLoaded', observe);
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
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (['/api/v1/auth/login', '/api/v1/auth/logout'].includes(url.pathname) && request.method() === 'POST')
        evidence.auth.push({ path: url.pathname, status: response.status(), csrfHeader: Boolean(headers['x-csrf-token']) });
      if (url.pathname === '/api/v1/me/social/activity') {
        const body = await response.json();
        expect(url.searchParams.has('userId')).toBe(false);
        evidence.activity.push({ status: response.status(), actor: body.items?.some(item => item.itemName.startsWith('A-private-')) ? 'A'
          : body.items?.some(item => item.itemName.startsWith('B-private-')) ? 'B' : 'none' });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    const login = async email => {
      await expect(page.getByRole('heading', { name: 'Chào mừng bạn trở lại', exact: true })).toBeVisible();
      await page.getByLabel('Email', { exact: true }).fill(email);
      await page.getByLabel('Mật khẩu', { exact: true }).fill(fixture.password);
      await page.evaluate(() => { window.loginRealmMarker = 'before login'; });
      const submit = page.getByRole('button', { name: 'Đăng nhập', exact: true });
      await expect(submit).toBeEnabled();
      await submit.click();
      await expect(page).not.toHaveURL(/\/login(?:\?|$)/);
      expect(await page.evaluate(() => window.loginRealmMarker ?? null)).toBeNull();
      evidence.loginDocumentResets.push(true);
    };
    await page.goto(origin + '/login?returnTo=%2Fme%2Factivity%3Ftype%3DLIKED');
    await login(fixture.firstEmail);
    await expect(page.getByText('A-private-0', { exact: true })).toBeVisible();
    const authCookies = (await context.cookies()).filter(cookie => ['covie_at', 'covie_rt'].includes(cookie.name));
    expect(authCookies).toHaveLength(2);
    expect(authCookies.every(cookie => cookie.httpOnly)).toBe(true);
    evidence.httpOnlyCookiesFromLogin = true;
    await page.getByRole('link', { name: 'Quay lại hồ sơ', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Browser buyer A', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
    await expect(page).toHaveURL(origin + '/login');
    forbidA = true;
    await page.goBack();
    await expect.poll(async () => new URL(page.url()).pathname === '/login'
      || await page.getByRole('heading', { name: 'Vui lòng đăng nhập lại', exact: true }).isVisible()).toBe(true);
    if (new URL(page.url()).pathname !== '/login') await page.getByRole('link', { name: 'Đăng nhập lại', exact: true }).click();
    await login(fixture.secondEmail);
    if (new URL(page.url()).pathname !== '/me/activity') {
      await page.locator('a[href="/me"]').first().click();
      await expect(page.getByRole('heading', { name: 'Browser buyer B', exact: true })).toBeVisible();
      await page.getByRole('link', { name: /Hoạt động của tôi/ }).click();
    }
    await expect(page.getByRole('heading', { name: 'Hoạt động của tôi', exact: true })).toBeVisible();
    await expect(page.getByText('B-private-21', { exact: true })).toBeVisible();
    await expect(page.locator('#activity-panel').getByText('A-private-', { exact: false })).toHaveCount(0);
    await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
    expect(evidence.privateAFlashes).toEqual([]);
    expect(evidence.auth).toEqual([
      { path: '/api/v1/auth/login', status: 200, csrfHeader: true },
      { path: '/api/v1/auth/logout', status: 204, csrfHeader: true },
      { path: '/api/v1/auth/login', status: 200, csrfHeader: true },
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
