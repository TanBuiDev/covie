# Social V2 — Seller reason/feedback browser proof and scoped layout repair

## Actual integration scope

Extend the existing opt-in CatalogSocialNavigationBrowserIntegrationTest with a separate `covie.test.social-seller-feedback-browser=true` fixture branch and new `apps/web/e2e/seller-feedback-browser.mjs`. Old navigation/hydration/offer scripts and their assertions remain available; their successful scenarios are not rerun here.

The new fixture creates a real Admin test actor and performs an actual bearer-authenticated Admin HIDE HTTP command before taking readonly snapshots. Its reason contains an HTML-looking script sentinel, and its private Admin note is different. A second public item has one persisted literal comment. These are disposable PG fixtures, not recovered/user/runtime data. Browser receives only an issued OWNER HttpOnly JWT cookie; no Admin credential is written to its manifest.

Actual random-port Next/API and disposable PostgreSQL are used, with localhost:3000 browser-origin HTTP/HMR forwarding. Responses are forwarded unchanged, not mocked. Flags are ON only in owned fixture processes. No MinIO is needed for these no-media items.

## Behavior verified

- Real Seller edit URL and exact item h1 asserted; caption waits for hydration readiness.
- HIDDEN item's actual reasonCode renders as literal text. No script element inside the reason and no sentinel JavaScript execution; Admin private note is not rendered.
- No comment GET before opening the entry. HIDDEN item's public thread returns real404 and error presentation, not privileged comments. A public item's thread returns real200 and the persisted literal comment; its approved item has no stale reason note.
- Open focuses the existing Close button. Escape closes, restores entry focus and retains the actual unsaved Caption field on both pages. No Catalog save, comment mutation, router-refresh command or API write is sent by the browser.
- Four viewport widths360/768/1280/1440 for each state, eight geometry observations. Panel bounds and document scroll width stay inside client bounds; existing stable scrollbar gutter is not mistaken for overflow.
- Full JSONB snapshots of catalog_items/variants/location_variants/catalog_item_media plus comments/moderation_cases/moderation_actions/audit_logs/outbox_events stay unchanged throughout browser interaction. Owner media remains0. HIDE is fixture setup, before readonly snapshots.

## Harness correction is not a production defect

The first helper assumed one GET per opened thread. Next development run produced two same-path GETs after opening, so the assertion before the second page failed. Evidence `.local/social-v2-g2-feedback-browser.log` and its browser/failure JSON. No request occurred before the first entry opened.

The helper now checks zero additional reads before each open, one-or-two same-path reads per open with the correct status, no other comment targets and no writes. It does not modify production request behavior or claim production duplicate requests were fixed. Functional run then passed in `.local/social-v2-g2-feedback-browser-final.log` (1 PASS, test58.82s). That run alone did **not** close visual/layout acceptance.

## Proven desktop stretch and minimal repair

Reviewing the functional screenshots showed the new feedback panel stretching to the height of the adjacent long commerce form, distributing extra height into its title/button. A natural-height browser assertion measured at1280:

- Entry button **234.609375px**, natural/minimum44px.
- Title **220.40625px**, natural29.796875px.
- 360/768 already had natural heights; this is a desktop grid stretch issue, not an API/business error.

`.local/social-v2-g2-feedback-layout-red.log` and matching failure JSON record the actual assertion failure. Natural height is calculated from text/line height, padding/borders and min-height, not a new product/design limit.

Only production change: add the feedback-specific CSS class and `.seller-catalog-editor__feedback { align-self: start; }`. No global panel/grid rule, commerce form, navigation, comment policy, BE/API/schema/client or color/icon redesign.

## Final validation

Sequential isolated API invocation:

```text
mvnw.cmd -o -Dtest=CatalogSocialNavigationBrowserIntegrationTest -Dcovie.test.social-navigation-browser=true -Dcovie.test.social-seller-feedback-browser=true -Dcovie.test.social-browser-web=E:/WebApp/CoVie/.local/social-v2-browser-validation/apps/web -Dcovie.test.social-browser-script=E:/WebApp/CoVie/apps/web/e2e/seller-feedback-browser.mjs -Dcovie.test.social-browser-output=E:/WebApp/CoVie/.local/social-v2-g2-feedback-layout-green test
```

- `.local/social-v2-g2-feedback-layout-green.log`: **1 PASS**, test40.03s, Maven42.889s.
- All eight observations now measure button44px and title29.796875px, matching natural heights. Browser API writes0, uncaught page errors0; two real404 GETs and two real200 GETs after explicit open in the dev runtime.
- `.local/social-v2-g2-feedback-layout-green/evidence.json`, browser/Next logs and eight screenshots retained. Current360/1280 hidden/public captures reviewed: no inflated title/button, reason/comment remain literal. Other widths have geometry checks and captures, not a complete screen-reader/whole-editor visual acceptance claim.
- Changed component's focused tests: **2 PASS**,6.47s, `.local/social-v2-g2-feedback-layout-component.log`. Final web typecheck and targeted component/helper lint exit0, matching `-typecheck.log`/`-lint.log`. Diff check passes.
- Current source/private-copy component and CSS hashes match. Manifest absent after cleanup; owned test listener ports60035/60038 no longer listen. Normal ports3000/8081 were also not listening at the latest checks; old normal PIDs7544/25008 no longer existed. They were not started/stopped/restarted by this work; do not carry forward the earlier “normal PIDs alive” claim.

No full BE/web suite, unchanged navigation/gallery/browser happy path, OpenAPI/client regeneration, runtime/recovered DB access, normal flag activation, commit/branch/remote operation or deployment. Whole V2 remains ACTIVE; normal V2/video flags OFF.

## Remaining gates

This proves OWNER Chrome local reason/feedback reads, not every role/type/policy, comment mutation/retry, native phone/cross-browser, entire editor keyboard/a11y, concurrent permission changes, new reason-query scale plan, load or staging. Prior JWT/privacy/schema proofs remain separate.

Next: capture the newly added reason SQL/query count through actual Seller HTTP and measure its plan using the existing 20k-item/200050-case fixture scale. Do not rerun the old Admin history scenario or add an index without measured evidence.
