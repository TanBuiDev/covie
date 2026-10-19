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
const evidence = { actualNextApi: true, syntheticFocus: true, oldSummaries: [], mutations: [], pageErrors: [] };
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
  let releaseLike;
  const likeGate = new Promise(resolve => { releaseLike = resolve; });
  const releases = [];
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => {
      localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
        location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated overlap fixture' } }));
      window.confirmedReactions = [];
      window.addEventListener('covie:catalog-social-updated', event => {
        window.confirmedReactions.push({ itemId: event.detail.itemId, liked: event.detail.liked, likes: event.detail.likes });
      });
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
    let likeHeld = false, overlap = false;
    await context.route(origin + '/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const isLike = url.pathname === `/api/v1/me/catalog/items/${fixture.itemId}/likes` && request.method() === 'PUT';
      if (isLike) { likeHeld = true; await likeGate; }
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (overlap && url.pathname === '/api/v1/catalog/social-summary') {
        const summary = (await response.json())[fixture.itemId];
        expect(summary.liked).toBe(false);
        expect(summary.likes).toBe(0);
        evidence.oldSummaries.push({ status: response.status(), liked: summary.liked, likes: summary.likes });
        await new Promise(resolve => releases.push(resolve));
      }
      if (isLike) {
        const summary = await response.json();
        evidence.mutations.push({ status: response.status(), liked: summary.liked, likes: summary.likes });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    await expect(page.getByRole('region', { name: 'Ảnh Social browser product', exact: true })).toBeVisible();
    const like = page.getByRole('button', { name: 'Thích Social browser product', exact: true });
    await expect(like).toBeEnabled();
    await like.click();
    await expect.poll(() => likeHeld).toBe(true);
    overlap = true;
    await page.evaluate(() => window.dispatchEvent(new Event('focus')));
    await expect.poll(() => releases.length).toBeGreaterThan(0);
    releaseLike();
    await expect.poll(async () => page.evaluate(itemId => window.confirmedReactions.some(summary =>
      summary.itemId === itemId && summary.liked && summary.likes === 1), fixture.itemId)).toBe(true);
    evidence.confirmationObservedBeforeReadRelease = true;
    for (const release of releases) release();
    const confirmed = page.getByRole('button', { name: 'Bỏ thích Social browser product', exact: true });
    await expect(confirmed).toBeEnabled();
    await expect(confirmed).toHaveAttribute('aria-pressed', 'true');
    await expect(confirmed).toHaveText('1');
    expect(evidence.mutations).toEqual([{ status: 200, liked: true, likes: 1 }]);
    expect(evidence.pageErrors).toEqual([]);
    evidence.finalLiked = true;
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    await context.close();
  } finally { releaseLike(); for (const release of releases) release(); await browser.close(); }
})().catch(error => {
  fs.writeFileSync(path.join(fixture.output, 'failure-evidence.json'), JSON.stringify(evidence, null, 2));
  console.error(redact(error.stack ?? error));
  process.exitCode = 1;
});
