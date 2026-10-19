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
const evidence = { actualNextApiAndIssuedCookie: true, delayedRealJavaScript: true, forms: [], writes: [], pageErrors: [] };
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
  let release;
  try {
    for (const mode of ['edit', 'create']) {
      let holding = true;
      const pending = new Promise(resolve => { release = resolve; });
      const row = { mode, heldScripts: 0 };
      const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
      await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
      await context.routeWebSocket('**/*', socket => {
        const url = new URL(socket.url());
        const upstream = new WebSocket(target.replace('http:', 'ws:') + url.pathname + url.search);
        const queued = [];
        socket.onMessage(message => { if (upstream.readyState === WebSocket.OPEN) upstream.send(message); else queued.push(message); });
        upstream.on('open', () => { for (const message of queued) upstream.send(message); });
        upstream.on('message', (message, binary) => socket.send(binary ? message : message.toString()));
        upstream.on('error', error => { row.socketError = redact(error.message); });
        upstream.on('close', () => socket.close());
        socket.onClose(() => upstream.close());
      });
      await context.route(origin + '/**', async route => {
        const request = route.request();
        const url = new URL(request.url());
        if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) evidence.writes.push({ method: request.method(), path: url.pathname });
        const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
        if (holding && url.pathname.startsWith('/_next/') && url.pathname.endsWith('.js')) { row.heldScripts++; await pending; }
        return route.fulfill({ response }).catch(() => undefined);
      });
      const page = await context.newPage();
      page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
      await page.addInitScript(() => { window.earlyInputEvents = []; document.addEventListener('input', event => { if (event.target.name === 'socialCaption') window.earlyInputEvents.push({ trusted: event.isTrusted }); }, true); });
      const url = `${origin}/seller/${fixture.merchantId}/catalog/${mode === 'edit' ? fixture.itemId : 'new'}`;
      await page.goto(url, { waitUntil: 'commit' });
      const caption = page.locator('textarea[name="socialCaption"]');
      await expect(caption).toBeVisible();
      await expect.poll(() => row.heldScripts).toBeGreaterThan(0);
      row.blockedBeforeHydration = await caption.evaluate(element => element.disabled || element.readOnly || Boolean(element.closest('[inert]')) || Boolean(element.closest('fieldset:disabled')));
      row.submitDisabledBeforeHydration = await page.getByRole('button', { name: mode === 'edit' ? 'Lưu thông tin' : 'Tạo nội dung', exact: true }).isDisabled();
      row.galleryDisabledBeforeHydration = await page.getByLabel('Ảnh Social', { exact: true }).isDisabled();
      row.beforeInput = await caption.inputValue();
      const early = `Early ${mode} draft\nSecond line`;
      if (!row.blockedBeforeHydration) await caption.fill(early);
      row.beforeRelease = await caption.inputValue();
      row.earlyInputEvents = await page.evaluate(() => window.earlyInputEvents);
      holding = false;
      release();
      if (mode === 'edit') {
        await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
      } else {
        const category = page.getByRole('combobox', { name: 'Danh mục', exact: true });
        await expect(async () => {
          if (await category.getAttribute('aria-expanded') === 'false') await category.click();
          await expect(page.getByRole('option', { name: 'Navigation category', exact: true })).toBeVisible({ timeout: 1000 });
        }).toPass({ timeout: 20000 });
        await page.getByRole('option', { name: 'Navigation category', exact: true }).click();
      }
      row.afterHydration = await caption.inputValue();
      row.counterAfterHydration = await page.getByLabel('Số ký tự caption').textContent();
      row.dirtyAfterHydration = await page.getByText('Caption chưa lưu.', { exact: true }).count();
      await caption.fill(`Hydrated ${mode} draft`);
      await expect(page.getByText('Caption chưa lưu.', { exact: true })).toBeVisible();
      await page.getByLabel('Ảnh Social', { exact: true }).setInputFiles(path.join(fixture.output, 'pending.png'));
      await expect(page.getByText('Gallery chưa lưu.', { exact: true })).toBeVisible();
      row.usableAfterHydration = true;
      evidence.forms.push(row);
      await context.close();
    }
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    for (const row of evidence.forms) {
      if (!row.blockedBeforeHydration) {
        expect(row.afterHydration, `${row.mode}: accepted early input must not disappear`).toBe(`Early ${row.mode} draft\nSecond line`);
        expect(row.dirtyAfterHydration).toBe(1);
      } else {
        expect(row.afterHydration).toBe(row.beforeInput);
        expect(row.galleryDisabledBeforeHydration).toBe(true);
        expect(row.submitDisabledBeforeHydration).toBe(true);
      }
    }
    expect(evidence.writes).toEqual([]);
  } finally { release?.(); await browser.close(); }
})().catch(error => { console.error(redact(error.stack ?? error)); process.exitCode = 1; });
