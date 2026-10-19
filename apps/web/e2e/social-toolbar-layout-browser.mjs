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
const evidence = { actualNextApi: true, measurements: [], writes: [], pageErrors: [] };
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
    const context = await browser.newContext({ viewport: { width: 360, height: 900 }, isMobile: true, hasTouch: true, locale: 'vi-VN' });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated toolbar fixture' } })));
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
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    await expect(page.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toBeVisible();
    const toggle = page.getByRole('group', { name: 'Chế độ hiển thị sản phẩm', exact: true });
    const toolbar = toggle.locator('..');
    const label = toolbar.getByText('Sắp xếp', { exact: true });
    for (const mode of ['social', 'catalog']) {
      const modeButton = toggle.getByRole('button', { name: mode === 'social' ? 'Khám phá' : 'Mua sắm nhanh', exact: true });
      await modeButton.click();
      await expect(modeButton).toHaveAttribute('aria-pressed', 'true');
      for (const width of [360, 768, 1280, 1440]) {
        await page.setViewportSize({ width, height: 900 });
        await toolbar.evaluate(element => window.scrollTo({ top: Math.max(0, window.scrollY + element.getBoundingClientRect().top - 100), behavior: 'instant' }));
        await page.evaluate(() => new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve))));
        const text = await label.evaluate(element => {
          const words = [...element.textContent.matchAll(/\S+/gu)].map(match => {
            const range = document.createRange();
            range.setStart(element.firstChild, match.index);
            range.setEnd(element.firstChild, match.index + match[0].length);
            return { word: match[0], lines: new Set([...range.getClientRects()].map(rect => Math.round(rect.top))).size };
          });
          return { words, width: element.getBoundingClientRect().width, height: element.getBoundingClientRect().height };
        });
        const bounds = await toolbar.boundingBox();
        const controls = await toolbar.locator('button').evaluateAll(buttons => buttons.map(button => {
          const box = button.getBoundingClientRect();
          return { text: button.textContent.trim(), x: box.x, right: box.right, y: box.y, bottom: box.bottom };
        }));
        const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
        const clientWidth = await page.evaluate(() => document.documentElement.clientWidth);
        evidence.measurements.push({ mode, width, documentWidth, clientWidth, label: text, toolbar: bounds, controls });
        await toolbar.screenshot({ path: path.join(fixture.output, `toolbar-${mode}-${width}.png`) });
      }
    }
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    for (const row of evidence.measurements) {
      for (const word of row.label.words) expect(word.lines, `${row.mode} ${row.width}: ${word.word} must not split into letters`).toBe(1);
      expect(row.documentWidth).toBeLessThanOrEqual(row.clientWidth);
      for (const control of row.controls) {
        expect(control.x).toBeGreaterThanOrEqual(row.toolbar.x - 1);
        expect(control.right).toBeLessThanOrEqual(row.toolbar.x + row.toolbar.width + 1);
      }
    }
    expect(evidence.writes).toEqual([]);
    expect(evidence.pageErrors).toEqual([]);
    await context.close();
  } finally { await browser.close(); }
})().catch(error => {
  fs.writeFileSync(path.join(fixture.output, 'failure-evidence.json'), JSON.stringify(evidence, null, 2));
  console.error(redact(error.stack ?? error));
  process.exitCode = 1;
});
