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
const catalog = `/seller/${fixture.merchantId}/catalog`;
const edit = catalog + '/' + fixture.itemId;
const evidence = { actualNextApiAndIssuedCookie: true, syntheticNavigationEvents: false, dialogs: [], writes: [], pageErrors: [] };
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
  let page;
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
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) evidence.writes.push({ method: request.method(), path: url.pathname });
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      return route.fulfill({ response }).catch(() => undefined);
    });
    page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.addInitScript(() => {
      window.navigationObservations = [];
      window.navigation?.addEventListener('navigate', event => {
        const row = { type: event.navigationType, trusted: event.isTrusted, cancelable: event.cancelable, sameDocument: event.destination.sameDocument };
        window.navigationObservations.push(row);
        setTimeout(() => { row.cancelled = event.defaultPrevented; }, 0);
      });
    });
    const decide = async (type, allow, action) => {
      const pendingDialog = page.waitForEvent('dialog');
      const pendingAction = action();
      const dialog = await pendingDialog;
      evidence.dialogs.push({ type: dialog.type(), allowed: allow });
      expect(dialog.type()).toBe(type);
      if (allow) await dialog.accept(); else await dialog.dismiss();
      await pendingAction;
    };
    const caption = () => page.getByRole('textbox', { name: 'Caption', exact: true });
    await page.goto(origin + catalog);
    await page.locator(`a[href="${edit}"]`).first().click();
    await expect(page).toHaveURL(origin + edit);
    await expect(page.getByRole('heading', { name: 'Navigation item', level: 1, exact: true })).toBeVisible();
    await expect(caption()).toHaveValue('Persisted caption');
    await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
    await caption().fill('Unsaved caption\nSecond line');
    await page.getByLabel('Ảnh Social', { exact: true }).setInputFiles(path.join(fixture.output, 'pending.png'));
    await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toBeVisible();
    await page.setViewportSize({ width: 360, height: 900 });
    const group = page.getByRole('group', { name: 'Nội dung hiển thị trên Khám phá', exact: true });
    await group.evaluate(element => { element.scrollIntoView({ block: 'start' }); window.scrollBy(0, -100); });
    await expect(page).toHaveURL(origin + edit);
    await expect(group.locator('legend')).toBeInViewport();
    await expect(caption()).toBeInViewport();
    await page.screenshot({ path: path.join(fixture.output, 'edit-caption-360.png') });
    await page.setViewportSize({ width: 1280, height: 900 });
    const backLink = () => page.getByRole('link', { name: 'Quay lại danh mục', exact: true });
    await decide('confirm', false, () => backLink().click());
    await expect(page).toHaveURL(origin + edit);
    await expect(caption()).toHaveValue('Unsaved caption\nSecond line');
    await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toBeVisible();
    await decide('confirm', false, () => page.evaluate(() => window.history.back()));
    await expect(page).toHaveURL(origin + edit);
    await expect(caption()).toHaveValue('Unsaved caption\nSecond line');
    await decide('confirm', true, () => page.evaluate(() => window.history.back()));
    await expect(page).toHaveURL(origin + catalog);
    await expect(page.locator(`a[href="${edit}"]`).first()).toBeVisible();
    await page.evaluate(() => window.history.forward());
    await expect(page).toHaveURL(origin + edit);
    await expect(caption()).toHaveValue('Persisted caption');
    await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toHaveCount(0);
    await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
    evidence.traversals = await page.evaluate(() => window.navigationObservations.filter(event => event.type === 'traverse'));
    expect(evidence.traversals.some(event => event.trusted && event.cancelable && event.sameDocument && event.cancelled)).toBe(true);
    await caption().fill('Reload draft');
    await decide('beforeunload', false, () => page.evaluate(() => { setTimeout(() => window.location.reload(), 0); }));
    await expect(caption()).toHaveValue('Reload draft');
    const reloaded = page.waitForEvent('domcontentloaded');
    await decide('beforeunload', true, () => page.evaluate(() => { setTimeout(() => window.location.reload(), 0); }));
    await reloaded;
    await expect(caption()).toHaveValue('Persisted caption');
    await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
    await caption().fill('Temporary caption');
    await page.getByLabel('Ảnh Social', { exact: true }).setInputFiles(path.join(fixture.output, 'pending.png'));
    await page.getByRole('button', { name: 'Hoàn nguyên caption', exact: true }).click();
    await expect(caption()).toHaveValue('Persisted caption');
    await decide('confirm', false, () => backLink().click());
    await expect(page).toHaveURL(origin + edit);
    await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Xóa pending.png', exact: true }).click();
    await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toHaveCount(0);
    await backLink().click();
    await expect(page).toHaveURL(origin + catalog);
    expect(evidence.dialogs).toHaveLength(6);
    expect(evidence.writes).toEqual([]);
    evidence.browser = browser.version();
    evidence.result = 'PASS: real Next links, trusted cancellable Back/Forward, native reload beforeunload, multiple dirty sources/cleanup, no writes';
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
  } catch (error) {
    if (page) evidence.bodyAtFailure = await page.locator('body').innerText().catch(() => 'unavailable');
    console.log(redact(JSON.stringify(evidence)));
    throw error;
  } finally { await browser.close(); }
})().catch(error => { console.error(redact(error.stack ?? error)); process.exitCode = 1; });
