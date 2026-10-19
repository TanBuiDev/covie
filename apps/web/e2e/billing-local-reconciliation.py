import csv
import hashlib
import io
import json
import os
import sys
import time
import urllib.request
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright


if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires isolated billing reconciliation fixture")
fixture = json.load(sys.stdin)
origin, backend = fixture["origin"], fixture["api"]
output = Path(fixture["output"])
evidence = {"completed": False, "invoices": [], "exports": [], "screens": [], "tables": [], "initialReads": [], "pageErrors": 0}


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


active_page = None


def verify_finance_tables(page, width, role):
    for label, preceding_button, column, key in [
        ("Danh sách giao dịch thuê bao", "Xuất trang CSV", -1, "invoices"),
        ("Danh sách phân quyền tài chính", "Cấp vai trò tài chính", 1, "roles"),
    ]:
        region = page.get_by_role("region", name=label, exact=True)
        table = region.get_by_role("table")
        page.get_by_role("button", name=preceding_button, exact=True).focus()
        page.keyboard.press("Tab")
        expect(region).to_be_focused()
        geometry = region.evaluate("region => ({width: region.clientWidth, table: region.querySelector('table').getBoundingClientRect().width, scroll: region.scrollWidth})")
        assert geometry["table"] >= 720
        keyboard_scrolled = False
        if geometry["scroll"] > geometry["width"] + 1:
            page.keyboard.press("ArrowRight")
            page.wait_for_function("region => region.scrollLeft > 0", arg=region.element_handle(), timeout=3000)
            keyboard_scrolled = True
        if width < 500:
            assert keyboard_scrolled, "Mobile financial tables must support keyboard horizontal scrolling"
        cell = table.locator("tbody tr").first.locator("td").nth(column)
        lines = cell.evaluate("cell => { const range = document.createRange(); range.selectNodeContents(cell); return range.getClientRects().length; }")
        assert lines == 1, "Financial status/role must not break into character fragments"
        region.evaluate("region => { region.scrollLeft = 0; }")
        region.screenshot(path=str(output / f"table-{key}-{role}-{width}-left.png"))
        if keyboard_scrolled:
            region.evaluate("region => { region.scrollLeft = region.scrollWidth; }")
            region.screenshot(path=str(output / f"table-{key}-{role}-{width}-right.png"))
        evidence["tables"].append({"width": width, "role": role, "kind": key,
                                   "regionWidth": geometry["width"], "tableWidth": geometry["table"],
                                   "keyboardScrolled": keyboard_scrolled, "statusOrRoleLines": lines})


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    errors = []

    def context_for(token, width, transport):
        context = browser.new_context(viewport={"width": width, "height": 1000}, accept_downloads=True)
        context.add_cookies([{"name": "covie_at", "value": token, "domain": "localhost", "path": "/",
                             "httpOnly": True, "sameSite": "Lax", "expires": int(time.time()) + 850}])

        def forward(route):
            url = urlparse(route.request.url)
            if url.hostname not in ("localhost", "127.0.0.1"):
                route.abort("blockedbyclient")
            elif url.path.startswith("/api/v1/") and url.path != "/api/v1/auth/ssr-refresh":
                if route.request.method == "GET" and transport.get("failRead") == url.path:
                    transport["failRead"] = None
                    route.fulfill(status=503, content_type="application/json", body=json.dumps({
                        "error": {"code": "BILLING_PAYMENT_UNAVAILABLE", "message": "Fixture temporary read unavailable"}
                    }))
                    return
                response = route.fetch(url=backend + url.path + ("?" + url.query if url.query else ""))
                if route.request.method == "POST" and url.path.endswith("/reconciliation"):
                    transport["keys"].append(route.request.headers.get("idempotency-key"))
                    if transport["dropNext"]:
                        assert response.status == 200, "Only lose an actually committed successful response"
                        transport["dropNext"] = False
                        route.abort("failed")
                        return
                route.fulfill(response=response)
            else:
                route.continue_()

        context.route("**/*", forward)
        context.on("page", lambda page: page.on("pageerror", lambda error: errors.append(type(error).__name__)))
        return context

    try:
        baseline_stats = api("GET", fixture["provider"] + "/_fixture/stats", control=True)
        api("POST", backend + "/api/v1/admin/billing/plans", fixture["adminToken"], {
            "code": fixture["planCode"], "displayName": "Manual reconciliation fixture", "priceVnd": 15000,
            "cycleMonths": 1, "effectiveAt": (datetime.now(timezone.utc) + timedelta(seconds=2)).isoformat(),
        })
        deadline = time.monotonic() + 15
        while not any(plan["code"] == fixture["planCode"] for plan in api("GET", backend + "/api/v1/billing/plans")):
            assert time.monotonic() < deadline, "Fixture plan becomes effective"
            time.sleep(0.1)
        for shop in fixture["shops"]:
            width, merchant = shop["width"], shop["merchantId"]
            base = backend + "/api/v1/merchants/" + merchant + "/billing"
            quote = api("POST", base + "/quote?planCode=" + fixture["planCode"], fixture["ownerToken"])
            headers = {"Authorization": "Bearer " + fixture["ownerToken"], "Content-Type": "application/json",
                       "Idempotency-Key": "native-manual-checkout-" + merchant}
            body = {"planCode": fixture["planCode"], "expectedAmountVnd": quote["amountDueVnd"],
                    "expectedPlanRevision": quote["planRevision"], "quoteId": quote["quoteId"]}
            request = urllib.request.Request(base + "/checkout", data=json.dumps(body).encode("utf-8"), headers=headers, method="POST")
            with urllib.request.urlopen(request, timeout=20) as response:
                payment = json.load(response)
            invoice_number = next(invoice["invoiceNumber"] for invoice in api("GET", base + "/invoices", fixture["ownerToken"])
                                  if invoice["invoiceId"] == payment["invoiceId"])
            api("POST", fixture["provider"] + "/_fixture/paid/" + str(payment["orderCode"]), control=True)
            reader_transport = {"dropNext": False, "keys": [], "failRead": "/api/v1/admin/billing/overview"}
            operator_transport = {"dropNext": True, "keys": []}
            reader_context = context_for(fixture["readerToken"], width, reader_transport)
            operator_context = context_for(fixture["operatorToken"], width, operator_transport)
            reader = reader_context.new_page()
            reader.goto(origin + "/admin/billing")
            active_page = reader
            expect(reader.locator("p[role='alert']")).to_contain_text("Fixture temporary read unavailable")
            expect(reader.get_by_role("heading", name="Tổng quan thu phí Seller", exact=True)).to_have_count(0)
            reader.screenshot(path=str(output / f"initial-error-reader-{width}.png"))
            reader_accessibility = reader.locator("body").aria_snapshot()
            assert "alert" in reader_accessibility and "Tải lại dữ liệu Billing" in reader_accessibility
            (output / f"initial-error-reader-{width}.aria.txt").write_text(reader_accessibility, encoding="utf-8")
            reader.get_by_role("button", name="Tải lại dữ liệu Billing", exact=True).focus()
            reader.keyboard.press("Enter")
            reader.wait_for_load_state("networkidle")
            expect(reader.locator("p[role='alert']")).to_have_count(0)
            evidence["initialReads"].append({"width": width, "role": "reader", "injected503": True, "keyboardRetry": True})
            expect(reader.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
            expect(reader.get_by_role("cell", name=invoice_number, exact=True)).to_be_visible()
            expect(reader.get_by_role("button", name="Đối soát với payOS", exact=True)).to_have_count(0)
            seller_transport = {"dropNext": False, "keys": [], "failRead": "/api/v1/merchants/" + merchant + "/billing/invoices"}
            seller_context = context_for(fixture["ownerToken"], width, seller_transport)
            seller = seller_context.new_page()
            active_page = seller
            seller.goto(origin + "/seller/" + merchant + "/billing")
            expect(seller.locator("p[role='alert']")).to_contain_text("Fixture temporary read unavailable")
            expect(seller.get_by_text("Chế độ thu phí chưa được kích hoạt cho các cửa hàng hiện tại.", exact=True)).to_have_count(0)
            expect(seller.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_have_count(0)
            seller.screenshot(path=str(output / f"initial-error-seller-{width}.png"))
            seller_accessibility = seller.locator("body").aria_snapshot()
            assert "alert" in seller_accessibility and "Tải lại dữ liệu thuê bao" in seller_accessibility
            (output / f"initial-error-seller-{width}.aria.txt").write_text(seller_accessibility, encoding="utf-8")
            seller.get_by_role("button", name="Tải lại dữ liệu thuê bao", exact=True).focus()
            seller.keyboard.press("Enter")
            expect(seller.get_by_role("link", name="Mở payOS để thanh toán", exact=False)).to_be_visible()
            expect(seller.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_disabled()
            seller.reload(wait_until="networkidle")
            expect(seller.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_disabled()
            navigation = seller.goto(origin + "/seller", wait_until="domcontentloaded")
            assert navigation is not None and navigation.status == 200
            seller.go_back(wait_until="networkidle")
            expect(seller.get_by_role("link", name="Mở payOS để thanh toán", exact=False)).to_be_visible()
            expect(seller.get_by_role("button", name="Tạo link thanh toán payOS", exact=True)).to_be_disabled()
            expect(seller.get_by_role("cell", name="PAID", exact=True)).to_have_count(0)
            history = seller.get_by_role("region", name="Lịch sử hóa đơn thuê bao", exact=True)
            status = history.locator("tbody tr").first.locator("td").nth(3)
            lines = status.evaluate("cell => { const range = document.createRange(); range.selectNodeContents(cell); return range.getClientRects().length; }")
            assert lines == 1, "Seller invoice status must remain readable"
            history.focus()
            if width < 500:
                seller.keyboard.press("ArrowRight")
                seller.wait_for_function("region => region.scrollLeft > 0", arg=history.element_handle(), timeout=3000)
            assert seller.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            seller.screenshot(path=str(output / f"initial-recovered-seller-{width}.png"))
            evidence["initialReads"].append({"width": width, "role": "seller", "injected503": True, "keyboardRetry": True,
                                             "pendingCheckoutDisabled": True, "statusLines": lines,
                                             "refreshBackRetainsPending": True, "noFalsePaid": True})
            active_page = reader
            with reader.expect_response(lambda response: urlparse(response.url).path == "/api/v1/admin/billing/invoices/export") as exported_response:
                with reader.expect_download() as downloaded:
                    reader.get_by_role("button", name="Xuất trang CSV", exact=True).focus()
                    reader.keyboard.press("Enter")
            assert exported_response.value.status == 200
            assert "text/csv" in exported_response.value.headers["content-type"]
            assert "no-store" in exported_response.value.headers["cache-control"]
            download = downloaded.value
            assert download.suggested_filename == "seller-billing-invoices.csv"
            export_file = output / f"invoice-export-reader-{width}.csv"
            download.save_as(str(export_file))
            exported_bytes = export_file.read_bytes()
            records = csv.DictReader(io.StringIO(exported_bytes.decode("utf-8"), newline=""))
            assert records.fieldnames == ["invoiceId", "merchantId", "invoiceNumber", "status", "amountVnd", "createdAtUtc", "paidAtUtc"]
            exported_rows = list(records)
            assert 1 <= len(exported_rows) <= 20
            assert any(row["invoiceId"] == payment["invoiceId"] and row["amountVnd"] == "15000" for row in exported_rows)
            denied_context = context_for(fixture["adminToken"], width, {"dropNext": False, "keys": []})
            denied_page = denied_context.new_page()
            denied_page.goto(origin + "/admin/billing")
            denied_page.wait_for_load_state("networkidle")
            expect(denied_page.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
            expect(denied_page.get_by_text("Tài khoản này chưa được cấp quyền đọc dữ liệu tài chính.", exact=False)).to_be_visible()
            expect(denied_page.get_by_role("button", name="Xuất trang CSV", exact=True)).to_have_count(0)
            expect(denied_page.get_by_text("Chưa được cấp quyền xem tổng quan tài chính.", exact=True)).to_be_visible()
            expect(denied_page.get_by_text("Chưa có giao dịch thuê bao.", exact=True)).to_have_count(0)
            expect(denied_page.get_by_text("Không có hồ sơ cần xem.", exact=True)).to_have_count(0)
            denied_page.screenshot(path=str(output / f"finance-denied-{width}.png"))
            denied_context.close()
            evidence["exports"].append({"width": width, "filename": export_file.name, "rows": len(exported_rows),
                                        "sha256": hashlib.sha256(exported_bytes).hexdigest(), "keyboardDownload": True,
                                        "deniedRoleHasNoExport": True})
            page = operator_context.new_page()
            active_page = page
            page.goto(origin + "/admin/billing")
            page.wait_for_load_state("networkidle")
            expect(page.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
            page.get_by_label("Hóa đơn cần đối soát", exact=True).select_option(payment["invoiceId"])
            page.get_by_label("Lý do đối soát", exact=True).fill("Investigate missing callback in native disposable fixture")
            button = page.get_by_role("button", name="Đối soát với payOS", exact=True)
            button.focus()
            page.keyboard.press("Enter")
            section = page.locator("section[aria-labelledby='billing-reconciliation-heading']")
            expect(section.get_by_role("alert")).to_be_visible()
            with page.expect_response(lambda response: response.request.method == "POST"
                                      and urlparse(response.url).path.endswith("/reconciliation")) as checked:
                button.focus()
                page.keyboard.press("Enter")
            assert checked.value.status == 200
            result = checked.value.json()
            assert result["invoiceStatus"] == "PAID" and result["providerStatus"] == "PAID" and result["discrepancy"] == "NONE"
            expect(section.get_by_role("status")).to_contain_text("Hóa đơn: PAID; payOS: PAID; kết quả: NONE")
            expect(page.get_by_role("row").filter(has_text=invoice_number)).to_contain_text("PAID")
            expect(seller.get_by_role("region", name="Lịch sử hóa đơn thuê bao", exact=True)
                   .get_by_role("cell", name="PAID", exact=True)).to_be_visible(timeout=12000)
            expect(seller.locator("p[role='alert']")).to_have_count(0)
            seller.screenshot(path=str(output / f"recovered-paid-seller-{width}.png"))
            assert len(operator_transport["keys"]) == 2 and operator_transport["keys"][0]
            assert operator_transport["keys"][0] == operator_transport["keys"][1], "Uncertain response retry preserves key"
            assert not reader_transport["keys"], "Reader must not send a reconciliation mutation"
            for current, role in [(reader, "reader"), (page, "operator")]:
                verify_finance_tables(current, width, role)
                assert urlparse(current.url).path == "/admin/billing"
                expect(current.get_by_role("heading", name="Thuê bao Seller", exact=True)).to_be_visible()
                assert current.evaluate("document.documentElement.scrollWidth <= window.innerWidth"), "Admin viewport overflow"
                filename = f"reconciliation-{role}-{width}.png"
                current.screenshot(path=str(output / filename), full_page=True)
                evidence["screens"].append({"width": width, "role": role, "screenshot": filename})
            evidence["invoices"].append({"invoiceId": payment["invoiceId"], "merchantId": merchant, "width": width,
                                         "sameKeyRetry": True, "readerNoMutation": True})
            reader_context.close()
            operator_context.close()
            seller_context.close()
        evidence["providerStats"] = api("GET", fixture["provider"] + "/_fixture/stats", control=True)
        evidence["providerBaseline"] = {name: baseline_stats[name] for name in ("creates", "lookups")}
        evidence["providerDelta"] = {name: evidence["providerStats"][name] - baseline_stats[name]
                                     for name in ("creates", "lookups")}
        assert evidence["providerDelta"]["creates"] == 3 and evidence["providerDelta"]["lookups"] == 3
        assert not errors, "Native browser page errors observed"
        evidence["pageErrors"] = len(errors)
        evidence["completed"] = True
    finally:
        if not evidence["completed"] and active_page is not None and not active_page.is_closed():
            active_page.screenshot(path=str(output / "diagnostic.png"), full_page=True)
            (output / "diagnostic.txt").write_text(active_page.locator("body").inner_text(), encoding="utf-8")
        (output / "result.json").write_text(json.dumps(evidence, ensure_ascii=False, indent=2), encoding="utf-8")
        browser.close()
