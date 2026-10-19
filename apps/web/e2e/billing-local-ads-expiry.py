import json
import os
import sys
import urllib.request
from pathlib import Path
from urllib.parse import urlparse

from playwright.sync_api import expect, sync_playwright


if os.environ.get("COVIE_BILLING_BROWSER_TEST") != "1":
    raise RuntimeError("Requires isolated billing Ads fixture")
fixture = json.load(sys.stdin)
output = Path(fixture["output"])
evidence = {"completed": False, "widths": [], "pageErrors": 0}


def action(name):
    request = urllib.request.Request(fixture["provider"] + "/_fixture/action/" + name, data=b"",
                                     headers={"x-fixture-control": fixture["providerControl"]}, method="POST")
    with urllib.request.urlopen(request, timeout=15) as response:
        assert response.status == 200


with sync_playwright() as playwright:
    browser = playwright.chromium.launch(channel="chrome", headless=True)
    errors = []
    try:
        for width in (360, 390, 1366):
            context = browser.new_context(viewport={"width": width, "height": 1000})

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
            normalized = page.evaluate("""merchant => [
                new URL('/search/../merchants/' + merchant, 'http://localhost:3000').pathname,
                new URL('https://billing-fixture.invalid/search/%2e%2e/merchants/' + merchant).pathname
            ]""", fixture["merchantId"])
            assert normalized == ["/merchants/" + fixture["merchantId"]] * 2
            action("ads-short-term")
            page.goto(fixture["origin"] + "/", wait_until="domcontentloaded")
            link = page.get_by_role("link", name="Billing expiry fixture — View", exact=True)
            expect(link).to_be_visible(timeout=5000)
            assert link.get_attribute("href") == "/merchants/" + fixture["merchantId"]
            page.screenshot(path=str(output / f"ads-active-{width}.png"))
            expect(link).to_have_count(0, timeout=16000)
            page.reload(wait_until="domcontentloaded")
            with page.expect_response(lambda response: "/api/v1/ads/active" in response.url):
                page.reload(wait_until="domcontentloaded")
            expect(link).to_have_count(0)
            page.screenshot(path=str(output / f"ads-expired-{width}.png"))
            action("ads-renew-term")
            page.reload(wait_until="domcontentloaded")
            expect(link).to_be_visible(timeout=10000)
            assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
            assert urlparse(page.url).path == "/"
            evidence["widths"].append({"width": width, "activeLink": True, "expiredLinkRemoved": True,
                                       "reloadStillHidden": True, "renewedLink": True, "browserDotNormalization": True})
            context.close()
        assert not errors
        evidence["pageErrors"] = len(errors)
        evidence["completed"] = True
    finally:
        (output / "ads-expiry-result.json").write_text(json.dumps(evidence, indent=2), encoding="utf-8")
        browser.close()
