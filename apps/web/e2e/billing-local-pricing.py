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
    raise RuntimeError("Requires isolated billing pricing fixture")
fixture = json.load(sys.stdin)
origin, backend = fixture["origin"], fixture["api"]
output = Path(fixture["output"])
evidence = {"completed": False, "invoices": [], "screens": [], "publicationPreviews": [], "pageErrors": 0}


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


def wait_revision(code, revision):
    deadline = time.monotonic() + 20
    while True:
        for plan in api("GET", backend + "/api/v1/billing/plans"):
            if plan["code"] == code and plan["revision"] == revision:
                return plan
        assert time.monotonic() < deadline, "Scheduled price revision did not become effective"
        time.sleep(0.1)


def preview(page, amount):
    page.get_by_role("button", name="Kiểm tra số tiền thanh toán", exact=True).click()
    expect(page.get_by_text("Phải thanh toán: " + amount, exact=True)).to_be_visible()


def checkout(page):
    with page.expect_response(lambda response: response.request.method == "POST"
                              and urlparse(response.url).path.endswith("/billing/checkout")) as result:
        page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True).click()
    return result.value


active_page = None
with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    errors = []

    def context_for(token, width):
        context = browser.new_context(viewport={"width": width, "height": 1000})
        context.add_cookies([{"name": "covie_at", "value": token, "domain": "localhost", "path": "/",
                             "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850}])

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
        context.on("page", lambda page: page.on("pageerror", lambda error: errors.append(type(error).__name__)))
        return context

    try:
        for index, shop in enumerate(fixture["shops"]):
            width, merchant = shop["width"], shop["merchantId"]
            code = fixture["planCode"] + "_" + str(width)
            api("POST", backend + "/api/v1/admin/billing/plans", fixture["adminToken"], {
                "code": code, "displayName": "Pricing browser " + str(width), "priceVnd": 10000,
                "cycleMonths": 1, "effectiveAt": (datetime.now(timezone.utc) + timedelta(seconds=2)).isoformat(),
            })
            assert wait_revision(code, 1)["priceVnd"] == 10000
            owner_context = context_for(fixture["ownerToken"], width)
            admin_context = context_for(fixture["adminToken"], width)
            page = owner_context.new_page()
            active_page = page
            path = "/seller/" + merchant + "/billing"
            page.goto(origin + path)
            page.wait_for_load_state("networkidle")
            expect(page.get_by_role("heading", name="Gói thuê bao đang được công bố", exact=True)).to_be_visible()
            page.locator("label").filter(has_text=re.compile(r"^Chọn gói")).locator("select").select_option(code)
            preview(page, "10.000đ")
            admin_pages = [admin_context.new_page(), admin_context.new_page()]
            for admin_page in admin_pages:
                admin_page.goto(origin + "/admin/billing")
                admin_page.wait_for_load_state("networkidle")
                expect(admin_page.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
                admin_page.get_by_label("Chọn gói muốn cập nhật", exact=True).select_option(code)
                admin_page.get_by_label("Giá mới (VND)", exact=True).fill("15000")
                admin_page.get_by_label("Số tháng", exact=True).fill("1")
            for position, admin_page in enumerate(admin_pages):
                effective = (datetime.now() + timedelta(seconds=10 + position * 60)).isoformat(timespec="seconds").removesuffix(":00")
                admin_page.get_by_label("Có hiệu lực từ", exact=True).fill(effective)
                price_path = "/api/v1/admin/billing/plans/" + code + "/prices"
                confirmations = []

                def cancel_preview(dialog):
                    confirmations.append(dialog.message)
                    assert "mọi trạng thái" in dialog.message and "Không tự thay đổi" in dialog.message
                    assert "10.000" in dialog.message and "15.000" in dialog.message
                    dialog.dismiss()

                if position == 0:
                    admin_page.once("dialog", cancel_preview)
                    with admin_page.expect_response(lambda response: response.request.method == "POST"
                                                   and urlparse(response.url).path == price_path + "/preview") as cancelled:
                        admin_page.get_by_role("button", name="Công bố giá mới", exact=True).click()
                    assert cancelled.value.status == 200
                    expect(admin_page.get_by_role("button", name="Công bố giá mới", exact=True)).to_be_enabled()
                    assert len(confirmations) == 1
                    assert next(plan for plan in api("GET", backend + "/api/v1/admin/billing/plans", fixture["adminToken"])
                                if plan["code"] == code)["revision"] == 1
                    admin_page.once("dialog", lambda dialog: dialog.accept())
                    with admin_page.expect_response(lambda response: response.request.method == "POST"
                                                   and urlparse(response.url).path == price_path) as published:
                        admin_page.get_by_role("button", name="Công bố giá mới", exact=True).click()
                    assert published.value.status == 200
                if position == 1:
                    with admin_page.expect_response(lambda response: response.request.method == "POST"
                                                   and urlparse(response.url).path == price_path + "/preview") as stale:
                        admin_page.get_by_role("button", name="Công bố giá mới", exact=True).click()
                    assert stale.value.status == 409
                    expect(admin_page.locator("p[role='alert']")).to_contain_text("Billing configuration changed")
                    admin_page.get_by_role("button", name="Tải lại phiên bản để xem lại", exact=True).click()
                    expect(admin_page.get_by_text("Đã tải phiên bản mới nhất.", exact=False)).to_be_visible()
                    scheduled_messages = []

                    def cancel_scheduled(dialog):
                        scheduled_messages.append(dialog.message)
                        assert "bản đã công bố 2" in dialog.message
                        dialog.dismiss()

                    admin_page.once("dialog", cancel_scheduled)
                    with admin_page.expect_response(lambda response: response.request.method == "POST"
                                                   and urlparse(response.url).path == price_path + "/preview") as scheduled:
                        admin_page.get_by_role("button", name="Công bố giá mới", exact=True).click()
                    assert scheduled.value.status == 200
                    expect(admin_page.get_by_role("button", name="Công bố giá mới", exact=True)).to_be_enabled()
                    assert len(scheduled_messages) == 1
                    assert next(plan for plan in api("GET", backend + "/api/v1/admin/billing/plans", fixture["adminToken"])
                                if plan["code"] == code)["revision"] == 2
                    evidence["publicationPreviews"].append({"width": width, "cancelPreservedRevision": True,
                        "stalePreview409": True, "explicitReloadScheduledRevision": 2})
            assert wait_revision(code, 2)["priceVnd"] == 15000
            failed = checkout(page)
            assert failed.status == 409 and failed.json()["error"]["code"] == "BILLING_PRICE_INVALID"
            assert api("GET", fixture["provider"] + "/_fixture/stats", control=True)["creates"] == index
            expect(page.locator("p[role='alert']")).to_be_visible()
            expect(page.locator("label").filter(has_text=re.compile(r"^Chọn gói")).locator("select")).to_contain_text("15.000đ")
            preview(page, "15.000đ")
            created = checkout(page)
            assert created.status == 200
            payment = created.json()
            assert payment["amountVnd"] == 15000
            callback = api("POST", fixture["provider"] + "/_fixture/paid/" + str(payment["orderCode"]), control=True)
            assert api("POST", backend + "/api/v1/billing/webhooks/payos", body=callback)["status"] == "settled"
            expect(page.get_by_role("cell", name="PAID", exact=True)).to_be_visible(timeout=15000)
            for current, role, landmark in [(page, "seller", "Gói thuê bao đang được công bố"),
                                            (admin_pages[1], "admin-stale", "Thuê bao Seller")]:
                assert urlparse(current.url).path == (path if role == "seller" else "/admin/billing")
                expect(current.get_by_role("heading", name=landmark, exact=True)).to_be_visible()
                assert current.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Pricing viewport overflow"
                filename = f"pricing-{role}-{width}.png"
                current.screenshot(path=str(output / filename), full_page=True)
                evidence["screens"].append({"width": width, "role": role, "screenshot": filename})
            evidence["invoices"].append({"invoiceId": payment["invoiceId"], "merchantId": merchant,
                                         "width": width, "orderCode": payment["orderCode"]})
            policy_before = api("GET", backend + "/api/v1/admin/billing/policy/latest", fixture["adminToken"])
            policy_date = max(datetime.now(timezone.utc), datetime.fromisoformat(policy_before["effectiveAt"].replace("Z", "+00:00"))) + timedelta(hours=1)
            policy_page = admin_pages[1]
            policy_page.get_by_label("Có hiệu lực từ", exact=True).fill(policy_date.astimezone().replace(tzinfo=None).isoformat(timespec="seconds"))
            policy_page.get_by_label("Chính sách", exact=True).select_option("TRIAL")
            policy_page.get_by_label("Số ngày dùng thử", exact=True).fill("14")
            policy_messages = []

            def cancel_policy(dialog):
                policy_messages.append(dialog.message)
                assert "TRIAL, dùng thử 14 ngày" in dialog.message and "mọi trạng thái" in dialog.message
                dialog.dismiss()

            policy_page.once("dialog", cancel_policy)
            with policy_page.expect_response(lambda response: response.request.method == "POST"
                                             and urlparse(response.url).path == "/api/v1/admin/billing/policy/preview") as policy_preview:
                policy_page.get_by_role("button", name="Công bố chính sách mới", exact=True).click()
            assert policy_preview.value.status == 200
            expect(policy_page.get_by_role("button", name="Công bố chính sách mới", exact=True)).to_be_enabled()
            assert len(policy_messages) == 1
            assert api("GET", backend + "/api/v1/admin/billing/policy/latest", fixture["adminToken"]) == policy_before
            evidence["publicationPreviews"][-1]["policyCancelPreservedRevision"] = policy_before["revision"]
            owner_context.close()
            admin_context.close()
        evidence["providerStats"] = api("GET", fixture["provider"] + "/_fixture/stats", control=True)
        assert evidence["providerStats"]["creates"] == 3
        assert not errors, "Pricing browser page errors observed"
        evidence["pageErrors"] = len(errors)
        evidence["completed"] = True
    finally:
        if not evidence["completed"] and active_page is not None and not active_page.is_closed():
            active_page.screenshot(path=str(output / "diagnostic.png"), full_page=True)
            (output / "diagnostic.txt").write_text(active_page.locator("body").inner_text(), encoding="utf-8")
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
