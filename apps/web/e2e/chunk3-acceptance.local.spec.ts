import axe from "axe-core";
import { expect, test, type Page } from "@playwright/test";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { localFixtureEnabled, loginAs, readLocalUiFixture, type LocalUiFixture } from "./fixtures/local-actors";

test.skip(!localFixtureEnabled, "Run with COVIE_UI_FIXTURE=1 after pnpm ui:seed-local.");
test.describe.configure({ mode: "serial" });
test.setTimeout(180_000);

const evidenceDirectory = path.resolve(process.cwd(), "../../.local/dev/visual-review/chunk3");
const allResponsiveWidths = [360, 390, 768, 1024, 1280, 1440] as const;
const adminWidths = [1024, 1280, 1440] as const;

type Landmark = { role: "heading" | "region" | "searchbox"; name: string | RegExp; exact?: boolean };
type AcceptanceRoute = {
  unit: string;
  figmaNode: string;
  path: (fixture: LocalUiFixture) => string;
  landmark: Landmark;
};

const buyerRoutes: AcceptanceRoute[] = [
  { unit: "PUB-001", figmaNode: "282:290", path: () => "/", landmark: { role: "heading", name: "Khám phá danh mục" } },
  { unit: "PUB-004", figmaNode: "294:1289", path: () => "/search?q=rau", landmark: { role: "searchbox", name: "Tìm món đồ, dịch vụ", exact: true } },
  { unit: "PUB-006", figmaNode: "299:2562", path: fixture => `/items/${fixture.productItemId}`, landmark: { role: "heading", name: "Rau củ theo mùa" } },
  { unit: "PUB-007", figmaNode: "304:2803", path: fixture => `/items/${fixture.serviceItemId}`, landmark: { role: "heading", name: "Sửa khóa tận nơi" } },
  { unit: "PUB-008", figmaNode: "308:2978", path: fixture => `/items/${fixture.resaleItemId}`, landmark: { role: "heading", name: "Kệ gỗ thanh lý" } },
  { unit: "PUB-009", figmaNode: "312:3076", path: fixture => `/merchants/${fixture.merchantId}`, landmark: { role: "heading", name: "Cửa hàng Chợ Xanh" } },
  { unit: "BUY-003", figmaNode: "327:4023", path: () => "/me/addresses", landmark: { role: "heading", name: "Địa chỉ của tôi" } },
  { unit: "BUY-004-new", figmaNode: "327:4086", path: () => "/me/addresses/new", landmark: { role: "heading", name: "Thêm địa chỉ" } },
  { unit: "BUY-004-edit", figmaNode: "564:5448", path: fixture => `/me/addresses/${fixture.addressId}`, landmark: { role: "heading", name: "Sửa địa chỉ" } },
  { unit: "BUY-007", figmaNode: "332:4364", path: () => "/orders", landmark: { role: "heading", name: "Đơn hàng của tôi" } },
  { unit: "ORD-011", figmaNode: "334:5377", path: fixture => `/orders/${fixture.scenarios.completedOrderId}`, landmark: { role: "heading", name: /Đơn CV-UI-COMPLETED/ } },
  { unit: "NEED-001", figmaNode: "342:5209", path: () => "/needs", landmark: { role: "heading", name: /Nhu cầu của tôi|Cần mua/ } },
  { unit: "NEED-002", figmaNode: "342:5413", path: () => "/needs/new", landmark: { role: "heading", name: /Đăng nhu cầu|Tạo Cần mua/ } },
  { unit: "NEED-003", figmaNode: "342:5567", path: fixture => `/needs/${fixture.scenarios.activeNeedId}`, landmark: { role: "heading", name: "Cần rau củ giao trong ngày" } },
  { unit: "BUY-009", figmaNode: "348:6173", path: () => "/me/reputation", landmark: { role: "heading", name: "Uy tín người mua" } },
  { unit: "Notifications", figmaNode: "348:6408", path: () => "/me/notifications", landmark: { role: "heading", name: "Thông báo", exact: true } },
  { unit: "Profile", figmaNode: "566:9705", path: () => "/me", landmark: { role: "heading", name: "Minh Buyer" } },
  { unit: "Sessions", figmaNode: "319:3917", path: () => "/me/security/sessions", landmark: { role: "heading", name: "Bảo mật" } },
];

