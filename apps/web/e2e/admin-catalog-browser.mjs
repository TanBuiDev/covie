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
const evidence = { actualNextApiAndIssuedAdminCookie: true, reads: [], writes: [], viewports: [], pageErrors: [] };
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
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (url.pathname.startsWith('/api/v1/admin/')) {
        const body = await response.json().catch(() => null);
        expect(JSON.stringify(body)).not.toContain('admin-browser-private-key-');
        if (request.method() === 'GET') evidence.reads.push({ path: url.pathname + url.search, status: response.status(), items: body?.items?.length });
        else evidence.writes.push({ method: request.method(), path: url.pathname, body: request.postDataJSON(), status: response.status(), result: body, idempotencyKeyPresent: Boolean((await request.allHeaders())['idempotency-key']) });
      }
      return route.fulfill({ response }).catch(() => undefined);
    });
    const page = await context.newPage();
    page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
    const tableRows = page.locator('table tbody tr');
    const captionText = '<script>Literal admin caption</script>\nSecond line';
    await page.goto(origin + '/admin/listings');
    await expect(tableRows).toHaveCount(20);
    await expect(page.getByText('Có caption', { exact: true })).toHaveCount(1);
    await expect(page.getByText('Chưa có caption', { exact: true })).toHaveCount(19);
    await page.getByRole('button', { name: 'Xem thêm', exact: true }).click();
    await expect(tableRows).toHaveCount(21);
    await expect(page.getByText('Admin browser item 1', { exact: true })).toBeVisible();
    expect(await tableRows.locator('strong').allTextContents()).toEqual(expect.arrayContaining(['Admin browser item 1', 'Admin browser item 21']));
    expect(new Set(await tableRows.locator('strong').allTextContents()).size).toBe(21);
    evidence.appendRetainedFirstPage = true;
    const capture = async stage => {
      for (const width of [360, 768, 1280, 1440]) {
        await page.setViewportSize({ width, height: 900 });
        if (stage === 'detail') await page.getByRole('heading', { name: 'Nội dung hiển thị trên Khám phá', exact: true }).scrollIntoViewIfNeeded();
        else await page.evaluate(() => window.scrollTo(0, 0));
        const documentWidth = await page.evaluate(() => document.documentElement.scrollWidth);
        const tableWidth = await page.locator('table').evaluate(table => table.getBoundingClientRect().width);
        const buttonTextLines = stage === 'list' ? await tableRows.first().getByRole('button').evaluate(button => {
          const range = document.createRange();
          range.selectNodeContents(button);
          return new Set([...range.getClientRects()].map(rect => Math.round(rect.top))).size;
        }) : undefined;
        evidence.viewports.push({ stage, width, documentWidth, tableWidth, buttonTextLines });
        expect(documentWidth).toBe(width);
        if (width < 768) expect(tableWidth, 'narrow Admin table must scroll horizontally rather than compress columns into letters').toBeGreaterThanOrEqual(760);
        if (stage === 'list') expect(buttonTextLines, 'short detail action must remain readable on one line').toBe(1);
        await page.screenshot({ path: path.join(fixture.output, `${stage}-${width}.png`) });
      }
    };
    await capture('list');
    await page.getByLabel('Tìm nội dung', { exact: true }).fill('nothing-matches-' + fixture.itemId);
    await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
    await expect(page.getByText('Không có nội dung phù hợp.', { exact: true })).toBeVisible();
    evidence.actualEmptyFilter = true;
    await page.getByLabel('Tìm nội dung', { exact: true }).fill(fixture.itemId);
    await page.getByLabel('Mã cửa hàng', { exact: true }).fill(fixture.merchantId);
    await page.locator('select[name="type"]').selectOption('PRODUCT');
    await page.locator('select[name="listingKind"]').selectOption('STANDARD');
    await page.locator('select[name="listingStatus"]').selectOption('ACTIVE');
    await page.locator('select[name="moderationStatus"]').selectOption('APPROVED');
    await page.locator('select[name="sort"]').selectOption('CREATED_ASC');
    await page.getByRole('button', { name: 'Áp dụng', exact: true }).click();
    await expect(tableRows).toHaveCount(1);
    expect(new URL(page.url()).searchParams.get('merchantId')).toBe(fixture.merchantId);
    await page.setViewportSize({ width: 360, height: 900 });
    await page.getByRole('button', { name: 'Xem Admin browser item 1', exact: true }).click();
    evidence.mobileTableActionReachable = true;
    await expect(page.getByText('Separate commerce description', { exact: true })).toBeVisible();
    const caption = page.locator('p').filter({ hasText: '<script>Literal admin caption</script>' });
    await expect(caption).toHaveText(captionText);
    expect(await caption.textContent()).toBe(captionText);
    expect(await caption.locator('script').count()).toBe(0);
    await expect(page.getByText('Chưa có hồ sơ kiểm duyệt cho nội dung này.', { exact: true })).toBeVisible();
    await expect(page.getByText('image/png · Chưa sẵn sàng', { exact: true })).toBeVisible();
    evidence.literalCaptionAndPendingMedia = true;
    await capture('detail');
    await page.getByLabel('Mã lý do', { exact: true }).fill('SPAM');
    await page.getByLabel('Ghi chú', { exact: true }).fill('Browser observed content');
    await expect(page.getByRole('button', { name: 'Xác nhận mở hồ sơ', exact: true })).toBeDisabled();
    await page.getByRole('checkbox', { name: 'Tôi xác nhận mở hồ sơ; thao tác này không đổi trạng thái nội dung.', exact: true }).check();
    await page.getByRole('button', { name: 'Xác nhận mở hồ sơ', exact: true }).click();
    await expect.poll(() => evidence.writes.length).toBe(1);
    const opening = evidence.writes[0];
    expect(opening.status).toBe(200);
    expect(opening.idempotencyKeyPresent).toBe(true);
    expect(opening.result.created).toBe(true);
    const caseId = opening.result.caseId;
    await expect(page).toHaveURL(origin + '/admin/moderation/' + caseId);
    await expect(page.getByText(fixture.itemId, { exact: true })).toBeVisible();
    await expect(page.getByText('SPAM', { exact: true })).toBeVisible();
    evidence.actualCaseDestination = caseId;
    await page.goto(origin + '/admin/listings?item=' + fixture.itemId);
    const caseLink = page.getByRole('link', { name: 'Mở hồ sơ ' + caseId, exact: true });
    await expect(caseLink).toHaveAttribute('href', '/admin/moderation/' + caseId);
    await caseLink.click();
    await expect(page).toHaveURL(origin + '/admin/moderation/' + caseId);
    await expect(page.getByText(fixture.itemId, { exact: true })).toBeVisible();
    expect(evidence.writes).toHaveLength(1);
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
