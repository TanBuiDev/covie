import json
import os
import sys
import time
from pathlib import Path
from urllib.parse import urlparse
from playwright.sync_api import expect, sync_playwright

if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires isolated billing Admin read fixture")
fixture = json.load(sys.stdin)
output = Path(fixture["output"])
evidence = {"completed": False, "invoices": fixture["readInvoices"], "screens": [], "pageErrors": 0, "mutations": 0}

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    errors = []
    writes = []
    try:
        for shop in fixture["shops"]:
            width, merchant = shop["width"], shop["merchantId"]
            context = browser.new_context(viewport={"width": width, "height": 1000}, service_workers="block")
            context.add_cookies([{"name": "covie_at", "value": fixture["adminToken"], "domain": "localhost", "path": "/",
                                 "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850}])

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
            page.on("request", lambda request: writes.append(request.method) if urlparse(request.url).path.startswith("/api/v1/")
                    and request.method not in ("GET", "HEAD", "OPTIONS") else None)
            page.goto(fixture["origin"] + "/admin/billing")
            expect(page.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
            page.get_by_label("Mã gói tra cứu lịch sử", exact=True).fill(fixture["historyPlanCode"])
            page.get_by_role("button", name="Xem lịch sử giá", exact=True).click()
            history = page.get_by_role("region", name="Lịch sử phiên bản Billing", exact=True)
            expect(history).to_be_visible()
            expect(history.get_by_text("Đã ngừng bán", exact=True)).to_have_count(2)
            expect(history.get_by_role("cell", name="2", exact=True)).to_have_count(1)
            page.get_by_role("button", name="Xem lịch sử chính sách", exact=True).focus()
            page.keyboard.press("Tab")
            expect(history).to_be_focused()
            page.keyboard.press("ArrowRight")
            history_geometry = history.evaluate("element => ({width:element.clientWidth,scroll:element.scrollWidth,left:element.scrollLeft})")
            if history_geometry["scroll"] > history_geometry["width"]:
                page.wait_for_function("document.querySelector('[aria-label=\"Lịch sử phiên bản Billing\"]').scrollLeft > 0")
            page.get_by_role("button", name="Xem lịch sử chính sách", exact=True).click()
            expect(history.get_by_role("columnheader", name="Công bố lúc", exact=True)).to_be_visible()
            expect(history.get_by_text("Toàn hệ thống", exact=True).first).to_be_visible()
            page.get_by_label("Merchant ID tra cứu thuê bao", exact=True).fill(merchant)
            page.get_by_role("button", name="Tra cứu thuê bao", exact=True).click()
            subscriptions = page.get_by_role("region", name="Danh sách thuê bao Seller", exact=True)
            expect(subscriptions).to_be_visible()
            expect(subscriptions.get_by_text(fixture["ownerUserId"], exact=True)).to_be_visible()
            expect(subscriptions.get_by_role("cell", name="ACTIVE", exact=True)).to_be_visible()
            page.get_by_role("button", name="Tra cứu thuê bao", exact=True).focus()
            page.keyboard.press("Tab")
            expect(subscriptions).to_be_focused()
            page.keyboard.press("ArrowRight")
            subscription_geometry = subscriptions.evaluate("element => ({width:element.clientWidth,scroll:element.scrollWidth,left:element.scrollLeft})")
            if subscription_geometry["scroll"] > subscription_geometry["width"]:
                page.wait_for_function("document.querySelector('[aria-label=\"Danh sách thuê bao Seller\"]').scrollLeft > 0")
            for value in ("SELLER_STANDARD", "ACTIVE", fixture["ownerUserId"]):
                cell = subscriptions.get_by_role("cell", name=value, exact=True)
                assert cell.evaluate("element => {const range=document.createRange();range.selectNodeContents(element);return range.getClientRects().length}") == 1
            page.get_by_role("button", name="Xem hóa đơn của " + merchant, exact=True).click()
            invoices = page.get_by_role("region", name="Hóa đơn liên quan của thuê bao", exact=True)
            expect(invoices).to_be_visible()
            expect(invoices.get_by_role("cell", name="PAID", exact=True)).to_be_visible()
            assert urlparse(page.url).path == "/admin/billing"
            assert page.evaluate("document.documentElement.scrollWidth <= innerWidth"), "Admin read viewport overflow"
            filename = f"admin-reads-{width}.png"
            page.screenshot(path=str(output / filename), full_page=True)
            evidence["screens"].append({"width": width, "screenshot": filename, "historyGeometry": history_geometry,
                                        "subscriptionGeometry": subscription_geometry, "subscriptionTextSingleLine": True})
            if width == 1366:
                invoice = next(entry for entry in fixture["readInvoices"] if entry["merchantId"] == merchant)
                page.get_by_role("link", name="Tra cứu nhật ký quản trị", exact=True).click()
                expect(page.get_by_role("heading", name="Nhật ký quản trị", exact=True)).to_be_visible()
                page.get_by_label("Hành động", exact=True).fill("BILLING_PAYMENT_SETTLED")
                page.get_by_label("Loại đối tượng", exact=True).fill("BILLING_INVOICE")
                page.get_by_label("ID đối tượng", exact=True).fill(invoice["invoiceId"])
                with page.expect_response(lambda response: response.request.method == "GET"
                                          and invoice["invoiceId"] in response.url) as audit_read:
                    page.get_by_role("button", name="Áp dụng", exact=True).click()
                assert audit_read.value.status == 200
                expect(page.locator(".admin-audit-list [role=listitem]")).to_have_count(1)
                expect(page.locator(".admin-audit-list")).to_contain_text("BILLING_PAYMENT_SETTLED")
                evidence["associatedInvoiceAuditVisible"] = True
            context.close()
        evidence["pageErrors"] = len(errors)
        evidence["mutations"] = len(writes)
        assert not errors and not writes
        evidence["completed"] = True
    finally:
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