const sellerRoutes: AcceptanceRoute[] = [
  { unit: "SEL-001", figmaNode: "357:9", path: f => `/seller/${f.merchantId}`, landmark: { role: "heading", name: "Tổng quan" } },
  { unit: "SEL-002", figmaNode: "357:220", path: f => `/seller/${f.merchantId}/settings`, landmark: { role: "heading", name: "Cài đặt shop" } },
  { unit: "SEL-003", figmaNode: "357:321", path: f => `/seller/${f.merchantId}/locations`, landmark: { role: "heading", name: "Điểm bán" } },
  { unit: "SEL-004", figmaNode: "357:407", path: f => `/seller/${f.merchantId}/locations/${f.locationId}`, landmark: { role: "heading", name: "Sửa điểm bán" } },
  { unit: "SEL-005", figmaNode: "362:358", path: f => `/seller/${f.merchantId}/locations/${f.locationId}/delivery`, landmark: { role: "heading", name: /Phạm vi phục vụ|Vùng giao hàng & phục vụ/ } },
  { unit: "SEL-006", figmaNode: "362:525", path: f => `/seller/${f.merchantId}/locations/${f.locationId}/shipping`, landmark: { role: "heading", name: /Phí giao hàng|Chính sách giao hàng/ } },
  { unit: "SEL-007", figmaNode: "362:718", path: f => `/seller/${f.merchantId}/locations/${f.locationId}/minimum-order`, landmark: { role: "heading", name: "Đơn tối thiểu" } },
  { unit: "SEL-008", figmaNode: "362:796", path: f => `/seller/${f.merchantId}/locations/${f.locationId}/contacts`, landmark: { role: "region", name: "Kênh liên hệ" } },
  { unit: "SEL-009", figmaNode: "368:709", path: f => `/seller/${f.merchantId}/catalog`, landmark: { role: "heading", name: "Danh mục", exact: true } },
  { unit: "SEL-010", figmaNode: "368:854", path: f => `/seller/${f.merchantId}/catalog/${f.productItemId}/edit`, landmark: { role: "heading", name: "Rau củ theo mùa" } },
  { unit: "SEL-011", figmaNode: "368:1026", path: f => `/seller/${f.merchantId}/catalog/new?type=SERVICE`, landmark: { role: "heading", name: /Dịch vụ|Tạo nội dung bán/ } },
  { unit: "SEL-014", figmaNode: "374:1340", path: f => `/seller/${f.merchantId}/orders`, landmark: { role: "heading", name: "Đơn hàng" } },
  { unit: "SEL-015", figmaNode: "375:1070", path: f => `/seller/${f.merchantId}/orders/${f.scenarios.negotiatedOrderId}`, landmark: { role: "heading", name: /Chi tiết đơn|Đơn CV-UI-NEGOTIATED/ } },
  { unit: "NEED-005/006", figmaNode: "387:13665", path: f => `/seller/${f.merchantId}/needs`, landmark: { role: "heading", name: "Cần mua phù hợp" } },
  { unit: "SEL-018", figmaNode: "389:13627", path: f => `/seller/${f.merchantId}/reputation`, landmark: { role: "heading", name: "Uy tín cửa hàng", exact: true } },
  { unit: "SEL-019", figmaNode: "389:13692", path: f => `/seller/${f.merchantId}/notices`, landmark: { role: "heading", name: "Thông báo kiểm duyệt", exact: true } },
];

