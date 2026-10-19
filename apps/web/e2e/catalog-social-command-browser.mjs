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
const evidence = { actualNextApiAndIssuedCookie: true, dialogs: [], writes: [], pageErrors: [] };
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
      const isWrite = url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method());
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (isWrite) evidence.writes.push({ method: request.method(), path: url.pathname, body: request.postDataJSON(), status: response.status() });
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    const caption = page.getByRole('textbox', { name: 'Caption', exact: true });
    const price = page.getByLabel('Giá tại Navigation counter', { exact: true });
    const save = page.getByRole('button', { name: 'Lưu Navigation counter', exact: true });
    const dirtyCaption = page.getByText('Caption chưa lưu.', { exact: true });
    const dirtyGallery = page.getByText('Gallery chưa lưu.', { exact: true });
    const confirm = async allow => {
      const dialogEvent = page.waitForEvent('dialog');
      const click = save.click();
      const dialog = await dialogEvent;
      expect(dialog.type()).toBe('confirm');
      evidence.dialogs.push({ type: dialog.type(), allowed: allow });
      if (allow) await dialog.accept(); else await dialog.dismiss();
      await click;
    };
    await page.goto(`${origin}/seller/${fixture.merchantId}/catalog/${fixture.itemId}`);
    await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
    await expect(caption).toHaveValue('Persisted caption');
    await caption.fill('Unsaved command caption\nLiteral <b>draft</b>');
    await page.getByLabel('Ảnh Social', { exact: true }).setInputFiles(path.join(fixture.output, 'pending.png'));
    await price.fill('31000');
    await expect(dirtyCaption).toBeVisible();
    await expect(dirtyGallery).toBeVisible();
    await confirm(false);
    await expect(caption).toHaveValue('Unsaved command caption\nLiteral <b>draft</b>');
    await expect(dirtyGallery).toBeVisible();
    await expect(price).toHaveValue('31000');
    expect(evidence.writes).toEqual([]);
    evidence.cancelRetainedDraftAndNoWrite = true;
    await page.screenshot({ path: path.join(fixture.output, 'cancel-retains-draft.png') });
    await confirm(true);
    await expect.poll(() => evidence.writes.length).toBe(1);
    await expect(caption).toHaveValue('Persisted caption');
    await expect(dirtyCaption).toHaveCount(0);
    await expect(dirtyGallery).toHaveCount(0);
    await expect(price).toHaveValue('31000');
    expect(evidence.writes[0].method).toBe('PATCH');
    expect(evidence.writes[0].path).toMatch(/\/location-offers\/[^/]+$/);
    expect(evidence.writes[0].status).toBe(204);
    expect(evidence.writes[0].body.price).toBe(31000);
    expect(evidence.writes[0].body).not.toHaveProperty('socialCaption');
    expect(evidence.writes[0].body).not.toHaveProperty('mediaIds');
    evidence.acceptRefreshedAuthoritativeDraft = true;
    await page.screenshot({ path: path.join(fixture.output, 'accept-authoritative-refresh.png') });
    await page.reload();
    await expect(page.getByRole('group', { name: 'Thuộc tính danh mục', exact: true })).toBeVisible();
    await expect(price).toHaveValue('31000');
    await expect(caption).toHaveValue('Persisted caption');
    expect(evidence.writes).toHaveLength(1);
    expect(evidence.pageErrors).toEqual([]);
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    await context.close();
  } finally { await browser.close(); }
})().catch(error => { console.error(redact(error.stack ?? error)); process.exitCode = 1; });
