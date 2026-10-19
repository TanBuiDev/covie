import json
import os
import re
import sys
import time
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright


if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires the isolated billing backend fixture")

fixture = json.load(sys.stdin)
origin = fixture["origin"]
backend = fixture["api"]
output = Path(fixture["output"])
output.mkdir(parents=True, exist_ok=True)
evidence = {"completed": False, "screens": [], "pageErrors": 0}
active_page = None


def api(method, url, token=None, body=None, headers=None):
    values = dict(headers or {})
    if token:
        values["Authorization"] = "Bearer " + token
    data = None if body is None else json.dumps(body).encode("utf-8")
    if data is not None:
        values["Content-Type"] = "application/json"
    request = urllib.request.Request(url, data=data, headers=values, method=method)
    with urllib.request.urlopen(request, timeout=15) as response:
        return json.load(response)


def session(browser, token, width):
    policy_failure = fixture.get("scenario") == "onboarding" and token == fixture["ownerToken"]
    context = browser.new_context(viewport={"width": width, "height": 1000},
                                  timezone_id="UTC" if fixture.get("scenario") == "calendar"
                                  and token == fixture["ownerToken"] else "Asia/Ho_Chi_Minh")
    context.add_cookies([{
        "name": "covie_at", "value": token, "domain": "localhost", "path": "/",
        "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850,
    }])
    if fixture.get("scenario") == "qr":
        context.grant_permissions(["clipboard-read", "clipboard-write"], origin=origin)

    def route_request(route):
        nonlocal policy_failure
        url = urlparse(route.request.url)
        if url.hostname not in ("localhost", "127.0.0.1"):
            route.abort("blockedbyclient")
        elif url.path.startswith("/api/v1/") and url.path != "/api/v1/auth/ssr-refresh":
            if policy_failure and url.path == "/api/v1/billing/onboarding":
                policy_failure = False
                route.fulfill(status=503, content_type="application/json", body='{"error":{"code":"BILLING_PAYMENT_UNAVAILABLE"}}')
                return
            target = backend + url.path + ("?" + url.query if url.query else "")
            response = route.fetch(url=target)
            route.fulfill(response=response)
        else:
            route.continue_()

    context.route("**/*", route_request)
    return context


