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
const evidence = { actualNextApiIssuedCookie: true, commentReads: [], writes: [], pageErrors: [], viewports: [] };
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
      if (url.pathname.startsWith('/api/v1/') && ['POST', 'PATCH', 'PUT', 'DELETE'].includes(request.method())) evidence.writes.push({ method: request.method(), path: url.pathname });
      const response = await route.fetch({ url: target + url.pathname + url.search, headers: { ...await request.allHeaders(), host: 'localhost:3000' }, timeout: 60000 });
      if (/\/api\/v1\/catalog\/items\/[^/]+\/comments$/.test(url.pathname)) evidence.commentReads.push({ path: url.pathname, status: response.status() });
      return route.fulfill({ response }).catch(() => undefined);
    });
    for (const hidden of [true, false]) {
      const page = await context.newPage();
      page.on('pageerror', error => evidence.pageErrors.push(redact(error.message)));
      const itemId = hidden ? fixture.itemId : fixture.publicItemId;
      const edit = `/seller/${fixture.merchantId}/catalog/${itemId}`;
      const readsBeforeOpen = evidence.commentReads.length;
      await page.goto(origin + edit);
      await expect(page).toHaveURL(origin + edit);
      await expect(page.getByRole('heading', { level: 1, name: hidden ? 'Navigation item' : 'Public feedback item', exact: true })).toBeVisible();
      const caption = page.getByRole('textbox', { name: 'Caption', exact: true });
      await expect(caption).toBeEnabled();
      expect(evidence.commentReads).toHaveLength(readsBeforeOpen);
      const reason = page.getByRole('note', { name: 'Lý do kiểm duyệt', exact: true });
      if (hidden) {
        await expect(reason).toContainText(fixture.reason);
        await expect(reason.locator('script')).toHaveCount(0);
        await expect(page.getByText('ADMIN_NOTE_PRIVATE', { exact: false })).toHaveCount(0);
      } else await expect(reason).toHaveCount(0);
      const draft = hidden ? 'Unsaved hidden caption' : 'Unsaved public caption';
      await caption.fill(draft);
      const trigger = page.getByRole('button', { name: 'Xem bình luận', exact: true });
      await trigger.click();
      const thread = page.getByRole('region', { name: 'Bình luận sản phẩm', exact: true });
      if (hidden) await expect(thread.getByRole('alert')).toContainText('Không tải được bình luận.');
      else {
        await expect(thread.getByText('<script>window.SELLER_COMMENT_PROBE=1</script>', { exact: true })).toBeVisible();
        await expect(thread.locator('script')).toHaveCount(0);
      }
      await expect(thread.getByRole('button', { name: 'Đóng', exact: true })).toBeFocused();
      const panel = page.getByRole('region', { name: 'Phản hồi về tin đăng', exact: true });
      for (const width of [360, 768, 1280, 1440]) {
        await page.setViewportSize({ width, height: 900 });
        const anchor = hidden ? reason : panel;
        await anchor.evaluate(element => window.scrollTo({ top: Math.max(0, window.scrollY + element.getBoundingClientRect().top - 100), behavior: 'instant' }));
        const bounds = await panel.evaluate(element => {
          const button = element.querySelector(':scope > button');
          const title = element.firstElementChild;
          const buttonStyle = getComputedStyle(button);
          const titleStyle = getComputedStyle(title);
          const range = document.createRange();
          range.selectNodeContents(button);
          const verticalChrome = style => ['paddingTop', 'paddingBottom', 'borderTopWidth', 'borderBottomWidth']
            .reduce((sum, property) => sum + (parseFloat(style[property]) || 0), 0);
          return { left: element.getBoundingClientRect().left, right: element.getBoundingClientRect().right,
            documentWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth,
            buttonHeight: button.getBoundingClientRect().height,
            naturalButtonHeight: Math.max(parseFloat(buttonStyle.minHeight) || 0,
              (parseFloat(buttonStyle.lineHeight) || range.getBoundingClientRect().height) + verticalChrome(buttonStyle)),
            titleHeight: title.getBoundingClientRect().height,
            naturalTitleHeight: Math.max(...Array.from(title.children).map(child => child.getBoundingClientRect().height)) + verticalChrome(titleStyle) };
        });
        evidence.viewports.push({ hidden, width, ...bounds });
        expect(bounds.left).toBeGreaterThanOrEqual(0);
        expect(bounds.right).toBeLessThanOrEqual(bounds.clientWidth);
        expect(bounds.documentWidth).toBeLessThanOrEqual(bounds.clientWidth);
        expect(bounds.buttonHeight).toBeLessThanOrEqual(bounds.naturalButtonHeight + 1);
        expect(bounds.titleHeight).toBeLessThanOrEqual(bounds.naturalTitleHeight + 1);
        await expect(caption).toHaveValue(draft);
        await page.screenshot({ path: path.join(fixture.output, `${hidden ? 'hidden' : 'public'}-${width}.png`) });
      }
      await thread.press('Escape');
      await expect(thread).toHaveCount(0);
      await expect(trigger).toBeFocused();
      await expect(caption).toHaveValue(draft);
      expect(await page.evaluate(() => window.SELLER_REASON_PROBE ?? window.SELLER_COMMENT_PROBE ?? null)).toBeNull();
    }
    for (const [itemId, status] of [[fixture.itemId, 404], [fixture.publicItemId, 200]]) {
      const reads = evidence.commentReads.filter(read => read.path === `/api/v1/catalog/items/${itemId}/comments`);
      expect(reads.length).toBeGreaterThanOrEqual(1);
      expect(reads.length).toBeLessThanOrEqual(2);
      expect(reads.every(read => read.status === status)).toBe(true);
    }
    expect(evidence.commentReads.every(read => [fixture.itemId, fixture.publicItemId]
      .some(itemId => read.path === `/api/v1/catalog/items/${itemId}/comments`))).toBe(true);
    expect(evidence.writes).toEqual([]);
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
