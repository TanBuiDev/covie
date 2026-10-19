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
const evidence = { actualNextApiAndIssuedAdminCookie: true, reads: [], browserWrites: [], fixtureCommands: [], pageErrors: [] };
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
      const headers = await request.allHeaders();
      const response = await route.fetch({ url: target + url.pathname + url.search,
        headers: { ...headers, host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname.startsWith('/api/v1/admin/')) {
        const body = await response.json();
        expect(JSON.stringify(body)).not.toContain('admin-browser-private-key-');
        expect(response.headers()['cache-control']).toContain('no-store');
        if (request.method() === 'GET') evidence.reads.push({ path: url.pathname + url.search, status: response.status(),
          moderationStatus: body.item?.moderationStatus, caseStatus: body.moderationCases?.[0]?.status });
        else evidence.browserWrites.push({ path: url.pathname, status: response.status(), result: body,
          csrfHeader: Boolean(headers['x-csrf-token']), idempotencyKeyPresent: Boolean(headers['idempotency-key']) });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    await page.goto(origin + '/admin/listings?item=' + fixture.itemId);
    await expect(page.getByRole('heading', { name: 'Chi tiết nội dung', exact: true })).toBeVisible();
    await expect(page.getByText('Chưa có hồ sơ kiểm duyệt cho nội dung này.', { exact: true })).toBeVisible();
    const itemRow = page.getByRole('row').filter({ hasText: fixture.itemId });
    await expect(itemRow.getByText('Đã duyệt', { exact: true })).toBeVisible();
    await page.getByRole('textbox', { name: 'Mã lý do', exact: true }).fill('SPAM');
    await page.getByRole('checkbox').check();
    const command = async (route, body) => {
      const response = await fetch(fixture.apiBase + route, { method: 'POST',
        headers: { Authorization: `Bearer ${fixture.token}`, 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify(body) });
      const result = await response.json();
      evidence.fixtureCommands.push({ path: route, status: response.status });
      expect(response.status).toBe(200);
      return result;
    };
    const opened = await command('/api/v1/admin/catalog-items/' + fixture.itemId + '/moderation-cases',
      { reasonCode: 'SPAM', note: 'Concurrent Admin observed content' });
    expect(opened.created).toBe(true);
    await command('/api/v1/admin/moderation-cases/' + opened.caseId + '/hide-content', { reasonCode: 'SPAM' });
    await expect(itemRow.getByText('Đã duyệt', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: /^Mở hồ sơ / })).toHaveCount(0);
    evidence.staleDetailRemainedUntilCommand = true;
    await page.getByRole('button', { name: 'Xác nhận mở hồ sơ', exact: true }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Đã yêu cầu tải lại trạng thái từ máy chủ.' })).toBeVisible();
    await expect(itemRow.getByText('Đã ẩn', { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Mở hồ sơ ' + opened.caseId, exact: true })).toHaveCount(1);
    await expect(page.getByRole('listitem').filter({ hasText: opened.caseId }).getByText('Đã xử lý', { exact: true })).toBeVisible();
    await expect(page.getByRole('textbox', { name: 'Mã lý do', exact: true })).toHaveValue('');
    await expect(page).toHaveURL(origin + '/admin/listings?item=' + fixture.itemId);
    expect(evidence.browserWrites).toHaveLength(1);
    expect(evidence.browserWrites[0]).toMatchObject({ status: 409, csrfHeader: true, idempotencyKeyPresent: true });
    expect(evidence.browserWrites[0].result.error.code).toBe('MODERATION_TARGET_STATE_CONFLICT');
    expect(evidence.reads.some(read => read.moderationStatus === 'APPROVED')).toBe(true);
    expect(evidence.reads.some(read => read.moderationStatus === 'HIDDEN' && read.caseStatus === 'RESOLVED')).toBe(true);
    expect(evidence.pageErrors).toEqual([]);
    evidence.returnedCaseId = opened.caseId;
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