const adminRoutes: AcceptanceRoute[] = [
  { unit: "ADM-001", figmaNode: "393:4507", path: () => "/admin", landmark: { role: "heading", name: "Tổng quan" } },
  { unit: "ADM-002", figmaNode: "393:4746", path: () => "/admin/users", landmark: { role: "heading", name: "Người dùng" } },
  { unit: "ADM-003", figmaNode: "393:5003", path: () => "/admin/merchants", landmark: { role: "heading", name: "Người bán / Nhà cung cấp" } },
  { unit: "ADM-004", figmaNode: "399:1190", path: () => "/admin/shops", landmark: { role: "heading", name: "Cửa hàng & điểm bán" } },
  { unit: "ADM-005", figmaNode: "399:1413", path: () => "/admin/listings", landmark: { role: "heading", name: "Nội dung đăng bán" } },
  { unit: "ADM-006", figmaNode: "400:1545", path: () => "/admin/orders", landmark: { role: "heading", name: "Tra cứu đơn hàng" } },
  { unit: "ADM-007", figmaNode: "400:1818", path: f => `/admin/orders/${f.scenarios.completedOrderId}/history`, landmark: { role: "heading", name: /Lịch sử đơn CV-UI-COMPLETED/ } },
  { unit: "ADM-008", figmaNode: "404:2216", path: () => "/admin/needs", landmark: { role: "heading", name: "Cần mua" } },
  { unit: "ADM-009", figmaNode: "404:2473", path: () => "/admin/reports", landmark: { role: "heading", name: "Hàng đợi báo cáo" } },
  { unit: "ADM-010/012", figmaNode: "407:2674", path: () => "/admin/settings", landmark: { role: "heading", name: "Kiểm duyệt · Đánh giá · Uy tín", exact: true } },
  { unit: "ADM-013", figmaNode: "407:2946", path: () => "/admin/settings?section=orders", landmark: { role: "heading", name: "Thời hạn đơn hàng" } },
  { unit: "ADM-014", figmaNode: "407:3149", path: () => "/admin/settings?section=needs", landmark: { role: "heading", name: "Giới hạn phản hồi Cần mua", exact: true } },
  { unit: "ADM-015", figmaNode: "423:17219", path: () => "/admin/zones", landmark: { role: "heading", name: "Khu vực" } },
  { unit: "ADM-016", figmaNode: "423:17230", path: () => "/admin/categories", landmark: { role: "heading", name: "Danh mục" } },
  { unit: "ADM-017", figmaNode: "417:3838", path: () => "/admin/audit", landmark: { role: "heading", name: "Nhật ký quản trị" } },
];

function evidenceName(route: AcceptanceRoute, width: number) {
  return `${route.unit.toLowerCase().replace(/[^a-z0-9]+/g, "-")}-${route.figmaNode.replace(":", "-")}-${width}.png`;
}

async function assertRouteAndLandmark(page: Page, route: AcceptanceRoute, routePath: string) {
  await expect(page).toHaveURL(url => `${url.pathname}${url.search}` === routePath);
  const landmark = page.getByRole(route.landmark.role, {
    name: route.landmark.name,
    exact: route.landmark.exact,
  });
  await expect(landmark).toHaveCount(1);
  await expect(landmark).toBeVisible();
}

async function assertNoSeriousA11yViolations(page: Page, unit: string) {
  await page.addScriptTag({ content: axe.source });
  const violations = await page.evaluate(async () => {
    const runner = (globalThis as unknown as {
      axe: {
        run: (root: Document, options: object) => Promise<{
          violations: Array<{
            id: string;
            impact: string | null;
            nodes: Array<{ failureSummary?: string; html: string; target: string[] }>;
          }>;
        }>;
      };
    }).axe;
    const result = await runner.run(document, { runOnly: { type: "tag", values: ["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"] } });
    return result.violations
      .filter(violation => violation.impact === "critical" || violation.impact === "serious")
      .map(violation => ({
        id: violation.id,
        impact: violation.impact,
        nodes: violation.nodes.map(node => ({
          failureSummary: node.failureSummary,
          html: node.html.slice(0, 240),
          target: node.target,
        })),
      }));
  });
  expect(violations, `${unit} has serious WCAG violations`).toEqual([]);
}

