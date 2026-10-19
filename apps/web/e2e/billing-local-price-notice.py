import json
import os
import sys
import time
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright


if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires isolated billing price notice fixture")
fixture = json.load(sys.stdin)
output = Path(fixture["output"])
evidence = {"completed": False, "invoices": [], "screens": [], "pageErrors": 0}
errors = []

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    try:
        for width in (360, 390, 1366):
            context = browser.new_context(viewport={"width": width, "height": 1000})
            context.add_cookies([{"name": "covie_at", "value": fixture["ownerToken"], "domain": "localhost",
                                 "path": "/", "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850}])

            def forward(route):
                url = urlparse(route.request.url)
                if url.hostname not in ("localhost", "127.0.0.1"):
                    route.abort("blockedbyclient")
                elif url.path.startswith("/api/v1/") and url.path != "/api/v1/auth/ssr-refresh":
                    response = route.fetch(url=fixture["api"] + url.path + ("?" + url.query if url.query else ""))
                    route.fulfill(response=response)
                else:
                    route.continue_()

            context.route("**/*", forward)
            page = context.new_page()
            page.on("pageerror", lambda error: errors.append(type(error).__name__))
            page.goto(fixture["origin"] + "/me/notifications")
            expect(page).to_have_url(fixture["origin"] + "/me/notifications")
            expect(page.get_by_role("heading", name="Thông báo gần đây", exact=True)).to_be_visible()
            notice = page.get_by_role("button", name="Bảng giá thuê bao được cập nhật. Mở chi tiết", exact=True)
            expect(notice).to_have_count(1)
            expect(notice).to_contain_text("Gói " + fixture["noticePlanCode"] + ", phiên bản 2")
            expect(notice).to_contain_text("15.000đ / 1 tháng")
            expect(notice).to_contain_text("giờ Việt Nam")
            expect(notice).to_contain_text("Kỳ đã thanh toán không thay đổi; không tự động thu phí.")
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            notice.focus()
            expect(notice).to_be_focused()
            page.screenshot(path=str(output / f"price-notice-{width}.png"), full_page=True)
            (output / f"price-notice-{width}.aria.txt").write_text(notice.aria_snapshot(), encoding="utf-8")
            page.keyboard.press("Enter")
            expect(page).to_have_url(fixture["origin"] + "/seller")
            expect(page.get_by_role("heading", name="Seller Center", exact=True)).to_be_visible()
            page.go_back(wait_until="networkidle")
            expect(page).to_have_url(fixture["origin"] + "/me/notifications")
            expect(page.get_by_role("button", name="Bảng giá thuê bao được cập nhật. Mở chi tiết", exact=True)).to_have_count(1)
            evidence["screens"].append({"width": width, "keyboardNavigation": True, "publicFacts": True,
                                         "sameNoticeAfterBack": True, "screenshot": f"price-notice-{width}.png"})
            context.close()
        assert not errors
        evidence["pageErrors"] = len(errors)
        evidence["completed"] = True
    finally:
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
