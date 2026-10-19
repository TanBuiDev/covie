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
const evidence = { actualNextApi: true, commands: [], previews: [], reactions: [], pageErrors: [] };
const redact = value => [fixture.token, fixture.adminToken].filter(Boolean).reduce((text, token) => text.replaceAll(token, '[redacted]'), String(value));

(async () => {
  const deadline = Date.now() + 90000;
  let ready = false;
  while (!ready && Date.now() < deadline) {
    try { ready = (await fetch(target + '/login', { signal: AbortSignal.timeout(3000) })).ok; } catch { ready = false; }
    if (!ready) await new Promise(resolve => setTimeout(resolve, 500));
  }
  if (!ready) throw new Error('Isolated Next did not become ready');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  let releasePreview;
  try {
    const context = await browser.newContext({ viewport: { width: 1280, height: 900 }, locale: 'vi-VN' });
    await context.grantPermissions(['local-network-access'], { origin });
    await context.addCookies([{ name: 'covie_at', value: fixture.token, url: origin, httpOnly: true, sameSite: 'Lax' }]);
    await context.addInitScript(() => localStorage.setItem('covie:browse-location:v1', JSON.stringify({ version: 1,
      location: { kind: 'coordinates', lat: 10.8, lng: 106.7, label: 'Isolated visibility fixture' } })));
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
    let holdPreview = false;
    await context.route(origin + '/**', async route => {
      const request = route.request();
      const url = new URL(request.url());
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname === '/api/v1/catalog/social-previews') {
        const body = await response.json();
        expect(response.status()).toBe(200);
        expect(response.headers()['cache-control']).toContain('no-store');
        evidence.previews.push({ afterHide: holdPreview, ids: Object.keys(body), status: response.status() });
        if (holdPreview) {
          expect(body[fixture.itemId]).toBeUndefined();
          await new Promise(resolve => { releasePreview = resolve; });
        }
      }
      if (url.pathname === `/api/v1/me/catalog/items/${fixture.itemId}/likes`) {
        evidence.reactions.push({ method: request.method(), status: response.status(), code: (await response.json()).error?.code });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/search?type=PRODUCT&sort=NEWEST&view=social');
    const gallery = page.getByRole('region', { name: 'Ảnh Social browser product', exact: true });
    await expect(gallery).toBeVisible();
    await expect(gallery.getByRole('img').first()).toBeVisible();
    const like = page.getByRole('button', { name: 'Thích Social browser product', exact: true });
    await expect(like).toBeEnabled();
    await expect(page.getByText('<script>Literal Social caption</script>', { exact: false })).toBeVisible();
    const report = await fetch(fixture.apiBase + '/api/v1/reports', { method: 'POST', headers: {
      Authorization: `Bearer ${fixture.token}`, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID(),
    }, body: JSON.stringify({ targetType: 'CATALOG_ITEM', targetId: fixture.itemId, reasonCode: 'SPAM' }) });
    expect(report.status).toBe(201);
    const caseId = (await report.json()).caseId;
    evidence.commands.push({ command: 'REPORT', status: report.status, caseId });
    const hideKey = crypto.randomUUID();
    for (let attempt = 0; attempt < 2; attempt++) {
      const hidden = await fetch(`${fixture.apiBase}/api/v1/admin/moderation-cases/${caseId}/hide-content`, { method: 'POST', headers: {
        Authorization: `Bearer ${fixture.adminToken}`, 'Content-Type': 'application/json', 'Idempotency-Key': hideKey,
      }, body: JSON.stringify({ reasonCode: 'SPAM', note: 'Isolated browser visibility proof' }) });
      expect(hidden.status).toBe(200);
      evidence.commands.push({ command: attempt === 0 ? 'HIDE' : 'HIDE_REPLAY', status: hidden.status });
    }
    holdPreview = true;
    await like.click();
    await expect.poll(() => evidence.reactions.length).toBe(1);
    expect(evidence.reactions[0]).toEqual({ method: 'PUT', status: 404, code: 'RESOURCE_NOT_FOUND' });
    await expect.poll(() => typeof releasePreview).toBe('function');
    await expect(gallery).toHaveCount(0);
    await expect(page.getByText('<script>Literal Social caption</script>', { exact: false })).toHaveCount(0);
    await expect(page.getByText('Social browser product', { exact: true })).toHaveCount(0);
    evidence.oldContentAbsentWhileFreshResponseHeld = true;
    releasePreview();
    await expect(page.getByText('Đang tải nội dung bài đăng…', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Social empty product', { exact: true })).toBeVisible();
    await expect(gallery).toHaveCount(0);
    await expect(page).toHaveURL(/\/search\?/);
    await expect(page.getByRole('group', { name: 'Chế độ hiển thị sản phẩm', exact: true })).toBeVisible();
    await page.screenshot({ path: path.join(fixture.output, 'after-hide.png') });
    expect(evidence.previews.filter(preview => preview.afterHide)).toHaveLength(1);
    expect(evidence.pageErrors).toEqual([]);
    evidence.browser = browser.version();
    fs.writeFileSync(path.join(fixture.output, 'evidence.json'), JSON.stringify(evidence, null, 2));
    console.log(JSON.stringify(evidence));
    await context.close();
  } finally { releasePreview?.(); await browser.close(); }
})().catch(error => {
  fs.writeFileSync(path.join(fixture.output, 'failure-evidence.json'), JSON.stringify(evidence, null, 2));
  console.error(redact(error.stack ?? error));
  process.exitCode = 1;
});