async function assertNoHorizontalOverflow(page: Page, unit: string, width: number) {
  const report = await page.evaluate(() => {
    const viewportWidth = window.innerWidth;
    const offenders = Array.from(document.querySelectorAll<HTMLElement>("body *"))
      .map(element => {
        const rect = element.getBoundingClientRect();
        return {
          selector: `${element.tagName.toLowerCase()}${element.id ? `#${element.id}` : ""}${
            typeof element.className === "string" && element.className
              ? `.${element.className.trim().replace(/\s+/g, ".")}`
              : ""
          }`,
          left: Math.round(rect.left),
          right: Math.round(rect.right),
          width: Math.round(rect.width),
        };
      })
      .filter(({ left, right }) => left < -1 || right > viewportWidth + 1)
      .slice(0, 8);

    return {
      documentWidth: document.documentElement.scrollWidth,
      viewportWidth,
      offenders,
    };
  });

  expect(
    report.documentWidth,
    `${unit} overflows at ${width}px: ${JSON.stringify(report)}`,
  ).toBeLessThanOrEqual(report.viewportWidth);
}

async function captureRoute(
  page: Page,
  fixture: LocalUiFixture,
  route: AcceptanceRoute,
  widths: readonly number[],
) {
  const routePath = route.path(fixture);
  const initialWidth = widths[0] ?? 360;
  await page.setViewportSize({ width: initialWidth, height: initialWidth === 360 ? 800 : initialWidth === 390 ? 844 : 900 });
  await page.goto(routePath);
  await assertRouteAndLandmark(page, route, routePath);
  await assertNoSeriousA11yViolations(page, route.unit);
  await page.emulateMedia({ reducedMotion: "reduce" });
  mkdirSync(evidenceDirectory, { recursive: true });

  for (const width of widths) {
    const height = width === 360 ? 800 : width === 390 ? 844 : 900;
    await page.setViewportSize({ width, height });
    await assertRouteAndLandmark(page, route, routePath);
    await assertNoHorizontalOverflow(page, route.unit, width);
    await page.addStyleTag({ content: "nextjs-portal{display:none!important}" });
    await page.screenshot({ path: path.join(evidenceDirectory, evidenceName(route, width)) });
  }
}

test("AUTH production routes retain one landmark and no serious WCAG violations", async ({ page }) => {
  const routes: AcceptanceRoute[] = [
    { unit: "AUTH-001", figmaNode: "318:3579", path: () => "/login", landmark: { role: "heading", name: "Đăng nhập" } },
    { unit: "AUTH-002", figmaNode: "318:3674", path: () => "/register", landmark: { role: "heading", name: "Tạo tài khoản CoVie", exact: true } },
    { unit: "AUTH-reset", figmaNode: "318:3855", path: () => "/reset-password", landmark: { role: "heading", name: /Đặt lại mật khẩu|Liên kết không hợp lệ/ } },
  ];
  const fixture = readLocalUiFixture();
  for (const route of routes) await captureRoute(page, fixture, route, allResponsiveWidths);
});

test("Buyer production routes match their responsive and accessibility contracts", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.buyer);
  for (const route of buyerRoutes) await captureRoute(page, fixture, route, allResponsiveWidths);
});

test("Seller production routes match their responsive and accessibility contracts", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.seller);
  for (const route of sellerRoutes) await captureRoute(page, fixture, route, allResponsiveWidths);
});

test("Admin production routes match their responsive and accessibility contracts", async ({ page }) => {
  const fixture = readLocalUiFixture();
  await loginAs(page, fixture.actors.admin);
  for (const route of adminRoutes) await captureRoute(page, fixture, route, adminWidths);
});
