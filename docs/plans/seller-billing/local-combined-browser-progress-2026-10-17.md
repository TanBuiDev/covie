# Combined Seller/Admin browser, API, DB and provider HTTP — 2026-10-17

Whole approved Seller Billing objective remains **ACTIVE / NOT_READY**. Previous goal turn was progress:current BE full gate and issued-JWT/provider transport cases verified. This turn joins those boundaries and fixes one observed Billing UI overflow. No real provider/payment,production activation,remote Git operation or recovered-data use.

## Actual combined server boundary

New test-only `LoopbackPayosProvider` provides an owned loopback HTTP server and a primary gateway using the production adapter's real create/lookup/signature methods. Only availability is overridden;the gateway's HTTP client is captured during its construction and relays its otherwise unchanged official-origin requests to loopback. Server independently validates dummy credential headers and canonical HMAC,stores a payment record and signs create/lookup/callback data. No production endpoint/base-URL switch or trust-all TLS is added.

The fixture's `/_fixture/paid/{orderCode}` control endpoint is on that separate embedded test server only,requires an ephemeral control token and is not a Spring/application route. It changes fake-provider state and supplies a signed callback;it does not mutate the application DB. Fixture controls and issued tokens go to the browser driver through stdin,not environment,argv,logs or public assets.

Existing actual-JWT authorization/isolation/replay cases now use this HTTP-backed adapter instead of overriding create in-process:3 passed,0 failures/errors/skips,43.739s (`.local/billing-combined-provider-authorization.log`). Real Spring/JWT/session/membership/SQL remain intact. This closes the previously separate provider-HTTP/PG-auth boundary for those cases,without inventing live-provider acceptance.

## Native browser fixture

Opt-in `BillingAuthorizationHttpIntegrationTest#actualSellerAndAdminBrowsersUseSignedProviderHttpAndPersistOneSettlementPerShop` owns Spring/PG/provider and launches the existing built Next app from this C-drive checkout. Public origin consistently `http://localhost:3000`;startup refuses an occupied port and removes backend payOS/release variables from the Web child. Python Playwright uses installed Chrome headlessly,with separate actual-session OWNER/Admin contexts. A fetch/fulfill transport bridge forwards original API requests to the real Spring server because production Next has no development API rewrite. Responses are not manufactured. This is not actual Nginx/HTTPS,offline transport or production runtime-image proof.

Python3.13 virtual environment is ignored/local;installed Playwright1.64.0 from registry metadata,pinned for this fixture. No workspace lockfile or application dependency upgrade. Bundled server-helper `--help` was inspected;custom Java lifecycle is used because JUnit must retain the actual Spring/PG/provider session while transferring private issued fixtures via stdin.

Harness calibration failures are not product REDs:

- `.local/billing-combined-browser-first.log`:Next bound localhost only on::1 while actual Java DNS preferred127.0.0.1;owned calibration confirmed::1 HTTP200 and IPv4 connection failure. Bind changed to127.0.0.1 without changing public origin.
- `.local/billing-combined-browser-ipv4.log`:readiness still failed. Actual JShell probe showed HTTP1.1=200 versus HTTP2=IOException against that Next listener. Dedicated HTTP1.1 readiness client fixes the fixture,matching existing SSR test practice.
- `.local/billing-combined-browser-http1.log`:actual Admin plan creation succeeded,but exact implicit-label text did not match a label containing option text. Selector scoped to the actual labeled select. This run also had a Surefire shutdown timeout;not counted as clean success.
- `.local/billing-combined-browser-selector.log`:business validation paragraph appeared,but generic alert lookup also matched Next's route-announcer live region. Selector narrowed to the actual error paragraph. No assertion or product behavior was removed.

## Confirmed product RED and minimal correction

`.local/billing-combined-browser-alert.log` reached the actual360px payment flow,including signed settlement/polling/stop-resume. Seller fit;Admin screenshot/document width was450px on a360px viewport. Long native select content overflowed its grid field. Images in `.local/billing-browser-1792246052295/` were inspected.

