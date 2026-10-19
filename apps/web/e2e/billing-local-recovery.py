import json
import os
import re
import sys
import time
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright


if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires isolated billing recovery fixture")
fixture = json.load(sys.stdin)
origin, backend = fixture["origin"], fixture["api"]
output = Path(fixture["output"])
evidence = {"completed": False, "invoices": [], "screens": [], "pageErrors": 0}


def api(method, url, token=None, body=None, control=False):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    if control:
        headers["x-fixture-control"] = fixture["providerControl"]
    data = None if body is None else json.dumps(body).encode("utf-8")
    if data is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def control(name):
    return api("POST", fixture["provider"] + "/_fixture/action/" + name, control=True)


def stats():
    return api("GET", fixture["provider"] + "/_fixture/stats", control=True)


def preview(page):
    page.get_by_role("button", name="Kiểm tra số tiền thanh toán", exact=True).click()
    expect(page.get_by_text("Phải thanh toán: 15.000đ", exact=True)).to_be_visible()


def checkout(page):
    with page.expect_response(lambda response: response.request.method == "POST"
                              and urlparse(response.url).path.endswith("/billing/checkout")) as result:
        page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True).click()
    return result.value


api("POST", backend + "/api/v1/admin/billing/plans", fixture["adminToken"], {
    "code": fixture["planCode"], "displayName": "Recovery monthly fixture", "priceVnd": 15000,
    "cycleMonths": 1, "effectiveAt": (datetime.now(timezone.utc) + timedelta(seconds=2)).isoformat(),
})
deadline = time.monotonic() + 15
while not any(plan["code"] == fixture["planCode"] for plan in api("GET", backend + "/api/v1/billing/plans")):
    assert time.monotonic() < deadline, "Fixture plan not effective"
    time.sleep(0.25)

active_page = None
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    errors = []
    try:
        for index, shop in enumerate(fixture["shops"]):
            width, merchant = shop["width"], shop["merchantId"]
            context = browser.new_context(viewport={"width": width, "height": 1000})
            context.add_cookies([{"name": "covie_at", "value": fixture["ownerToken"], "domain": "localhost",
                                  "path": "/", "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850}])

            def forward(route):
                url = urlparse(route.request.url)
                if url.hostname not in ("localhost", "127.0.0.1"):
                    route.abort("blockedbyclient")
                elif url.path.startswith("/api/v1/") and url.path != "/api/v1/auth/ssr-refresh":
                    response = route.fetch(url=backend + url.path + ("?" + url.query if url.query else ""))
                    route.fulfill(response=response)
                else:
                    route.continue_()

            context.route("**/*", forward)
            page = context.new_page()
            active_page = page
            page.on("pageerror", lambda error: errors.append(type(error).__name__))
            path = "/seller/" + merchant + "/billing"
            page.goto(origin + path)
            page.wait_for_load_state("networkidle")
            expect(page.get_by_role("heading", name="Gói thuê bao đang được công bố", exact=True)).to_be_visible()
            page.locator("label").filter(has_text=re.compile(r"^Chọn gói")).locator("select").select_option(fixture["planCode"])
            preview(page)
            if index == 0:
                control("expire-quotes")
                rejected = checkout(page)
                assert rejected.status == 409, "Expired quote was not rejected"
                assert rejected.json()["error"]["code"] == "BILLING_PRICE_INVALID"
                assert stats()["creates"] == 0, "Expired quote reached provider"
                preview(page)
                result = checkout(page)
                assert result.status == 200, "Fresh quote did not recover"
                payment = result.json()
                order = payment["orderCode"]
                invoice_id = payment["invoiceId"]
                callback = api("POST", fixture["provider"] + "/_fixture/paid/" + str(order), control=True)
                assert api("POST", backend + "/api/v1/billing/webhooks/payos", body=callback)["status"] == "settled"
                expect(page.get_by_role("cell", name="PAID", exact=True)).to_be_visible(timeout=15000)
            else:
                control("lose-response")
                failed = checkout(page)
                assert failed.status == 503, "Provider ambiguity did not return safe503"
                assert failed.json()["error"]["code"] == "BILLING_PAYMENT_UNAVAILABLE"
                records = api("GET", backend + "/api/v1/merchants/" + merchant + "/billing/invoices", fixture["ownerToken"])
                assert len(records) == 1 and records[0]["status"] == "PENDING_PAYMENT"
                invoice_id = records[0]["invoiceId"]
                order = stats()["payments"][-1]["orderCode"]
                assert stats()["creates"] == index + 1, "Ambiguous create was repeated"
                control("reconcile")
                state = api("GET", backend + "/api/v1/merchants/" + merchant + "/billing/invoices/" + invoice_id,
                            fixture["ownerToken"])
                assert state["checkoutUrl"].startswith("https://pay.payos.vn/web/"), "Signed lookup did not repair link"
                if index == 1:
                    page.reload()
                    page.wait_for_load_state("networkidle")
                link = page.get_by_role("link", name="Mở payOS để thanh toán")
                expect(link).to_be_visible(timeout=15000)
                expect(link).to_have_attribute("href", state["checkoutUrl"])
                expect(page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_disabled()
                callback = api("POST", fixture["provider"] + "/_fixture/paid/" + str(order), control=True)
                control("reconcile")
                assert api("POST", backend + "/api/v1/billing/webhooks/payos", body=callback)["status"] == "duplicate"
                expect(page.get_by_role("cell", name="PAID", exact=True)).to_be_visible(timeout=15000)
                expect(page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_enabled()
                expect(page.locator("p[role='alert']")).to_have_count(0)
            assert stats()["creates"] == index + 1, "Recovery issued a second provider create"
            assert urlparse(page.url).path == path
            expect(page.get_by_role("heading", name="Gói thuê bao đang được công bố", exact=True)).to_be_visible()
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Recovery viewport overflow"
            filename = f"recovery-{width}.png"
            page.screenshot(path=str(output / filename), full_page=True)
            evidence["screens"].append({"width": width, "screenshot": filename, "path": path})
            evidence["invoices"].append({"invoiceId": invoice_id, "merchantId": merchant, "width": width, "orderCode": order})
            context.close()
        evidence["pageErrors"] = len(errors)
        evidence["providerStats"] = stats()
        assert not errors, "Recovery page errors observed"
        evidence["completed"] = True
    finally:
        if not evidence["completed"] and active_page is not None and not active_page.is_closed():
            active_page.screenshot(path=str(output / "diagnostic.png"), full_page=True)
            (output / "diagnostic.txt").write_text(active_page.locator("body").inner_text(), encoding="utf-8")
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