def capture(page, role, width, expected_path, landmark):
    assert urlparse(page.url).path == expected_path, "Wrong destination before capture"
    expect(page.get_by_role("heading", name=landmark, exact=True)).to_be_visible()
    assert page.evaluate("window.innerWidth") == width, "Wrong viewport"
    fits = page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    filename = f"{role}-{width}.png"
    page.screenshot(path=str(output / filename), full_page=True)
    evidence["screens"].append({"role": role, "width": width, "path": expected_path,
                                "screenshot": filename, "noOverflow": fits})
    assert fits, "Page overflows the required viewport"


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    try:
        admin_context = session(browser, fixture["adminToken"], 1366)
        admin_page = admin_context.new_page()
        active_page = admin_page
        page_errors = []
        admin_page.on("pageerror", lambda error: page_errors.append(type(error).__name__))
        admin_page.goto(origin + "/admin/billing")
        admin_page.wait_for_load_state("networkidle")
        expect(admin_page.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
        expect(admin_page.get_by_label("Tên gói", exact=True)).to_be_visible()
        admin_page.get_by_label("Mã gói (chữ in hoa, số, gạch dưới)", exact=True).fill(fixture["planCode"])
        admin_page.get_by_label("Tên gói", exact=True).fill("Browser monthly fixture")
        admin_page.get_by_label("Giá niêm yết (VND)", exact=True).fill("15000")
        admin_page.get_by_label("Chu kỳ (tháng)", exact=True).fill("1")
        effective = (datetime.now() + timedelta(seconds=8)).isoformat(timespec="seconds").removesuffix(":00")
        admin_page.get_by_label("Có hiệu lực từ", exact=True).fill(effective)
        admin_page.once("dialog", lambda dialog: dialog.accept())
        with admin_page.expect_response(lambda response: response.request.method == "POST"
                                      and urlparse(response.url).path == "/api/v1/admin/billing/plans") as created:
            admin_page.get_by_role("button", name="Tạo gói mới", exact=True).click()
        assert created.value.status == 200, "Admin plan creation failed"
        expect(admin_page.get_by_text("Gói mới đã được tạo. Giá có hiệu lực theo lịch đã chọn.", exact=True)).to_be_visible()
        evidence["planCode"] = fixture["planCode"]
        deadline = time.monotonic() + 20
        while not any(plan["code"] == fixture["planCode"]
                      for plan in api("GET", backend + "/api/v1/billing/plans")):
            assert time.monotonic() < deadline, "Scheduled fixture plan did not become effective"
            time.sleep(0.25)

        outcomes = []
        for shop in fixture["shops"]:
            width = shop["width"]
            merchant = shop["merchantId"]
            path = "/seller/" + merchant + "/billing"
            context = session(browser, fixture["ownerToken"], width)
            page = context.new_page()
            active_page = page
            page.on("pageerror", lambda error: page_errors.append(type(error).__name__))
            if fixture.get("scenario") == "onboarding":
                page.goto(origin + "/seller", wait_until="networkidle")
                page.get_by_text("+ Mở thêm cửa hàng mới", exact=True).click()
                acknowledge = page.get_by_role("checkbox", name=re.compile("Tôi đã đọc các điều kiện thuê bao"))
                create_shop = page.get_by_role("button", name="Tạo cửa hàng", exact=True)
                expect(acknowledge).to_be_disabled()
                expect(create_shop).to_be_disabled()
                retry = page.get_by_role("button", name="Tải lại chính sách thuê bao", exact=True)
                retry.focus()
                with page.expect_response(lambda response: urlparse(response.url).path == "/api/v1/billing/onboarding") as policy_read:
                    page.keyboard.press("Enter")
                assert policy_read.value.status == 200
                assert policy_read.value.json()["registrationEnabled"] is True
                expect(acknowledge).to_be_enabled()
                expect(create_shop).to_be_disabled()
                page.get_by_role("textbox", name="Tên cửa hàng", exact=True).fill("Billing onboarding " + str(width))
                expect(page.get_by_role("list", name="Các bước đăng ký Seller", exact=True)).to_be_visible()
                acknowledge.focus()
                page.keyboard.press("Space")
                expect(acknowledge).to_be_checked()
                expect(create_shop).to_be_enabled()
                capture(page, "onboarding", width, "/seller", "Tạo cửa hàng")
                with page.expect_response(lambda response: response.request.method == "POST"
                                          and urlparse(response.url).path == "/api/v1/me/merchants") as registered:
                    create_shop.focus()
                    page.keyboard.press("Enter")
                assert registered.value.status == 201
                evidence.setdefault("onboarding", []).append({"width": width, "policyRetryGET200": True,
                    "blockedBeforeAcknowledgement": True, "nativeKeyboardAcknowledgement": True, "createdHTTP201": True})
            page.goto(origin + path)
            page.wait_for_load_state("networkidle")
            expect(page.get_by_role("heading", name="Gói thuê bao đang được công bố", exact=True)).to_be_visible()
            expect(page.get_by_text("Chưa có hóa đơn thuê bao.", exact=True)).to_be_visible()
            page.locator("label").filter(has_text=re.compile(r"^Chọn gói")).locator("select").select_option(fixture["planCode"])
            page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True).click()
            expect(page.locator("p[role='alert']")).to_contain_text("Vui lòng kiểm tra và xác nhận số tiền")
            page.get_by_label("Mã ưu đãi (không bắt buộc)", exact=True).click()
            page.keyboard.press("Tab")
            preview = page.get_by_role("button", name="Kiểm tra số tiền thanh toán", exact=True)
            expect(preview).to_be_focused()
            with page.expect_response(lambda response: response.request.method == "POST"
                                      and urlparse(response.url).path.endswith("/billing/quote")) as quoted:
                page.keyboard.press("Enter")
            assert quoted.value.status == 200
            expect(page.get_by_text("Phải thanh toán: 15.000đ", exact=True)).to_be_visible()
            if fixture.get("scenario") == "calendar":
                expected_time = page.evaluate("value => new Date(value).toLocaleTimeString('vi-VN', {timeZone: 'Asia/Ho_Chi_Minh'})", quoted.value.json()["quoteExpiresAt"])
                expect(page.get_by_role("status").filter(has_text="Phải thanh toán")).to_contain_text(expected_time)
            with page.expect_response(lambda response: response.request.method == "POST"
                                      and urlparse(response.url).path.endswith("/billing/checkout")) as checkout:
                page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True).click()
            assert checkout.value.status == 200, "Seller checkout failed"
            invoice = checkout.value.json()
            expect(page.get_by_role("heading", name="Đang chờ xác nhận", exact=True)).to_be_visible()
            if fixture.get("scenario") == "qr":
                from PIL import Image
                import zxingcpp
                reference = "CB-" + str(invoice["orderCode"])
                qr = page.get_by_role("img", name="Mã QR thanh toán payOS cho hóa đơn " + reference, exact=True)
                expect(qr).to_be_visible()
                assert qr.get_attribute("src").startswith("data:image/png;base64,")
                qr_path = output / f"checkout-qr-{width}.png"
                qr.screenshot(path=str(qr_path))
                decoded = zxingcpp.read_barcode(Image.open(qr_path))
                assert decoded is not None and decoded.text == invoice["qrPayload"], "QR must encode exact signed-provider payload"
                copy = page.get_by_role("button", name="Sao chép mã hóa đơn " + reference, exact=True)
                copy.focus()
                page.keyboard.press("Enter")
                expect(page.get_by_text("Đã sao chép mã hóa đơn.", exact=True)).to_be_visible()
                assert page.evaluate("navigator.clipboard.readText()") == reference
                for label in ("Báo giá còn hiệu lực", "Link thanh toán còn hiệu lực"):
                    expect(page.get_by_role("timer", name=label, exact=True)).to_have_attribute("aria-live", "off")
                timer = page.get_by_role("timer", name="Link thanh toán còn hiệu lực", exact=True)
                previous = timer.inner_text()
                page.wait_for_function("previous => document.querySelector('[role=timer][aria-label=\"Link thanh toán còn hiệu lực\"]').textContent !== previous", arg=previous)
                capture(page, "pending-qr", width, path, "Gói thuê bao đang được công bố")
                assert "Đã xác nhận thanh toán" not in page.locator("body").inner_text()
                page.reload(wait_until="networkidle")
                expect(page.get_by_role("link", name="Mở payOS để thanh toán")).to_have_attribute("href", invoice["checkoutUrl"])
                expect(page.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_disabled()
                expect(page.get_by_text("Mã QR hiện có trên trang thanh toán payOS. Mở link của hóa đơn này để tiếp tục.", exact=True)).to_be_visible()
                evidence.setdefault("qrCheckouts", []).append({"width": width, "exactPayloadDecoded": True,
                    "localDataImage": True, "keyboardCopy": True, "expiryTicksNonLive": True,
                    "samePendingAfterReload": True, "noFalsePaid": True})
            link = page.get_by_role("link", name="Mở payOS để thanh toán")
            expect(link).to_have_attribute("href", re.compile(r"^https://pay\.payos\.vn/web/[0-9a-f]{32}$"))
            expect(link).to_have_attribute("target", "_blank")
            if width == 360:
                spoof = context.new_page()
                spoof.goto(origin + "/seller/billing/return?merchantId=" + merchant + "&code=00&status=PAID")
                expect(spoof.get_by_role("heading", name="Đang xác minh thanh toán", exact=True)).to_be_visible()
                state = api("GET", backend + "/api/v1/merchants/" + merchant
                            + "/billing/invoices/" + invoice["invoiceId"], fixture["ownerToken"])
                assert state["invoiceStatus"] == "PENDING_PAYMENT", "Redirect mutated payment state"
                spoof.close()
                evidence["spoofRedirectDidNotSettle"] = True
            callback = api("POST", fixture["provider"] + "/_fixture/paid/" + str(invoice["orderCode"]),
                           headers={"x-fixture-control": fixture["providerControl"]})
            settled = api("POST", backend + "/api/v1/billing/webhooks/payos", body=callback)
            assert settled["status"] == "settled", "Signed fixture callback did not settle"
            replay = api("POST", backend + "/api/v1/billing/webhooks/payos", body=callback)
            assert replay["status"] == "duplicate", "Callback replay was not idempotent"
            expect(page.get_by_role("heading", name="Đã xác nhận thanh toán", exact=True)).to_be_visible(timeout=15000)
            if fixture.get("scenario") == "onboarding":
                expect(page.get_by_text(re.compile("Còn khoảng [0-9]+ ngày"))).to_be_visible()
                evidence.setdefault("remainingDays", []).append({"width": width, "serverPeriodVisible": True})
            if fixture.get("scenario") == "qr":
                expect(page.get_by_role("img", name=re.compile("Mã QR thanh toán payOS"))).to_have_count(0)
            if fixture.get("scenario") == "calendar":
                state = api("GET", backend + "/api/v1/merchants/" + merchant
                            + "/billing/invoices/" + invoice["invoiceId"], fixture["ownerToken"])
                assert state["periodStart"] == str(fixture["calendarYear"]) + "-01-30T18:30:00Z"
                assert state["periodEnd"] == str(fixture["calendarYear"]) + "-02-28T18:30:00Z"
                expected_date = page.evaluate("value => new Intl.DateTimeFormat('vi-VN', {dateStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh'}).format(new Date(value))", state["periodEnd"])
                device_date = page.evaluate("value => new Intl.DateTimeFormat('vi-VN', {dateStyle: 'short'}).format(new Date(value))", state["periodEnd"])
                assert expected_date != device_date, "Calendar fixture must cross the UTC/ICT date boundary"
                expect(page.get_by_text("Kết thúc kỳ: " + expected_date + ".", exact=True)).to_be_visible(timeout=15000)
                evidence["utcDeviceShowsIctBillingDate"] = True
            page.once("dialog", lambda dialog: dialog.accept())
            page.get_by_role("button", name="Không gia hạn sau kỳ hiện tại", exact=True).click()
            resume = page.get_by_role("button", name="Bỏ yêu cầu dừng kỳ sau", exact=True)
            expect(resume).to_be_visible()
            page.once("dialog", lambda dialog: dialog.accept())
            resume.click()
            expect(page.get_by_role("button", name="Không gia hạn sau kỳ hiện tại", exact=True)).to_be_visible()
            capture(page, "seller", width, path, "Gói thuê bao đang được công bố")
            outcomes.append({"merchantId": merchant, "invoiceId": invoice["invoiceId"],
                             "orderCode": invoice["orderCode"], "width": width})
            context.close()
            admin_page.set_viewport_size({"width": width, "height": 1000})
            active_page = admin_page
            admin_page.reload()
            admin_page.wait_for_load_state("networkidle")
            expect(admin_page.get_by_text("PAID", exact=True).first).to_be_visible()
            capture(admin_page, "admin", width, "/admin/billing", "Thuê bao Seller")
        evidence["invoices"] = outcomes
        evidence["pageErrors"] = len(page_errors)
        assert not page_errors, "Browser page errors observed"
        evidence["browser"] = browser.version
        evidence["completed"] = True
        admin_context.close()
    finally:
        if not evidence["completed"] and active_page is not None and not active_page.is_closed():
            evidence["diagnosticPath"] = urlparse(active_page.url).path
            active_page.screenshot(path=str(output / "diagnostic.png"), full_page=True)
            (output / "diagnostic.txt").write_text(active_page.locator("body").inner_text(), encoding="utf-8")
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
