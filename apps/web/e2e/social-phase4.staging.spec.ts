import { expect, test, type Page } from "@playwright/test";
import {
  loginStagingRole, readStagingAuthConfig, stagingAuthIsConfigured,
} from "./fixtures/staging-auth-fixture";

/**
 * Real-browser Phase 4 acceptance on an explicitly configured nonproduction
 * environment. It must not masquerade as a successful staging rehearsal in CI.
 * Use two independent identities and a known, published catalog fixture.
 */
const searchPath = process.env.COVIE_SOCIAL_E2E_SEARCH_PATH ?? "";
const itemName = process.env.COVIE_SOCIAL_E2E_ITEM_NAME ?? "";
const prepared = stagingAuthIsConfigured() &&
  /^\/search(?:\?|$)/.test(searchPath) && itemName.trim().length > 0;
test.skip(!prepared, "Staging HTTPS credentials, search path and published fixture name are required.");
test.describe.configure({ mode: "serial" });
test.setTimeout(120_000);

function card(page: Page) {
  return page.getByRole("article").filter({
    has: page.getByRole("link", { name: itemName, exact: true }),
  }).first();
}

async function openDiscovery(page: Page) {
  await page.goto(searchPath);
  const discover = page.getByRole("button", { name: "Khám phá", exact: true });
  await expect(discover).toBeVisible();
  await discover.click();
  await expect(discover).toHaveAttribute("aria-pressed", "true");
  await expect(card(page)).toBeVisible({ timeout: 30_000 });
}

for (const width of [360, 768, 1280, 1440]) {
  test(`Social/Catalog layout + lazy comments at ${width}px`, async ({ browser }) => {
    const context = await browser.newContext({ viewport: { width, height: 900 } });
    const page = await context.newPage();
    try {
      let commentReads = 0;
      page.on("request", (request) => {
        if (/\/api\/v1\/catalog\/items\/[a-f0-9-]+\/comments\?/.test(request.url())) commentReads++;
      });
      await openDiscovery(page);
      const socialCard = card(page);
      await expect(socialCard.getByRole("button", { name: "Bình luận " + itemName })).toBeVisible();
      expect(commentReads, "No N+1 comment fetch before opening the thread").toBe(0);
      const viewportOverflow = await page.evaluate(() =>
        document.documentElement.scrollWidth > window.innerWidth + 2);
      expect(viewportOverflow, "No horizontal overflow in Social mode").toBe(false);
      await socialCard.getByRole("button", { name: "Bình luận " + itemName }).click();
      await expect(socialCard.getByRole("region", { name: "Bình luận sản phẩm" })).toBeVisible();
      await expect.poll(() => commentReads).toBeGreaterThan(0);
      await socialCard.getByRole("button", { name: "Đóng" }).press("Escape");
      await expect(socialCard.getByRole("region", { name: "Bình luận sản phẩm" })).toHaveCount(0);
      const catalog = page.getByRole("button", { name: "Mua sắm nhanh", exact: true });
      await catalog.click();
      await expect(catalog).toHaveAttribute("aria-pressed", "true");
      await expect(card(page).getByRole("button", { name: "Bình luận " + itemName })).toHaveCount(0);
      await expect(card(page).getByRole("link", { name: itemName, exact: true }).first()).toBeVisible();
      await page.getByRole("button", { name: "Khám phá", exact: true }).click();
      await expect(card(page).getByRole("button", { name: "Bình luận " + itemName })).toBeVisible();
      await page.screenshot({ path: test.info().outputPath(`social-${width}.png`), fullPage: true });
    } finally { await context.close(); }
  });
}

test("authenticated Like, Save and comment CRUD respect server state", async ({ browser }) => {
  test.skip(process.env.COVIE_SOCIAL_E2E_MUTATIONS_OK !== "1",
    "Staging-only write test requires COVIE_SOCIAL_E2E_MUTATIONS_OK=1.");
  const config = readStagingAuthConfig();
  const context = await browser.newContext({ baseURL: config.baseURL });
  const page = await context.newPage();
  const stamp = `Phase4-${Date.now()}`;
  let liked = false;
  let saved = false;
  try {
    await loginStagingRole(page, "buyer", config);
    await openDiscovery(page);
    const listing = card(page);
    const like = listing.getByRole("button", { name: "Thích " + itemName });
    await expect(like).toBeEnabled();
    await like.click();
    liked = true;
    await expect(listing.getByRole("button", { name: "Bỏ thích " + itemName })).toHaveAttribute("aria-pressed", "true");
    await listing.getByRole("button", { name: "Lưu " + itemName }).click();
    saved = true;
    await expect(listing.getByRole("button", { name: "Bỏ lưu " + itemName })).toHaveAttribute("aria-pressed", "true");
    await listing.getByRole("button", { name: "Bình luận " + itemName }).click();
    const comments = listing.getByRole("region", { name: "Bình luận sản phẩm" });
    await expect(comments).toBeVisible();
    await comments.getByRole("textbox", { name: "Viết bình luận" }).fill(stamp);
    await comments.getByRole("button", { name: "Gửi bình luận" }).click();
    await expect(comments.getByText(stamp, { exact: true })).toBeVisible();
    const mine = comments.getByText(stamp, { exact: true }).locator("..");
    await mine.getByRole("button", { name: "Xóa bình luận của bạn" }).click();
    await expect(comments.getByText(stamp, { exact: true })).toHaveCount(0);
    await page.reload();
    await openDiscovery(page);
    await expect(card(page).getByRole("button", { name: "Bỏ thích " + itemName })).toHaveAttribute("aria-pressed", "true");
    await expect(card(page).getByRole("button", { name: "Bỏ lưu " + itemName })).toHaveAttribute("aria-pressed", "true");
  } finally {
    // Best-effort cleanup: never claim successful cleanup if it fails.
    if (liked || saved) {
      try {
        await openDiscovery(page);
        if (liked) await card(page).getByRole("button", { name: "Bỏ thích " + itemName }).click();
        if (saved) await card(page).getByRole("button", { name: "Bỏ lưu " + itemName }).click();
      } catch (error) { console.error("Manual staging reaction cleanup required", error); }
    }
    await context.close();
  }
});