Only `AdminBillingScreen` receives a CSS-module class constraining its select controls to min-width0/max-width100%/width100%. No global Admin/Seller layout,table,workflow,API or business rule changes;no blanket overflow hiding. Build for this exact source passed (`.local/billing-web-admin-overflow-build.log`).

## Actual GREEN

`.local/billing-combined-browser-admin-css.log`:1 opt-in case passed,0 failures/errors/skips,1m01s. Evidence directory:

`.local/billing-browser-1792246268446/`

`result.json` completed=true,Chrome155.0.8059.84,six viewport/path/landmark-validated screenshots,all noOverflow=true,0 observed main-page errors. All six images were opened and visually inspected:Seller/Admin at360,390,1366. This is a scoped responsive review,not Figma pixel matching or a full accessibility audit.

Actual native actions:

1. Admin creates a scheduled15000VND fixture plan using the UI and real cookie-CSRF POST;waits for its effective time rather than changing the application clock or DB pricing.
2. OWNER's three separate fixture shops select that plan. Empty history and a checkout-without-reviewed-quote error are asserted. Native Tab→Enter activates quote preview.
3. Native checkout obtains the actual quote/version/amount-backed link through Spring→production payOS adapter→owned provider HTTP. Hosted URL is validated but never opened;external page traffic is blocked by fixture routing.
4. Native pending state appears. A separate spoofed return tab with code00/statusPAID displays verification only;actual API still reports PENDING_PAYMENT.
5. Owned provider generates its signed callback;actual anonymous Spring HTTP settles and duplicate callback returns duplicate. Original Seller page polls real API and renders paid automatically.
6. Native confirm dialogs stop/resume renewal-at-period-end metadata without another charge. This is not a new paid renewal or auto-debit test.
7. Actual Admin ledger reloads. Java independently verifies each invoice PAID/15000,one successful attempt,one grant,one settlement audit and one outbox. Exactly3 provider create POSTs for3 shops.

## Post-change gates and limits

- API-client check/typecheck/lint passed;lint0 errors/28 existing warnings.
- Full Web Vitest493 passed,12 skipped,0 failed,82.92s (`.local/billing-post-browser-web-test.log`);118 files passed/1 skipped. Skips are not passes.
- Production Next build passed before native verification;no subsequent application source change.
- Python script compiled successfully. Existing BE full974 reported/973 passed/1 skipped predates these test-only harness additions;no repeated full BE run for a scoped CSS change. Do not aggregate different runs into one suite.
- Original30 BIL IDs retained.025/005/007/010 gain partial evidence,but whole acceptance of their remaining states/invariants is not inferred. Accepted original IDs remain002/004/009/011 (4/30).
- Remaining:loading/unauthorized/resilience/a11y UI states,provider-unavailable/expired-quote recovery,combined signed lookup/reconciliation and process restart,remaining original BILs,migration/recovery/security/operational/pilot requirements. All ordinary payment/release/enforcement flags remain OFF.

## Reproduce locally

From repository root,prepare an ignored Python environment with `python -m venv .local/billing-browser-venv`,then its Python `-m pip install playwright==1.64.0`. Build with `pnpm web:build`. With installed Chrome and free port3000,from `apps/api` run the Maven wrapper with Java25:

```text
./mvnw.cmd -Dcovie.test.billing-browser=true -Dtest=BillingAuthorizationHttpIntegrationTest#actualSellerAndAdminBrowsersUseSignedProviderHttpAndPersistOneSettlementPerShop test
```

Optional `-Dcovie.test.python=<absolute interpreter>` supports another prepared environment. `.local/billing-browser-latest.json` points to each unique run directory;it contains no tokens. Do not start a second normal stack or use an existing/recovered DB.

Next action: Verify provider-unavailable/expired-quote recovery and signed lookup on this combined fixture,then remaining original-BIL acceptance.
Required from you: Nothing
Completion condition: Safe retries/recovery have actual HTTP/DB/UI evidence with no duplicate provider charge;whole goal remains active until all local requirements are proven.
